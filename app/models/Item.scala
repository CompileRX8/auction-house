package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Awaitable, Await}
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import actors.ItemsActor._
import play.api.libs.json.Json
import misc.Util
import scala.language.postfixOps
import scala.util.Try

case class ItemException(message: String, cause: Exception = null) extends Exception

case class ItemData(item: Item, winningBids: List[WinningBid])

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal)
object Item extends ((Option[Long], String, String, String, String, BigDecimal, BigDecimal) => Item) {
  implicit val timeout = Timeout(3 seconds)

  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  private def wait[T](awaitable: Awaitable[T]): T = {
    Await.result(awaitable, Util.defaultAwaitTimeout)
  }

  def allCategories() = wait { (itemsActor ? GetCategories).mapTo[Try[List[String]]] }
  def allDonors() = wait { (itemsActor ? GetDonors).mapTo[Try[List[String]]] }
  def all() = wait { (itemsActor ? GetItems).mapTo[Try[List[Item]]] }

  def allByCategory(category: String) = wait { (itemsActor ? GetItemsByCategory(category)).mapTo[Try[List[Item]]] }

  def get(id: Long) = wait { (itemsActor ? GetItem(id)).mapTo[Try[Option[Item]]] }

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal) =
    wait { (itemsActor ? Item(None, itemNumber, category, donor, description, minbid, estvalue)).mapTo[Try[Item]] }

  def delete(id: Long) = wait { (itemsActor ? DeleteItem(id)).mapTo[Try[Item]] }

  def edit(id: Long, itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal) =
    wait { (itemsActor ? EditItem(Item(Some(id), itemNumber, category, donor, description, minbid, estvalue))).mapTo[Try[Item]] }

  def getWinningBid(id: Long) = wait { (itemsActor ? GetWinningBid(id)).mapTo[Try[Option[WinningBid]]] }
  def winningBids(item: Item) = wait { (itemsActor ? WinningBidsByItem(item)).mapTo[Try[List[WinningBid]]] }
  def winningBids(bidder: Bidder) = wait { (itemsActor ? WinningBidsByBidder(bidder)).mapTo[Try[List[WinningBid]]] }

  def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal) =
    wait { (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[Try[WinningBid]] }

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal) =
    wait { (itemsActor ? EditWinningBid(winningBidId, bidder, item, amount)).mapTo[Try[WinningBid]] }

  def deleteWinningBid(winningBidId: Long) =
    wait { (itemsActor ? DeleteWinningBid(winningBidId)).mapTo[Try[WinningBid]] }

  def currentItems(): Try[List[ItemData]] = {
    Item.all() flatMap { items =>
      Try(items map { item =>
        ItemData(item, winningBids(item).getOrElse(List()))
      })
    }
  }

  def loadFromDataSource() = {
    (itemsActor ? LoadFromDataSource).mapTo[Try[Boolean]]
  }
}

case class WinningBidException(message: String, cause: Exception = null) extends Exception

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)
object WinningBid {

  implicit val winningBidFormat = Json.format[WinningBid]

  def get(id: Long) = Item.getWinningBid(id)

  def allByBidder(bidder: Bidder) = Item.winningBids(bidder)

  def totalByBidder(bidder: Bidder): Try[BigDecimal] =
    allByBidder(bidder) flatMap { bidsList =>
      Try((BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount})
    }

  def totalEstValueByBidder(bidder: Bidder): Try[BigDecimal] =
    allByBidder(bidder) flatMap { bidsList =>
      Try((BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.item.estvalue})
    }
}
