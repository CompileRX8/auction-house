package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import misc.Util
import play.api.libs.json.Json
import scala.concurrent.{Awaitable, Future, Await}
import scala.language.postfixOps
import scala.util.{Success, Failure, Try}

case class BidderException(message: String, cause: Exception = null) extends Exception

case class BidderData(bidder: Bidder, payments: List[Payment], winningBids: List[WinningBid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)

case class Bidder(id: Option[Long], name: String)
object Bidder extends ((Option[Long], String) => Bidder) {
  import actors.BiddersActor._

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(3 seconds)

  private def wait[T](awaitable: Awaitable[T]): T = {
    Await.result(awaitable, Util.defaultAwaitTimeout)
  }

  def payments(bidder: Bidder) =
    wait { (biddersActor ? Payments(bidder)).mapTo[Try[List[Payment]]] }

  def paymentsTotal(bidder: Bidder): Try[BigDecimal] = payments(bidder) flatMap { ps =>
    Try((BigDecimal(0.0) /: ps) { (sum, p) => sum + p.amount })
  }

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Try[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
        wait { (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Try[Payment]] }
      case None =>
        Failure(new BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  def all() = wait { (biddersActor ? GetBidders).mapTo[Try[List[Bidder]]] }

  def get(id: Long) = wait { (biddersActor ? GetBidder(id)).mapTo[Try[Option[Bidder]]] }

  def create(name: String) = wait { (biddersActor ? Bidder(None, name)).mapTo[Try[Bidder]] }

  def delete(id: Long) = wait { (biddersActor ? DeleteBidder(id)).mapTo[Try[Bidder]] }

  def edit(id: Long, name: String) = wait { (biddersActor ? EditBidder(Bidder(Some(id), name))).mapTo[Try[Bidder]] }

  def totalOwed(bidderId: Long): Try[BigDecimal] = {
    get(bidderId) flatMap {
      case Some(bidder) =>
        val totalOwed = WinningBid.totalByBidder(bidder).getOrElse(BigDecimal(0.0))
        val totalPayments = paymentsTotal(bidder).getOrElse(BigDecimal(0.0))
        Success(totalOwed - totalPayments)
      case None => Failure(new BidderException(s"Cannot find bidder ID $bidderId to get total owed"))
    }
  }

  def currentBidders(): Try[List[BidderData]] = {
    Bidder.all() flatMap { bidders =>
      Try {
        bidders map { bidder =>
          val payments = Bidder.payments(bidder).getOrElse(List())
          val winningBids = WinningBid.allByBidder(bidder).getOrElse(List())
          BidderData(bidder, payments, winningBids)
        }
      }
    }
  }

  def loadFromDataSource() = {
    (biddersActor ? LoadFromDataSource).mapTo[Boolean]
  }
}
