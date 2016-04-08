package models

import javax.inject.{Inject, Named}

import actors.ItemsActor._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class ItemException(message: String, cause: Exception = null) extends Exception

case class ItemData(item: Item, winningBids: List[WinningBid])

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal)

class ItemServiceImpl @Inject()(@Named("items-actor") itemsActor: ActorRef)(implicit ec: ExecutionContext) extends ItemService {
  implicit val timeout = Timeout(3 seconds)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  override def allCategories(): Future[List[String]] =
    (itemsActor ? GetCategories).mapTo[List[String]]

  override def allDonors(): Future[List[String]] =
    (itemsActor ? GetDonors).mapTo[List[String]]

  override def all(): Future[List[Item]] =
    (itemsActor ? GetItems).mapTo[List[Item]]

  override def allByCategory(category: String): Future[List[Item]] =
    (itemsActor ? GetItemsByCategory(category)).mapTo[List[Item]]

  override def get(id: Long): Future[Option[Item]] =
    (itemsActor ? GetItem(id)).mapTo[Option[Item]]

  override def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item] =
    (itemsActor ? Item(None, itemNumber, category, donor, description, minbid, estvalue)).mapTo[Item]

  override def delete(id: Long): Future[Item] =
    (itemsActor ? DeleteItem(id)).mapTo[Item]

  override def edit(id: Long, itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item] =
    (itemsActor ? EditItem(Item(Some(id), itemNumber, category, donor, description, minbid, estvalue))).mapTo[Item]

  override def getWinningBid(id: Long): Future[Option[WinningBid]] =
    (itemsActor ? GetWinningBid(id)).mapTo[Option[WinningBid]]

  override def winningBids(item: Item): Future[List[WinningBid]] =
    (itemsActor ? WinningBidsByItem(item)).mapTo[List[WinningBid]]

  override def winningBids(bidder: Bidder): Future[List[WinningBid]] =
    (itemsActor ? WinningBidsByBidder(bidder)).mapTo[List[WinningBid]]

  override def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] =
    (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[WinningBid]

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] =
    (itemsActor ? EditWinningBid(winningBidId, bidder, item, amount)).mapTo[WinningBid]

  override def deleteWinningBid(winningBidId: Long): Future[WinningBid] =
    (itemsActor ? DeleteWinningBid(winningBidId)).mapTo[WinningBid]

  override def currentItems(): Future[List[ItemData]] = for {
    items <- all()
    item <- items
    wbs <- winningBids(item)
  } yield {
    ItemData(item, wbs)
  }

  override def loadFromDataSource(): Future[Boolean] = {
    (itemsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class WinningBidException(message: String, cause: Exception = null) extends Exception

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)

class WinningBidServiceImpl @Inject()(itemService: ItemService)(implicit ec: ExecutionContext) extends WinningBidService {

  implicit val bidderFormat = Json.format[Bidder]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]

  override def get(id: Long): Future[Option[WinningBid]] = itemService.getWinningBid(id)

  override def allByBidder(bidder: Bidder): Future[List[WinningBid]] = itemService.winningBids(bidder)

  override def totalByBidder(bidder: Bidder): Future[Option[BigDecimal]] =
    allByBidder(bidder) map {
      case bidsList @ List(_) =>
        Some((BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount })
      case Nil =>
        None
    }

  override def totalEstValueByBidder(bidder: Bidder): Future[Option[BigDecimal]] =
    allByBidder(bidder) map {
      case bidsList @ List(_) =>
        Some((BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.item.estvalue })
      case Nil =>
        None
    }
}
