package actors

import akka.actor.{Props, Actor}
import misc.Util
import models._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.language.postfixOps
import scala.util.{Success, Try, Failure}

object ItemsActor {
  case object LoadFromDataSource

  case object GetItems
  case class GetItem(id: Long)
  case class GetItemsByCategory(category: String)
  case object GetCategories
  case object GetDonors
  case class DeleteItem(id: Long)

  case class EditWinningBid(id: Long, bidder: Bidder, item: Item, amount: BigDecimal)
  case class DeleteWinningBid(id: Long)

  case class GetWinningBid(id: Long)
  case class WinningBidsByBidder(bidder: Bidder)
  case class WinningBidsByItem(item: Item)

  def props = Props(classOf[ItemsActor])

  val itemsActor = Akka.system.actorOf(ItemsActor.props)
}

object ItemsPersistence {
  import ItemsActor._
  import play.api.db.slick.Config.driver.simple._
  import scala.slick.jdbc.JdbcBackend
  import java.sql.SQLException

  class Items(tag: Tag) extends Table[Item](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def itemNumber = column[String]("item_number", O.NotNull)
    def category = column[String]("category", O.NotNull)
    def donor = column[String]("donor", O.NotNull)
    def description = column[String]("description", O.NotNull)
    def minbid = column[BigDecimal]("minbid", O.NotNull)
    def * = (id.?, itemNumber, category, donor, description, minbid) <> ( Item.tupled, Item.unapply )

    def itemNumberIdx = index("item_item_number_idx", itemNumber, unique = true)
  }
  val itemsQuery = TableQuery[Items]

  case class WinningBidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
    def bidder(implicit session: JdbcBackend#SessionDef) = BiddersPersistence.biddersQuery.filter(_.id === bidderId).first
    def item(implicit session: JdbcBackend#SessionDef) = itemsQuery.filter(_.id === itemId).first
    def toWinningBid(implicit session: JdbcBackend#SessionDef): WinningBid = WinningBid(id, bidder, item, amount)
  }
  object WinningBidRow extends ((Option[Long], Long, Long, BigDecimal) => WinningBidRow) {
    def fromWinningBid(winningBid: WinningBid): WinningBidRow =
      WinningBidRow(winningBid.id, winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount)
  }

  class WinningBids(tag: Tag) extends Table[WinningBidRow](tag, "winningbid") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id", O.NotNull)
    def itemId = column[Long]("item_id", O.NotNull)
    def amount = column[BigDecimal]("amount", O.NotNull)
    def * = (id.?, bidderId, itemId, amount) <> ( WinningBidRow.tupled, WinningBidRow.unapply )

    def bidderFK = foreignKey("winningbid_bidder_id_fk", bidderId, BiddersPersistence.biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemFK = foreignKey("winningbid_item_id_fk", itemId, itemsQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemIdbidderIdIdx = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId), unique = true)
    def bidderIditemIdIdx = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId), unique = true)
  }
  val winningBidsQuery = TableQuery[WinningBids]

  def load: Boolean = {
    Util.db withSession {
      implicit session =>
        try {
          itemsQuery.list map { _.copy() } foreach { itemsActor ! _ }
          winningBidsQuery.list map { _.copy().toWinningBid } foreach { itemsActor ! _ }
          true
        } catch {
          case _: SQLException =>
            (itemsQuery.ddl ++ winningBidsQuery.ddl).create
            true
        }
    }
  }

  def create(item: Item): Try[Item] = {
    Util.db withSession {
      implicit session =>
        Try {
          val newItemId = (itemsQuery returning itemsQuery.map(_.id)) += item
          Item(Some(newItemId), item.itemNumber, item.category, item.donor, item.description, item.minbid)
        }
    }
  }

  def create(winningBid: WinningBid): Try[WinningBid] = {
    Util.db withSession {
      implicit session =>
        Try {
          val newWinningBidId = (winningBidsQuery returning winningBidsQuery.map(_.id)) += WinningBidRow.fromWinningBid(winningBid)
          WinningBid(Some(newWinningBidId), winningBid.bidder, winningBid.item, winningBid.amount)
        }
    }
  }

  def delete(item: Item): Try[Item] = {
    Util.db withSession {
      implicit session =>
        if(itemsQuery.filter(_.id === item.id).delete == 1) {
          Success(item)
        } else {
          Failure(new ItemException(s"Unable to delete item with unique ID ${item.id}"))
        }
    }
  }

  def delete(winningBid: WinningBid): Try[WinningBid] = {
    Util.db withSession {
      implicit session =>
        if(winningBidsQuery.filter(_.id === winningBid.id).delete == 1) {
          Success(winningBid)
        } else {
          Failure(new ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id}"))
        }
    }
  }

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[WinningBid] = {
    Util.db withSession {
      implicit session =>
        val row = WinningBidRow(Some(winningBidId), bidder.id.get, item.id.get, amount)
        if(winningBidsQuery.filter(_.id === winningBidId).update(row) == 1)
          Success(row.toWinningBid)
        else
          Failure(new ItemException(s"Unable to edit winning bid with unique ID $winningBidId"))
    }
  }

  def winningBidById(id: Long): Try[Option[WinningBid]] = {
    Util.db withSession {
      implicit session =>
        Try(winningBidsQuery.filter(_.id === id).list.headOption.map { row => row.toWinningBid })
    }
  }

  def winningBidsByItem(item: Item): Try[List[WinningBid]] = {
    Util.db withSession {
      implicit session =>
        Try(winningBidsQuery.filter(_.itemId === item.id.get).list.map { row => row.toWinningBid })
    }
  }

  def winningBidsByBidder(bidder: Bidder): Try[List[WinningBid]] = {
    Util.db withSession {
      implicit session =>
        Try(winningBidsQuery.filter(_.bidderId === bidder.id.get).list.map { row => row.toWinningBid })
    }
  }

  def sortedItems: Try[List[Item]] = {
    Util.db withSession {
      implicit session =>
        Try(itemsQuery.sortBy(_.itemNumber).list.map { row => row.copy() } )
    }
  }

  def itemById(id: Long): Try[Option[Item]] = {
    Util.db withSession {
      implicit session =>
        Try(itemsQuery.filter(_.id === id).list.headOption.map { row => row.copy() })
    }
  }

  def itemByItemNumber(itemNumber: String): Try[Option[Item]] = {
    Util.db withSession {
      implicit session =>
        Try(itemsQuery.filter(_.itemNumber === itemNumber).list.headOption.map { row => row.copy() })
    }
  }
}

