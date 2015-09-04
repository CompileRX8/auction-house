package models

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import actors.ItemsActor._
import play.api.libs.json.Json
import misc.Util
import scala.language.postfixOps
import scala.util.{Failure, Try}

case class ItemException(message: String, cause: Exception = null) extends Exception(message, cause)

case class ItemData(item: Item, bids: List[Bid])

case class Item(id: Option[Long], event: Event, itemNumber: String, description: String, minbid: BigDecimal)

object Item {
  // extends ((Option[Long], Event, String, String, BigDecimal) => Item) {
  implicit val timeout = Timeout(3 seconds)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]
  implicit val itemDataFormat = Json.format[ItemData]

  def all() = Util.wait {
    (itemsActor ? GetItems).mapTo[Try[List[Item]]]
  }

  def get(id: Long) = Util.wait {
    (itemsActor ? GetItem(id)).mapTo[Try[Option[Item]]]
  }

  def create(eventId: Long, itemNumber: String, description: String, minbid: BigDecimal): Try[Item] =
    Event.get(eventId) flatMap {
      case Some(event) =>
        Util.wait {
          (itemsActor ? Item(None, event, itemNumber, description, minbid)).mapTo[Try[Item]]
        }
      case None => Failure(new ItemException(s"Cannot find event ID $eventId to create item"))
    }

  def delete(id: Long) = Util.wait {
    (itemsActor ? DeleteItem(id)).mapTo[Try[Item]]
  }

  def edit(id: Long, itemNumber: String, description: String, minbid: BigDecimal): Try[Item] =
    get(id) flatMap {
      case Some(item @ Item(Some(_), event, _, _, _)) =>
        Util.wait {
          (itemsActor ? EditItem(Item(Some(id), event, itemNumber, description, minbid))).mapTo[Try[Item]]
        }
      case None => Failure(new ItemException(s"Cannot find item ID $id to edit item"))
    }

  def allByEvent(eventId: Long) = Util.wait {
    (itemsActor ? ItemsForEvent(eventId)).mapTo[Try[List[Item]]]
  }

  def currentItems(eventId: Long): Try[List[ItemData]] = {
    Item.allByEvent(eventId) map { items =>
      items map { item =>
        ItemData(item, Bid.allByItem(item).getOrElse(List()))
      }
    }
  }

  def loadFromDataSource() = {
    (itemsActor ? LoadFromDataSource).mapTo[Try[Boolean]]
  }
}

case class BidException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Bid(id: Option[Long], bidder: Bidder, item: Item, amount: BigDecimal)

object Bid {

  implicit val bidFormat = Json.format[Bid]

  def allByBidder(bidder: Bidder) = Util.wait {
    (itemsActor ? BidsByBidder(bidder)).mapTo[Try[List[Bid]]]
  }

  def allByItem(item: Item) = Util.wait {
    (itemsActor ? BidsByItem(item)).mapTo[Try[List[Bid]]]
  }

  def allByEvent(eventId: Long): Try[List[Bid]] =
    Item.allByEvent(eventId) flatMap { items =>
      val itemBidsLists = items map { item =>
        allByItem(item)
      }
      (Try(List[Bid]()) /: itemBidsLists) { (tryAllEventBids, tryItemBidsList) =>
        tryItemBidsList flatMap { itemBidsList =>
          tryAllEventBids map {
            _ ++ itemBidsList
          }
        }
      }
    }

  def get(id: Long) = Util.wait {
    (itemsActor ? GetBid(id)).mapTo[Try[Option[Bid]]]
  }

  def create(bidder: Bidder, item: Item, amount: BigDecimal) =
    Util.wait {
      (itemsActor ? Bid(None, bidder, item, amount)).mapTo[Try[Bid]]
    }

  def edit(bidId: Long, bidder: Bidder, item: Item, amount: BigDecimal) =
    Util.wait {
      (itemsActor ? EditBid(bidId, bidder, item, amount)).mapTo[Try[Bid]]
    }

  def delete(bidId: Long) =
    Util.wait {
      (itemsActor ? DeleteBid(bidId)).mapTo[Try[Bid]]
    }

  def winningBids(item: Item): Try[List[Bid]] =
    allByItem(item) map { bids =>
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

  def winningBids(eventId: Long): Try[Map[Item, List[Bid]]] =
    Item.allByEvent(eventId) map { items =>
      val itemWinningBidsMap = (Map[Item, Try[List[Bid]]]() /: items) { (m, item) =>
        m + (item -> winningBids(item))
      }
      itemWinningBidsMap map { case (item, tryBids) => item -> tryBids.getOrElse(List()) }
    }

  def winningBids(bidder: Bidder): Try[List[Bid]] = bidder match {
    case Bidder(Some(id), e@Event(Some(eventId), _, _), _, _) =>
      winningBids(eventId) map {
        _.values reduce {
          _ ++ _
        } filter {
          _.bidder.id.getOrElse(-1L) == id
        }
      }
    case _ => Failure(new BidException(s"Unable to find winning bids without bidder ID and event ID"))
  }

  def totalByBidder(bidder: Bidder): Try[BigDecimal] =
    winningBids(bidder) flatMap { bidsList =>
      Try((BigDecimal(0.0) /: bidsList) { (sum, bid) => sum + bid.amount })
    }
}
