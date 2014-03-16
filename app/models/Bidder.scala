package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import misc.Util

case class Bidder(id: Option[Long], name: String)
object Bidder extends ((Option[Long], String) => Bidder) {
  import actors.BiddersActor._

  implicit val timeout = Timeout(3 seconds)

  def total(bidder: Bidder): Future[Option[BigDecimal]] = {
    WinningBid.totalByBidder(bidder)
  }

  def owes(bidder: Bidder): Future[Option[BigDecimal]] = {
    val totalBidsFuture = total(bidder)
    val paymentsFuture = payments(bidder)

    totalBidsFuture zip paymentsFuture map { case (totalBidsOption, paymentsOption) =>
      totalBidsOption flatMap { totalBids =>
        paymentsOption map { payments =>
          val totalPayments = payments.map(_.amount).sum
          totalBids - totalPayments
        }
      }
    }
  }

  def payments(bidder: Bidder): Future[Option[List[Payment]]] =
    (biddersActor ? Payments(bidder)).mapTo[Option[List[Payment]]]

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Future[Option[Payment]] =
    get(bidderId) flatMap { bidder => addPayment(bidder.get, description, amount) }

  def addPayment(bidder: Bidder, description: String, amount: BigDecimal): Future[Option[Payment]] =
    (biddersActor ? Payment(None, bidder, description, amount)).mapTo[Option[Payment]]

  def all(): Future[List[Bidder]] = (biddersActor ? GetBidders).mapTo[List[Bidder]]

  def get(id: Long): Future[Option[Bidder]] = (biddersActor ? GetBidder(id)).mapTo[Option[Bidder]]

  def create(name: String): Future[Option[Bidder]] = {
    (biddersActor ? Bidder(None, name)).mapTo[Option[Bidder]]
  }

  def delete(id: Long): Future[Option[Bidder]] = (biddersActor ? DeleteBidder(id)).mapTo[Option[Bidder]]

  def loadFromDataSource(): Future[Boolean] = {
    (biddersActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class Payment(id: Option[Long], bidder: Bidder, description: String, amount: BigDecimal) {
  override def toString = {
    s"${description} ${Util.formatMoney(amount)}"
  }
}
object Payment { //} extends ((Option[Long], Bidder, String, BigDecimal) => Payment) {

  def allByBidder(bidder: Bidder): Future[Option[List[Payment]]] = Bidder.payments(bidder)

  def totalByBidder(bidder: Bidder): Future[Option[BigDecimal]] = Bidder.total(bidder)

  def create(bidder: Bidder, description: String, amount: BigDecimal): Future[Option[Payment]] = Bidder.addPayment(bidder, description, amount)
}
