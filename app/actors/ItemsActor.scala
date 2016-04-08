package actors

import javax.inject.Inject

import akka.actor.{Actor, Props}
import models._
import persistence.ItemsPersistence
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure

object ItemsActor {

  case object LoadFromDataSource

  case object GetItems

  case class GetItem(id: Long)

  case class GetItemsByCategory(category: String)

  case object GetCategories

  case object GetDonors

  case class DeleteItem(id: Long)

  case class EditItem(item: Item)

  case class EditWinningBid(id: Long, bidder: Bidder, item: Item, amount: BigDecimal)

  case class DeleteWinningBid(id: Long)

  case class GetWinningBid(id: Long)

  case class WinningBidsByBidder(bidder: Bidder)

  case class WinningBidsByItem(item: Item)

  def props = Props[ItemsActor]

  //  val itemsActor = Akka.system.actorOf(ItemsActor.props)
}


class ItemsActor @Inject()(itemsPersistence: ItemsPersistence) extends Actor {

  import ItemsActor._

  private def findItem(item: Item): Future[Option[Item]] = {
    item.id match {
      case Some(id) => itemsPersistence.itemById(id)
      case None => itemsPersistence.itemByItemNumber(item.itemNumber)
    }
  }

  private def findItem(id: Long): Future[Option[Item]] = {
    itemsPersistence.itemById(id)
  }

  private def findItem(itemNumber: String): Future[Option[Item]] = {
    itemsPersistence.itemByItemNumber(itemNumber)
  }

  private def sortedItems = itemsPersistence.sortedItems

  private val itemStringList = (f: Item => String) => sortedItems map { items =>
    items.map(f).distinct.sorted
  }

  override def receive = {
    case LoadFromDataSource =>
      itemsPersistence.load(self) onComplete {
        sender ! _
      }

    case GetItems =>
      sortedItems onComplete {
        sender ! _
      }

    case GetItem(id) =>
      findItem(id) onComplete {
        sender ! _
      }

    case GetItemsByCategory(category) =>
      sortedItems map { items =>
        items filter { item => item.category.equals(category) }
      } onComplete {
        sender ! _
      }

    case GetCategories =>
      val cats = itemStringList {
        _.category
      }
      sender ! cats

    case GetDonors =>
      val ds = itemStringList {
        _.donor
      }
      sender ! ds

    case newItem@Item(None, itemNumber, category, donor, description, minbid, estvalue) =>
      findItem(itemNumber) flatMap {
        case Some(_) => Future.failed(new ItemException(s"Item number $itemNumber already exists"))
        case None => itemsPersistence.create(newItem)
      } onComplete {
        sender ! _
      }

    case item@Item(idOpt@Some(id), itemNumber, category, donor, description, minbid, estvalue) =>
    // Do nothing since not maintaining our own Set[ItemInfo] anymore

    case DeleteItem(id) =>
      findItem(id) flatMap {
        case Some(item) =>
          itemsPersistence.winningBidsByItem(item).flatMap {
            case Nil => itemsPersistence.delete(item)
            case bids => Future.failed(new ItemException(s"Cannot delete item ID $id with ${bids.length} winning bids"))
          }
        case _ =>
          Future.failed(new ItemException(s"Cannot find item ID $id"))
      } onComplete {
        sender ! _
      }

    case EditItem(item@Item(idOpt@Some(id), itemNumber, category, donor, description, minbid, estvalue)) =>
      itemsPersistence.edit(item) onComplete {
        sender ! _
      }

    case EditItem(item@Item(None, _, _, _, _, _, _)) =>
      sender ! Failure(new ItemException(s"Cannot edit item without item ID"))

    case newWinningBid@WinningBid(None, bidder, item, amount) =>
      findItem(item) flatMap {
        case Some(_) => itemsPersistence.create(newWinningBid)
        case None => Future.failed(new WinningBidException(s"Unable to find item to create winning bid"))
      } onComplete {
        sender ! _
      }

    case winningBid@WinningBid(idOpt@Some(id), bidder, item, amount) =>
    // Do nothing since not maintaining our own Map[WinningBid, ItemInfo] anymore

    case GetWinningBid(id) =>
      itemsPersistence.winningBidById(id) onComplete {
        sender ! _
      }

    case EditWinningBid(id, bidder, item, amount) =>
      findItem(item) flatMap {
        case Some(_) => itemsPersistence.editWinningBid(id, bidder, item, amount)
        case None => Future.failed(new WinningBidException(s"Unable to find item to edit winning bid"))
      } onComplete {
        sender ! _
      }

    case DeleteWinningBid(id) =>
      itemsPersistence.winningBidById(id) flatMap {
        case Some(wb) => itemsPersistence.delete(wb)
        case None => Future.failed(new WinningBidException(s"Cannot find winning bid ID $id to delete"))
      } onComplete {
        sender ! _
      }

    case WinningBidsByBidder(bidder) =>
      itemsPersistence.winningBidsByBidder(bidder) onComplete {
        sender ! _
      }

    case WinningBidsByItem(item) =>
      itemsPersistence.winningBidsByItem(item) onComplete {
        sender ! _
      }
  }
}
