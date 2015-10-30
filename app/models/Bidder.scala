package models

import akka.util.Timeout
import persistence.BiddersPersistence.bidders
import persistence.PaymentsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class BidderException(message: String, cause: Exception = null) extends Exception(message, cause)

case class BidderData(bidder: Bidder, payments: List[Payment], bids: List[Bid])

case class PaymentException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)

case class Bidder(id: Option[Long], event: Event, bidderNumber: String, contact: Contact)
object Bidder extends ((Option[Long], Event, String, Contact) => Bidder) {

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val bidFormat = Json.format[Bid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(3 seconds)

  def payments(bidder: Bidder) =
    PaymentsPersistence.payments.forBidderId(bidder.id.get)

  def paymentsTotal(bidder: Bidder): Future[BigDecimal] = payments(bidder) map { ps =>
    (BigDecimal(0.0) /: ps) { (sum, p) => sum + p.amount }
  }

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Future[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
        PaymentsPersistence.payments.create(Payment(None, bidder, description, amount))
      case None =>
        Future.failed(new BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  def all(): Future[List[Bidder]] = bidders.all()

  def get(id: Long): Future[Option[Bidder]] = bidders.forId(id)

  def create(eventId: Long, bidderNumber: String, contactId: Long): Future[Bidder] =
    for {
      eventOpt <- Event.get(eventId)
      contactOpt <- Contact.get(contactId)
      event <- eventOpt
      contact <- contactOpt
    } yield {
      bidders.create(Bidder(None, event, bidderNumber, contact))
    }

  def delete(id: Long): Future[Option[Bidder]] = bidders.delete(id)

  def edit(id: Long, bidderNumber: String, contactId: Long): Future[Option[Bidder]] =
    for {
      bidderOpt <- get(id)
      bidder <- bidderOpt
      eventOpt <- Event.get(bidder.event.id.get)
      event <- eventOpt
      contactOpt <- Contact.get(contactId)
      contact <- contactOpt
    } yield {
      bidders.edit(Bidder(Some(id), event, bidderNumber, contact))
    }

  def allByEvent(eventId: Long) = bidders.forEventId(eventId)

  def totalOwed(bidderId: Long): Future[BigDecimal] = {
    get(bidderId) flatMap {
      case Some(bidder) =>
        val totalOwed = Bid.totalByBidder(bidder).fallbackTo(Future(BigDecimal(0.0)))
        val totalPayments = paymentsTotal(bidder).fallbackTo(Future(BigDecimal(0.0)))
        totalOwed flatMap { o =>
          totalPayments map { p =>
            o - p
          }
        }
      case None =>
        throw new BidderException(s"Cannot find bidder ID $bidderId to get total owed")
    }
  }

  def currentBidders(eventId: Long): Future[List[BidderData]] = {
    Bidder.allByEvent(eventId) map { bidders =>
      for {
        bidder <- bidders
        payments <- Bidder.payments(bidder)
        bids <- Bid.allByBidder(bidder.id.get)
      } yield {
        BidderData(bidder, payments, bids)
      }
    }
  }

  def loadFromDataSource() = {
  }
}
