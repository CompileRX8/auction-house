package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import misc.Util
import play.api.libs.json.Json
import scala.concurrent.Await
import scala.language.postfixOps

case class BidderData(bidder: Bidder, payments: List[Payment], winningBids: List[WinningBid])

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal)

case class Bidder(id: Option[Long], name: String)
object Bidder extends ((Option[Long], String) => Bidder) {
  import actors.BiddersActor._

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val bidderDataFormat = Json.format[BidderData]
  implicit val winningBidFormat = Json.format[WinningBid]

  implicit val timeout = Timeout(3 seconds)

  def payments(bidder: Bidder) =
    (biddersActor ? Payments(bidder)).mapTo[Option[List[Payment]]]

  def addPayment(bidderId: Long, description: String, amount: BigDecimal) =
    get(bidderId) map { bidderOpt =>
      bidderOpt map { bidder =>
        (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Option[Payment]] map { paymentOpt =>
          paymentOpt map { _ => updateBidders() }
        }
      }
    }

  def all() = (biddersActor ? GetBidders).mapTo[List[Bidder]]

  def get(id: Long) = (biddersActor ? GetBidder(id)).mapTo[Option[Bidder]]

  def create(name: String) = {
    (biddersActor ? Bidder(None, name)).mapTo[Option[Bidder]] map { bidderOpt =>
      bidderOpt map { _ => updateBidders() }
    }
  }

  def delete(id: Long) = {
    (biddersActor ? DeleteBidder(id)).mapTo[Option[Bidder]] map { bidderOpt =>
      bidderOpt map { _ => updateBidders() }
    }
  }

  def updateBidders(): List[BidderData] = {
    val biddersDataFuture = Bidder.all() map { bidders =>
      val dataFuture = bidders map { bidder =>
        WinningBid.allByBidder(bidder) flatMap { winningBidsOpt =>
          Bidder.payments(bidder) map { paymentsOpt =>
            BidderData(bidder, paymentsOpt.get, winningBidsOpt.get)
          }
        }
      }
      dataFuture.map { Await.result(_, Util.defaultAwaitTimeout) }
    }
    Await.result(biddersDataFuture, Util.defaultAwaitTimeout)
  }

  def loadFromDataSource() = {
    (biddersActor ? LoadFromDataSource).mapTo[Boolean]
  }
}
