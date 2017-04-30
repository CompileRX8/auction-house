package models

import javax.inject.Inject

import scala.concurrent.duration._
import akka.util.Timeout
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import persistence.BiddersPersistence
import play.api.Logger

case class BidderException(message: String, cause: Exception = null) extends Exception

case class BidderData(bidder: Bidder, payments: List[Payment], winningBids: List[WinningBid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)
object Payment extends((Option[Long], Bidder, String, BigDecimal) => Payment)

case class Bidder(id: Option[Long], name: String)
object Bidder extends ((Option[Long], String) => Bidder)

class BidderHandler @Inject()(biddersPersistence: BiddersPersistence, itemHandler: ItemHandler)(implicit val ec: ExecutionContext) {

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  implicit val timeout = Timeout(15 seconds)

  def payments(bidder: Bidder): Future[List[Payment]] =
    biddersPersistence.paymentsByBidder(bidder)
//    (biddersActor ? Payments(bidder)).mapTo[Future[List[Payment]]].flatMap( fList => fList )

  def paymentsTotal(bidder: Bidder): Future[BigDecimal] = payments(bidder) map { ps =>
    (BigDecimal(0.0) /: ps) { (sum, p) => sum + p.amount }
  }

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Future[Payment] =
    get(bidderId) flatMap {
      case Some(bidder) =>
        biddersPersistence.create(Payment(None, bidder, description, amount))
//       (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Future[Payment]].flatMap( fPayment => fPayment )
      case None =>
        Future.failed(BidderException(s"Cannot find bidder ID $bidderId to add payment"))
    }

  def all(): Future[List[Bidder]] = biddersPersistence.sortedBidders
//    val getBiddersResponse: Future[Future[List[Bidder]]] = (biddersActor ? GetBidders).mapTo[Future[List[Bidder]]]
//    getBiddersResponse.flatMap( fList => {
//      Logger.info(s"After all that fList is a ${fList.getClass}")
//      fList
//    } )
//  }

  def get(id: Long): Future[Option[Bidder]] = {
    biddersPersistence.bidderById(id)
//    (biddersActor ? GetBidder(id)).mapTo[Future[Option[Bidder]]].flatMap( fOpt => fOpt )
  }

  def create(name: String): Future[Bidder] = {
    biddersPersistence.create(Bidder(None, name))
/*
    (biddersActor ? Bidder(None, name)).mapTo[Future[Future[Bidder]]]
      .flatMap( fBidder => {
        Logger.info(s"fBidder is a ${fBidder.getClass}")
        fBidder
      }.flatMap( f => {
        Logger.info(s"f is a ${f.getClass}")
        f
      } )
      )
*/
  }

  def delete(id: Long): Future[Bidder] = {
    biddersPersistence.delete(Bidder(Some(id), ""))
//    (biddersActor ? DeleteBidder(id)).mapTo[Future[Bidder]].flatMap( fBidder => fBidder )
  }

  def edit(id: Long, name: String): Future[Bidder] = {
    biddersPersistence.edit(Bidder(Some(id), name))
//    (biddersActor ? EditBidder(Bidder(Some(id), name))).mapTo[Future[Bidder]].flatMap( fBidder => fBidder )
  }

  def totalOwed(bidderId: Long): Future[BigDecimal] = {
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
//      Logger.info(s"currentBidders sees bidders as a ${bidders.getClass}")
      val listFutureBidderData = bidders map { bidder =>
//        Logger.info(s"currentBidders sees bidder as a ${bidder.getClass}")
        val bidderPayments = payments(bidder)
        val bidderWinningBids = itemHandler.winningBids(bidder)
        bidderPayments flatMap { bps =>
          bidderWinningBids map { bwbs =>
//            Logger.info(s"currentBidders sees bps as a ${bps.getClass} and bwbs as a ${bwbs.getClass}")
            BidderData(bidder, bps, bwbs)
          }
        }
      }
      Future.sequence(listFutureBidderData)
    }
  }

  def loadFromDataSource(): Future[Boolean] = {
    Future.successful(true)
//    (biddersActor ? LoadFromDataSource).mapTo[Future[Boolean]].flatMap(b => b)
  }
}
