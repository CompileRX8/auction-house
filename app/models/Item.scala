package models

import actors.ItemsActor._
import akka.pattern.ask
import akka.util.Timeout
import persistence.BidsPersistence.bids
import persistence.ItemsPersistence.items
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

case class ItemException(message: String, cause: Exception = null) extends Exception(message, cause)

case class ItemData(item: Item, bids: List[Bid])

case class Item(id: Option[Long], event: Event, itemNumber: String, description: String, minbid: BigDecimal)

object Item extends ((Option[Long], Event, String, String, BigDecimal) => Item) {
  implicit val timeout = Timeout(3 seconds)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]
  implicit val itemDataFormat = Json.format[ItemData]

  def all(): Future[List[Item]] = items.all()

  def get(id: Long): Future[Option[Item]] = items.forId(id)

  def create(eventId: Long, itemNumber: String, description: String, minbid: BigDecimal): Future[Item] =
    Event.get(eventId) flatMap {
      case Some(event) =>
        items.create(Item(None, event, itemNumber, description, minbid))
      case None =>
        Future.failed(new ItemException(s"Cannot find event ID $eventId to create item"))
    }

  def delete(id: Long) = items.delete(id)

  def edit(id: Long, itemNumber: String, description: String, minbid: BigDecimal): Future[Option[Item]] =
    get(id) flatMap {
      case Some(item @ Item(Some(_), event, _, _, _)) =>
        items.edit(Item(Some(id), event, itemNumber, description, minbid))
      case None =>
        Future.failed(new ItemException(s"Cannot find item ID $id to edit item"))
    }

  def allByEvent(eventId: Long) = items.forEventId(eventId)

  def currentItems(eventId: Long): Future[List[ItemData]] = {
    for {
      items <- Item.allByEvent(eventId)
      item <- items
      bids <- Bid.allByItem(item.id.get)
    } yield {
      ItemData(item, bids)
    }
  }

  def loadFromDataSource() = {
    (itemsActor ? LoadFromDataSource).mapTo[Try[Boolean]]
  }
}

case class BidException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Bid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)

object Bid extends ((Option[Long], Bidder, Item, BigDecimal) => Bid) {
  implicit val timeout = Timeout(3 seconds)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]

  def allByBidder(bidderId: Long) = bids.forBidderId(bidderId)

  def allByItem(itemId: Long) = bids.forItemId(itemId)

  def allByEvent(eventId: Long): Future[List[Bid]] =
    Item.allByEvent(eventId) flatMap { items =>
      val itemBidsLists = items map { item =>
        allByItem(item.id.get)
      }
      Future.reduce(itemBidsLists)(_ ++ _)
    }

  def get(id: Long) = bids.forId(id)

  def create(bidder: Bidder, item: Item, amount: BigDecimal) =
    bids.create(Bid(None, bidder, item, amount))

  def edit(bidId: Long, bidder: Bidder, item: Item, amount: BigDecimal) =
    get(bidId) flatMap {
      case Some(b @ Bid(Some(_), _, _, _)) =>
        bids.edit(Bid(Some(bidId), bidder, item, amount))
      case None =>
        Future.failed(new BidException(s"Unable to find bid Id $bidId to edit"))
    }

  def delete(bidId: Long) = bids.delete(bidId)

  def winningBids(item: Item): Future[List[Bid]] =
    allByItem(item.id.get) map { bids =>
      val sortedBids = bids.sortBy(_.amount).reverse
      sortedBids.headOption map {
        _.amount
      } map { winningAmount =>
        sortedBids.takeWhile(_.amount == winningAmount)
      } match {
        case Some(list) => list
        case None => List()
      }
    }

  def winningBids(eventId: Long): Future[Map[Item, List[Bid]]] =
    allByEvent(eventId) map { bids =>
      (Map[Item, List[Bid]]() /: bids) { (m, bid) =>
        val bidsList = m.getOrElse(bid.item, List[Bid]()).::(bid)
        m + (bid.item -> bidsList)
      }
    }

  def winningBids(bidder: Bidder): Future[List[Bid]] = bidder match {
    case Bidder(Some(id), e@Event(Some(eventId), _, _), _, _) =>
      winningBids(eventId) map {
        _.values reduce {
          _ ++ _
        } filter {
          _.bidder.id.getOrElse(-1L) == id
        }
      }
    case _ =>
      Future.failed(new BidException(s"Unable to find winning bids without bidder ID and event ID"))
  }

  def totalByBidder(bidder: Bidder): Future[BigDecimal] =
    winningBids(bidder) map { bidsList =>
      (BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount }
    }
}