class ItemsActor extends Actor {
  import ItemsActor._

  private def findItem(item: Item): Try[Option[Item]] = {
    item.id match {
      case Some(id) => ItemsPersistence.itemById(id)
      case None => ItemsPersistence.itemByItemNumber(item.itemNumber)
    }
  }

  private def findItem(id: Long): Try[Option[Item]] = {
    ItemsPersistence.itemById(id)
  }

  private def findItem(itemNumber: String): Try[Option[Item]] = {
    ItemsPersistence.itemByItemNumber(itemNumber)
  }

  private def sortedItems = ItemsPersistence.sortedItems

  private val itemStringList = (f: Item => String) => sortedItems flatMap { items =>
    Success(items.map(f).toList.distinct.sorted)
  }

  override def receive = {
    case LoadFromDataSource =>
      sender ! ItemsPersistence.load

    case GetItems =>
      sender ! sortedItems

    case GetItem(id) =>
      sender ! findItem(id)

    case GetItemsByCategory(category) =>
      sender ! sortedItems.flatMap { items =>
        Success(items filter { item => item.category.equals(category) })
      }

    case GetCategories =>
      val cats = itemStringList { _.category }
      sender ! cats

    case GetDonors =>
      val ds = itemStringList { _.donor }
      sender ! ds

    case newItem @ Item(None, itemNumber, category, donor, description, minbid) =>
      sender ! findItem(itemNumber).flatMap {
        case Some(_) => Failure(new ItemException(s"Item number $itemNumber already exists"))
        case None => ItemsPersistence.create(newItem)
      }

    case item @ Item(idOpt @ Some(id), itemNumber, category, donor, description, minbid) =>
      // Do nothing since not maintaining our own Set[ItemInfo] anymore

    case DeleteItem(id) =>
      sender ! findItem(id).flatMap {
        case Some(item) =>
          ItemsPersistence.winningBidsByItem(item).flatMap {
            case Nil => ItemsPersistence.delete(item)
            case bids => Failure(new ItemException(s"Cannot delete item ID $id with ${bids.length} winning bids"))
          }
        case _ =>
          Failure(new ItemException(s"Cannot find item ID $id"))
      }

    case newWinningBid @ WinningBid(None, bidder, item, amount) =>
      sender ! findItem(item).flatMap {
        case Some(_) => ItemsPersistence.create(newWinningBid)
        case None => Failure(new WinningBidException(s"Unable to find item to create winning bid"))
      }

    case winningBid @ WinningBid(idOpt @ Some(id), bidder, item, amount) =>
      // Do nothing since not maintaining our own Map[WinningBid, ItemInfo] anymore

    case GetWinningBid(id) =>
      sender ! ItemsPersistence.winningBidById(id)

    case EditWinningBid(id, bidder, item, amount) =>
      sender ! findItem(item).flatMap {
        case Some(_) => ItemsPersistence.editWinningBid(id, bidder, item, amount)
        case None => Failure(new WinningBidException(s"Unable to find item to edit winning bid"))
      }

    case DeleteWinningBid(id) =>
      sender ! ItemsPersistence.winningBidById(id).flatMap {
        case Some(wb) => ItemsPersistence.delete(wb)
        case None => Failure(new WinningBidException(s"Cannot find winning bid ID $id to delete"))
      }

    case WinningBidsByBidder(bidder) =>
      sender ! ItemsPersistence.winningBidsByBidder(bidder)

    case WinningBidsByItem(item) =>
      sender ! ItemsPersistence.winningBidsByItem(item)
  }
}
