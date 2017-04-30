package models

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import persistence.ItemsPersistence
import play.api.libs.json.Json

import scala.language.postfixOps

case class ItemException(message: String, cause: Exception = null) extends Exception

case class ItemData(item: Item, winningBids: List[WinningBid])

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal)
object Item extends ((Option[Long], String, String, String, String, BigDecimal, BigDecimal) => Item)

class ItemHandler @Inject()(itemsPersistence: ItemsPersistence)(implicit val ec: ExecutionContext) {
  implicit val timeout = Timeout(15 seconds)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  private val itemStringList = (f: Item => String) => all() map { items =>
    items.map(f).distinct.sorted
  }

  def allCategories(): Future[List[String]] = {
    itemStringList { _.category }
//    (itemsActor ? GetCategories).mapTo[List[String]]
  }
  def allDonors(): Future[List[String]] = {
    itemStringList { _.donor }
//    (itemsActor ? GetDonors).mapTo[List[String]]
  }
  def all(): Future[List[Item]] = {
    itemsPersistence.sortedItems
//    (itemsActor ? GetItems).mapTo[Future[List[Item]]].flatMap( fList => fList )
  }

  def allByCategory(category: String): Future[List[Item]] = {
    itemsPersistence.sortedItems map { items => items filter { i => i.category.equals(category) } }
//    (itemsActor ? GetItemsByCategory(category)).mapTo[Future[List[Item]]].flatMap( fItems => fItems )
  }

  def get(id: Long): Future[Option[Item]] = {
    itemsPersistence.itemById(id)
//    (itemsActor ? GetItem(id)).mapTo[Future[Option[Item]]].flatMap( fOpt => fOpt )
  }

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item] =
    itemsPersistence.create(Item(None, itemNumber, category, donor, description, minbid, estvalue))
//    (itemsActor ? Item(None, itemNumber, category, donor, description, minbid, estvalue)).mapTo[Future[Item]].flatMap( fItem => fItem )

  def delete(id: Long): Future[Item] =
    get(id) flatMap {
      case Some(item) => itemsPersistence.delete(item)
    }
//    (itemsActor ? DeleteItem(id)).mapTo[Future[Item]].flatMap( fItem => fItem )

  def edit(id: Long, itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item] =
    itemsPersistence.edit(Item(Some(id), itemNumber, category, donor, description, minbid, estvalue))
//  (itemsActor ? EditItem(Item(Some(id), itemNumber, category, donor, description, minbid, estvalue))).mapTo[Future[Item]].flatMap( fItem => fItem )

  def getWinningBid(id: Long): Future[Option[WinningBid]] = {
    itemsPersistence.winningBidById(id)
//    (itemsActor ? GetWinningBid(id)).mapTo[Future[Option[WinningBid]]].flatMap( fOpt => fOpt )
  }
  def winningBids(item: Item): Future[List[WinningBid]] = {
    itemsPersistence.winningBidsByItem(item)
//    (itemsActor ? WinningBidsByItem(item)).mapTo[Future[List[WinningBid]]].flatMap( fList => fList )
  }
  def winningBids(bidder: Bidder): Future[List[WinningBid]] = {
    itemsPersistence.winningBidsByBidder(bidder)
//    (itemsActor ? WinningBidsByBidder(bidder)).mapTo[Future[List[WinningBid]]].flatMap( fList => fList )
  }

  def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] =
    itemsPersistence.create(WinningBid(None, bidder, item, amount))
//    (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[Future[WinningBid]].flatMap( fWB => fWB )

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] =
    itemsPersistence.edit(WinningBid(Some(winningBidId), bidder, item, amount))
//    (itemsActor ? EditWinningBid(winningBidId, bidder, item, amount)).mapTo[Future[WinningBid]].flatMap( fWB => fWB )

  def deleteWinningBid(winningBidId: Long): Future[WinningBid] =
    getWinningBid(winningBidId) flatMap {
      case Some(winningBid) => itemsPersistence.delete(winningBid)
    }
//    (itemsActor ? DeleteWinningBid(winningBidId)).mapTo[Future[WinningBid]].flatMap( fWB => fWB )

  def totalWinningBidsByBidder(bidder: Bidder): Future[BigDecimal] =
    winningBids(bidder) map { bidsList =>
      (BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount}
    }

  def totalEstValueByBidder(bidder: Bidder): Future[BigDecimal] =
    winningBids(bidder) map { bidsList =>
      (BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.item.estvalue}
    }

  def currentItems(): Future[List[ItemData]] = {
    all() flatMap { items =>
      val listFutureItemData = items map { item =>
        winningBids(item).fallbackTo(Future.successful(List())) map { wbs =>
          ItemData(item, wbs)
        }
      }
      Future.sequence(listFutureItemData)
    }
  }

  def loadFromDataSource(): Future[Boolean] = {
    Future.successful(true)
//    (itemsActor ? LoadFromDataSource).mapTo[Future[Boolean]].flatMap(b => b)
  }
}

case class WinningBidException(message: String, cause: Exception = null) extends Exception

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)
object WinningBid extends ((Option[Long], Bidder, Item, BigDecimal) => WinningBid)
