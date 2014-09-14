package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import actors.ItemsActor._
import play.api.libs.json.Json
import controllers.ItemController.ItemData
import misc.Util
import scala.language.postfixOps

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal)
object Item extends ((Option[Long], String, String, String, String, BigDecimal) => Item) {
  implicit val timeout = Timeout(3 seconds)

  implicit val itemFormat = Json.format[Item]

  def allCategories() = (itemsActor ? GetCategories).mapTo[List[String]]
  def allDonors() = (itemsActor ? GetDonors).mapTo[List[String]]
  def all() = (itemsActor ? GetItems).mapTo[List[Item]]

  def allByCategory(category: String) = (itemsActor ? GetItemsByCategory(category)).mapTo[List[Item]]

  def get(id: Long) = (itemsActor ? GetItem(id)).mapTo[Option[Item]]

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal) =
    (itemsActor ? Item(None, itemNumber, category, donor, description, minbid)).mapTo[Option[Item]] foreach { itemOpt =>
      itemOpt map { _ => updateItems() }
    }

  def delete(id: Long) = (itemsActor ? DeleteItem(id)).mapTo[Option[Item]] foreach { itemOpt =>
    itemOpt map { _ => updateItems() }
  }

  def winningBids(item: Item) = (itemsActor ? WinningBidsByItem(item)).mapTo[Option[List[WinningBid]]]
  def winningBids(bidder: Bidder) = (itemsActor ? WinningBidsByBidder(bidder)).mapTo[Option[List[WinningBid]]]

  def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal) =
    (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[Option[WinningBid]] foreach { winningBidOpt =>
      winningBidOpt map { _ => updateItems() }
    }

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal) =
    (itemsActor ? EditWinningBid(winningBidId, bidder, item, amount)).mapTo[Option[WinningBid]] foreach { winningBidOpt =>
      winningBidOpt map { _ => updateItems() }
    }

  def deleteWinningBid(winningBidId: Long) =
    (itemsActor ? DeleteWinningBid(winningBidId)).mapTo[Option[WinningBid]] foreach { winningBidOpt =>
      winningBidOpt map { _ => updateItems() }
    }

  def updateItems(): List[ItemData] = {
    val isFuture = Item.all() map { items =>
      val dataFuture = items map { item =>
        Item.winningBids(item) map { bidsOpt =>
          ItemData(item, bidsOpt.get)
        }
      }
      dataFuture map { Await.result(_, Util.defaultAwaitTimeout) }
    }
    Await.result(isFuture, Util.defaultAwaitTimeout)
  }

  def loadFromDataSource() = {
    (itemsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)
object WinningBid {

  implicit val winningBidFormat = Json.format[WinningBid]

  def allByBidder(bidder: Bidder) = Item.winningBids(bidder)

  def totalByBidder(bidder: Bidder) = allByBidder(bidder) map { bidsOption =>
    bidsOption map { bidsList =>
      (BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount }
    }
  }
}
