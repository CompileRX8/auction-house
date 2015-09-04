package actors

import akka.actor.{Props, Actor}
import misc.Util
import models._
import persistence.anorm.ItemsPersistenceAnorm
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.language.postfixOps
import scala.util.{Success, Try, Failure}

object ItemsActor {
  case object LoadFromDataSource

  case object GetItems
  case class GetItem(id: Long)
  case class DeleteItem(id: Long)
  case class EditItem(item: Item)

  case class EditBid(id: Long, bidder: Bidder, item: Item, amount: BigDecimal)
  case class DeleteBid(id: Long)

  case class GetBid(id: Long)
  case class BidsByBidder(bidder: Bidder)
  case class BidsByItem(item: Item)

  case class ItemsForEvent(eventId: Long)

  def props = Props(classOf[ItemsActor])

  val itemsActor = Akka.system.actorOf(ItemsActor.props)
  val itemsPersistence = ItemsPersistenceAnorm
}



class ItemsActor extends Actor {
  import ItemsActor._

  private def findItem(item: Item): Try[Option[Item]] = {
    item.id match {
      case Some(id) => itemsPersistence.itemById(id)
      case None => itemsPersistence.itemByItemNumber(item.itemNumber)
    }
  }

  private def findItem(id: Long): Try[Option[Item]] = {
    itemsPersistence.itemById(id)
  }

  private def findItem(itemNumber: String): Try[Option[Item]] = {
    itemsPersistence.itemByItemNumber(itemNumber)
  }

  private def sortedItems = itemsPersistence.sortedItems

  private val itemStringList = (f: Item => String) => sortedItems flatMap { items =>
    Success(items.map(f).toList.distinct.sorted)
  }

  override def receive = {
    case LoadFromDataSource =>
      sender ! itemsPersistence.load(self)

    case GetItems =>
      sender ! sortedItems

    case GetItem(id) =>
      sender ! findItem(id)

    case newItem @ Item(None, event, itemNumber, description, minbid) =>
      sender ! findItem(itemNumber).flatMap {
        case Some(_) => Failure(new ItemException(s"Item number $itemNumber already exists"))
        case None => itemsPersistence.create(newItem)
      }

    case item @ Item(idOpt @ Some(id), event, itemNumber, description, minbid) =>
      // Do nothing since not maintaining our own Set[ItemInfo] anymore

    case DeleteItem(id) =>
      sender ! findItem(id).flatMap {
        case Some(item) =>
          itemsPersistence.winningBidsByItem(item).flatMap {
            case Nil => itemsPersistence.delete(item)
            case bids => Failure(new ItemException(s"Unable to delete item ID $id with ${bids.length} winning bids"))
          }
        case _ =>
          Failure(new ItemException(s"Unable to find item ID $id to delete"))
      }

    case EditItem(item @ Item(idOpt @ Some(id), itemNumber, category, donor, description, minbid, estvalue)) =>
      sender ! itemsPersistence.edit(item)

    case EditItem(item @ Item(None, _, _, _, _, _, _)) =>
      sender ! Failure(new ItemException(s"Unable to edit item without item ID"))

    case newWinningBid @ Bid(None, bidder, item, amount) =>
      sender ! findItem(item).flatMap {
        case Some(_) => itemsPersistence.create(newWinningBid)
        case None => Failure(new BidException(s"Unable to find item to create winning bid"))
      }

    case winningBid @ Bid(idOpt @ Some(id), bidder, item, amount) =>
      // Do nothing since not maintaining our own Map[WinningBid, ItemInfo] anymore

    case GetBid(id) =>
      sender ! itemsPersistence.winningBidById(id)

    case EditBid(id, bidder, item, amount) =>
      sender ! findItem(item).flatMap {
        case Some(_) => itemsPersistence.editWinningBid(id, bidder, item, amount)
        case None => Failure(new BidException(s"Unable to find item to edit winning bid"))
      }

    case DeleteBid(id) =>
      sender ! itemsPersistence.winningBidById(id).flatMap {
        case Some(wb) => itemsPersistence.delete(wb)
        case None => Failure(new BidException(s"Unable to find winning bid ID $id to delete"))
      }

    case BidsByBidder(bidder) =>
      sender ! itemsPersistence.winningBidsByBidder(bidder)

    case BidsByItem(item) =>
      sender ! itemsPersistence.winningBidsByItem(item)
  }
}
