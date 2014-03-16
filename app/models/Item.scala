package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import actors.ItemsActor._

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal)
object Item extends ((Option[Long], String, String, String, String, BigDecimal) => Item) {
  implicit val timeout = Timeout(3 seconds)

  def allCategories(): Future[List[String]] = (itemsActor ? GetCategories).mapTo[List[String]]
  def allDonors(): Future[List[String]] = (itemsActor ? GetDonors).mapTo[List[String]]
  def all(): Future[List[Item]] = (itemsActor ? GetItems).mapTo[List[Item]]

  def allByCategory(category: String): Future[List[Item]] = (itemsActor ? GetItemsByCategory(category)).mapTo[List[Item]]

  def get(id: Long): Future[Option[Item]] = (itemsActor ? GetItem(id)).mapTo[Option[Item]]

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal): Future[Option[Item]] =
    (itemsActor ? Item(None, itemNumber, category, donor, description, minbid)).mapTo[Option[Item]]

  def delete(id: Long): Future[Option[Item]] = (itemsActor ? DeleteItem(id)).mapTo[Option[Item]]

  def winningBids(item: Item): Future[Option[List[WinningBid]]] = (itemsActor ? WinningBidsByItem(item)).mapTo[Option[List[WinningBid]]]
  def winningBids(bidder: Bidder): Future[Option[List[WinningBid]]] = (itemsActor ? WinningBidsByBidder(bidder)).mapTo[Option[List[WinningBid]]]

  def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal): Future[Option[WinningBid]] =
    (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[Option[WinningBid]]

  def editWinningBid(winningBidId: Long, bidder: Bidder, amount: BigDecimal): Future[Option[WinningBid]] =
    (itemsActor ? EditWinningBid(winningBidId, bidder, amount)).mapTo[Option[WinningBid]]

  def loadFromDataSource(): Future[Boolean] = {
    (itemsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)
object WinningBid {
  def allByBidder(bidder: Bidder): Future[Option[List[WinningBid]]] = Item.winningBids(bidder)

  def totalByBidder(bidder: Bidder): Future[Option[BigDecimal]] = allByBidder(bidder) map { bidsOption =>
    bidsOption map { bidsList =>
      (BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount }
    }
  }

  def allByItem(item: Item): Future[Option[List[WinningBid]]] = Item.winningBids(item)

  def create(bidder: Bidder, item: Item, amount: BigDecimal): Future[Option[WinningBid]] = Item.addWinningBid(bidder, item, amount)

  def edit(winningBidId: Long, bidder: Bidder, amount: BigDecimal): Future[Option[WinningBid]] = Item.editWinningBid(winningBidId, bidder, amount)
}
