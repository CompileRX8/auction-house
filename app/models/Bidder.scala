package models

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import misc.Util
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable, ExecutionContext, Future}
import scala.language.postfixOps

case class BidderException(message: String, cause: Exception = null) extends Exception

case class BidderData(bidder: Bidder, payments: List[Payment], winningBids: List[WinningBid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)

case class Bidder(id: Option[Long], name: String)

class BidderServiceImpl @Inject()(@Named("bidders-actor") biddersActor: ActorRef, winningBidService: WinningBidService)(implicit ec: ExecutionContext) extends BidderService {

  import actors.BiddersActor._

  implicit val itemFormat = Json.format[Item]
  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(3 seconds)

  private def wait[T](awaitable: Awaitable[T]): T = {
    Await.result(awaitable, Util.defaultAwaitTimeout)
  }

  override def payments(bidder: Bidder): Future[List[Payment]] =
    (biddersActor ? Payments(bidder)).mapTo[List[Payment]]

  override def paymentsTotal(bidder: Bidder): Future[Option[BigDecimal]] = payments(bidder) map {
    case ps @ List(_) => Some((BigDecimal(0.0) /: ps) { (sum, p) => sum + p.amount })
    case Nil => None
  }

  override def addPayment(bidderId: Long, description: String, amount: BigDecimal): Future[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
        (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Payment]
      case None =>
        Future.failed(new BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  override def all(): Future[List[Bidder]] =
    (biddersActor ? GetBidders).mapTo[List[Bidder]]

  override def get(id: Long): Future[Option[Bidder]] =
    (biddersActor ? GetBidder(id)).mapTo[Option[Bidder]]

  override def create(name: String): Future[Bidder] =
    (biddersActor ? Bidder(None, name)).mapTo[Bidder]

  override def delete(id: Long): Future[Bidder] =
    (biddersActor ? DeleteBidder(id)).mapTo[Bidder]

  override def edit(id: Long, name: String): Future[Bidder] =
    (biddersActor ? EditBidder(Bidder(Some(id), name))).mapTo[Bidder]

  override def totalOwed(bidderId: Long): Future[BigDecimal] = {
    get(bidderId) flatMap {
      case Some(bidder) =>
        paymentsTotal(bidder) flatMap { totalPaymentsOpt =>
          winningBidService.totalByBidder(bidder) map { totalOwedOpt =>
            totalOwedOpt.getOrElse(BigDecimal(0.0)) - totalPaymentsOpt.getOrElse(BigDecimal(0.0))
          }
        }
      case None => Future.failed(new BidderException(s"Cannot find bidder ID $bidderId to get total owed"))
    }
  }

  override def currentBidders(): Future[List[BidderData]] = for {
    bidders <- all()
    bidder <- bidders
    ps <- payments(bidder)
    wbs <- winningBidService.allByBidder(bidder)
  } yield {
    BidderData(bidder, ps, wbs)
  }

  override def loadFromDataSource(): Future[Boolean] = {
    (biddersActor ? LoadFromDataSource).mapTo[Boolean]
  }
}
