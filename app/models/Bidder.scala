package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import misc.Util
import play.api.libs.json.Json
import scala.language.postfixOps
import scala.util.{Success, Failure, Try}

case class BidderException(message: String, cause: Exception = null) extends Exception(message, cause)

case class BidderData(bidder: Bidder, payments: List[Payment], bids: List[Bid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)

case class Bidder(id: Option[Long], event: Event, bidderNumber: String, contact: Contact)
object Bidder { //extends ((Option[Long], Event, String, Contact) => Bidder) {
  import actors.BiddersActor._

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val bidFormat = Json.format[Bid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(3 seconds)

  def payments(bidder: Bidder) =
    Util.wait { (biddersActor ? Payments(bidder)).mapTo[Try[List[Payment]]] }

  def paymentsTotal(bidder: Bidder): Try[BigDecimal] = payments(bidder) flatMap { ps =>
    Try((BigDecimal(0.0) /: ps) { (sum, p) => sum + p.amount })
  }

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Try[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
        Util.wait { (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Try[Payment]] }
      case None =>
        Failure(new BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  def all() = Util.wait { (biddersActor ? GetBidders).mapTo[Try[List[Bidder]]] }

  def get(id: Long) = Util.wait { (biddersActor ? GetBidder(id)).mapTo[Try[Option[Bidder]]] }

  def create(eventId: Long, bidderNumber: String, contactId: Long): Try[Bidder] =
    Event.get(eventId) flatMap { eventOpt =>
      Contact.get(contactId) flatMap { contactOpt =>
        (eventOpt, contactOpt) match {
          case (Some(event), Some(contact)) =>
            Util.wait { (biddersActor ? Bidder(None, event, bidderNumber, contact)).mapTo[Try[Bidder]] }
          case _ =>
            Failure(new BidderException(s"Unable to find event ID $eventId and/or contact ID $contactId to create bidder"))
        }
      }
    }

  def delete(id: Long) = Util.wait { (biddersActor ? DeleteBidder(id)).mapTo[Try[Bidder]] }

  def edit(id: Long, bidderNumber: String, contactId: Long): Try[Bidder] =
    get(id) flatMap {
      case Some(bidder) =>
        Event.get(bidder.event.id.get) flatMap { eventOpt =>
          Contact.get(contactId) flatMap { contactOpt =>
            (eventOpt, contactOpt) match {
              case (Some(event), Some(contact)) =>
                Util.wait {
                  (biddersActor ? EditBidder(Bidder(Some(id), event, bidderNumber, contact))).mapTo[Try[Bidder]]
                }
              case _ =>
                Failure(new BidderException(s"Unable to find event ID ${bidder.event.id.get} and/or contact ID $contactId to edit bidder"))
            }
          }
        }
      case None => Failure(new BidderException(s"Cannot find bidder ID $id to edit"))
    }

  def allByEvent(eventId: Long) = Util.wait { (biddersActor ? BiddersForEvent(eventId)).mapTo[Try[List[Bidder]]] }

  def totalOwed(bidderId: Long): Try[BigDecimal] = {
    get(bidderId) flatMap {
      case Some(bidder) =>
        val totalOwed = Bid.totalByBidder(bidder).getOrElse(BigDecimal(0.0))
        val totalPayments = paymentsTotal(bidder).getOrElse(BigDecimal(0.0))
        Success(totalOwed - totalPayments)
      case None => Failure(new BidderException(s"Cannot find bidder ID $bidderId to get total owed"))
    }
  }

  def currentBidders(eventId: Long): Try[List[BidderData]] = {
    Bidder.allByEvent(eventId) map { bidders =>
      bidders map { bidder =>
        val payments = Bidder.payments(bidder).getOrElse(List())
        val winningBids = Bid.allByBidder(bidder).getOrElse(List())
        BidderData(bidder, payments, winningBids)
      }
    }
  }

  def loadFromDataSource() = {
    (biddersActor ? LoadFromDataSource).mapTo[Try[Boolean]]
  }
}
