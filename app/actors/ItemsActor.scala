package actors

import akka.actor.{Actor, ActorRef, Props}
import misc.Util
import models._
import persistence.slick.ItemsPersistenceSlick
import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

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

  def props = Props(classOf[ItemsActor])

  val itemsActor = Akka.system.actorOf(ItemsActor.props)
  val itemsPersistence = ItemsPersistenceSlick
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

  private def tryOrSendFailure(sender: ActorRef)(f: (ActorRef) => Unit) = {
    try {
      f(sender)
    } catch {
      case e: Exception =>
        sender ! akka.actor.Status.Failure(e)
        throw e
    }
  }

  override def receive = {
    case LoadFromDataSource => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.load(self)
    }

    case GetItems => tryOrSendFailure(sender) { s =>
      s ! sortedItems
    }

    case GetItem(id) => tryOrSendFailure(sender) { s =>
      s ! findItem(id)
    }

    case GetItemsByCategory(category) => tryOrSendFailure(sender) { s =>
      s ! sortedItems.flatMap { items =>
        Success(items filter { item => item.category.equals(category) })
      }
    }

    case GetCategories => tryOrSendFailure(sender) { s =>
      val cats = itemStringList { _.category }
      s ! cats
    }

    case GetDonors => tryOrSendFailure(sender) { s =>
      val ds = itemStringList { _.donor }
      s ! ds
    }

    case newItem @ Item(None, itemNumber, category, donor, description, minbid, estvalue) => tryOrSendFailure(sender) { s =>
      s ! findItem(itemNumber).flatMap {
        case Some(_) => Failure(new ItemException(s"Item number $itemNumber already exists"))
        case None => itemsPersistence.create(newItem)
      }
    }

    case item @ Item(idOpt @ Some(id), itemNumber, category, donor, description, minbid, estvalue) =>
      // Do nothing since not maintaining our own Set[ItemInfo] anymore

    case DeleteItem(id) => tryOrSendFailure(sender) { s =>
      s ! findItem(id).flatMap {
        case Some(item) =>
          itemsPersistence.winningBidsByItem(item).flatMap {
            case Nil => itemsPersistence.delete(item)
            case bids => Failure(new ItemException(s"Cannot delete item ID $id with ${bids.length} winning bids"))
          }
        case _ =>
          Failure(new ItemException(s"Cannot find item ID $id"))
      }
    }

    case EditItem(item @ Item(idOpt @ Some(id), itemNumber, category, donor, description, minbid, estvalue)) => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.edit(item)
    }

    case EditItem(item @ Item(None, _, _, _, _, _, _)) =>
      sender ! Failure(new ItemException(s"Cannot edit item without item ID"))

    case newWinningBid @ WinningBid(None, bidder, item, amount) => tryOrSendFailure(sender) { s =>
      s ! findItem(item).flatMap {
        case Some(_) => itemsPersistence.create(newWinningBid)
        case None => Failure(new WinningBidException(s"Unable to find item to create winning bid"))
      }
    }

    case winningBid @ WinningBid(idOpt @ Some(id), bidder, item, amount) =>
      // Do nothing since not maintaining our own Map[WinningBid, ItemInfo] anymore

    case GetWinningBid(id) => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.winningBidById(id)
    }

    case EditWinningBid(id, bidder, item, amount) => tryOrSendFailure(sender) { s =>
      s ! findItem(item).flatMap {
        case Some(_) => itemsPersistence.editWinningBid(id, bidder, item, amount)
        case None => Failure(new WinningBidException(s"Unable to find item to edit winning bid"))
      }
    }

    case DeleteWinningBid(id) => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.winningBidById(id).flatMap {
        case Some(wb) => itemsPersistence.delete(wb)
        case None => Failure(new WinningBidException(s"Cannot find winning bid ID $id to delete"))
      }
    }

    case WinningBidsByBidder(bidder) => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.winningBidsByBidder(bidder)
    }

    case WinningBidsByItem(item) => tryOrSendFailure(sender) { s =>
      s ! itemsPersistence.winningBidsByItem(item)
    }
  }
}
