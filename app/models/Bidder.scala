package models

import javax.inject.{ Inject, Singleton }

import actors.BiddersActor
import akka.actor.ActorSystem

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class BidderException(message: String, cause: Exception = null) extends Exception

case class BidderData(bidder: Bidder, payments: List[Payment], winningBids: List[WinningBid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: Double)
object Payment extends((Option[Long], Bidder, String, Double) => Payment)

case class Bidder(id: Option[Long], name: String)
object Bidder extends ((Option[Long], String) => Bidder)

@Singleton
class BidderHandler @Inject()(actorSystem: ActorSystem, itemHandler: ItemHandler, implicit val ec: ExecutionContext) {
  import BiddersActor._

  private val biddersActor = actorSystem.actorOf(BiddersActor.props)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(15 seconds)

  def payments(bidder: Bidder): Future[List[Payment]] =
    (biddersActor ? Payments(bidder)).mapTo[List[Payment]]

  def paymentsTotal(bidder: Bidder): Future[Double] = payments(bidder) map { ps =>
    (0.0 /: ps) { (sum, p) => sum + p.amount }
  }

  def addPayment(bidderId: Long, description: String, amount: Double): Future[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
       (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Payment]
      case None =>
        Future.failed(BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  def all(): Future[List[Bidder]] = (biddersActor ? GetBidders).mapTo[List[Bidder]]

  def get(id: Long): Future[Option[Bidder]] = (biddersActor ? GetBidder(id)).mapTo[Option[Bidder]]

  def create(name: String): Future[Bidder] = (biddersActor ? Bidder(None, name)).mapTo[Bidder]

  def delete(id: Long): Future[Bidder] = (biddersActor ? DeleteBidder(id)).mapTo[Bidder]

  def edit(id: Long, name: String): Future[Bidder] = (biddersActor ? EditBidder(Bidder(Some(id), name))).mapTo[Bidder]

  def totalOwed(bidderId: Long): Future[Double] = {
    get(bidderId) flatMap {
      case Some(bidder) =>
        val totalOwed = itemHandler.totalWinningBidsByBidder(bidder)
        val totalPayments = paymentsTotal(bidder)
        totalOwed flatMap { totalOwedAmount =>
          Future.fold(List(totalPayments))(totalOwedAmount) { (remainingOwedAmount, paymentAmount) =>
            remainingOwedAmount - paymentAmount
          }
        }
      case None => Future.failed(BidderException(s"Cannot find bidder ID $bidderId to get total owed"))
    }
  }

  def currentBidders(): Future[List[BidderData]] = {
    all() flatMap { bidders =>
      val listFutureBidderData = bidders map { bidder =>
        val bidderPayments = payments(bidder)
        val bidderWinningBids = itemHandler.winningBids(bidder)
        bidderPayments flatMap { bps =>
          bidderWinningBids map { bwbs =>
            BidderData(bidder, bps, bwbs)
          }
        }
      }
      Future.sequence(listFutureBidderData)
    }
  }

  def loadFromDataSource(): Future[Boolean] = {
    (biddersActor ? LoadFromDataSource).mapTo[Boolean]
  }
}
