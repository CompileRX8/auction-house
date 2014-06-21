package actors

import akka.actor.{Props, Actor}
import models.{Bidder, WinningBid, Item}
import play.api.libs.concurrent.Akka
import play.api.Play.current

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

  case class WinningBidsByBidder(bidder: Bidder)
  case class WinningBidsByItem(item: Item)

  private class ItemInfo(val item: Item) {
    private var winningBids: List[WinningBid] = Nil

    def addWinningBid(winningBid: WinningBid) {
      winningBids :+= winningBid
    }

    def getWinningBids = winningBids

    def editWinningBid(winningBidId: Long, bidder: Bidder, amount: BigDecimal): Option[WinningBid] = {
      winningBids find { _.id.get == winningBidId } match {
        case Some(originalWinningBid) =>
          val newWinningBid = WinningBid(originalWinningBid.id, bidder, originalWinningBid.item, amount)
          winningBids = (winningBids filter { _ != originalWinningBid }) :+ newWinningBid
          Some(newWinningBid)
        case None =>
          None
      }
    }

    def deleteWinningBid(winningBidId: Long): Option[WinningBid] = {
      winningBids find { _.id.get == winningBidId } match {
        case s @ Some(originalWinningBid) =>
          winningBids = winningBids filter { _ != originalWinningBid }
          s
        case None =>
          None
      }
    }
  }

  def props = Props(classOf[ItemsActor])

  val itemsActor = Akka.system.actorOf(ItemsActor.props)
}

object ItemsPersistence {
  import ItemsActor._
  import play.api.Play.current
  import play.api.db.DB
  import scala.slick.driver.PostgresDriver.simple._
  import scala.slick.jdbc.JdbcBackend

  class Items(tag: Tag) extends Table[Item](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def itemNumber = column[String]("item_number", O.NotNull)
    def category = column[String]("category", O.NotNull)
    def donor = column[String]("donor", O.NotNull)
    def description = column[String]("description", O.NotNull)
    def minbid = column[BigDecimal]("minbid", O.NotNull)
    def * = (id.?, itemNumber, category, donor, description, minbid) <> ( Item.tupled, Item.unapply )
  }
  val itemsQuery = TableQuery[Items]

  case class WinningBidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
    def bidder(implicit session: JdbcBackend#SessionDef) = BiddersPersistence.biddersQuery.where(_.id === bidderId).first()
    def item(implicit session: JdbcBackend#SessionDef) = itemsQuery.where(_.id === itemId).first()
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

    def bidderFK = foreignKey("bidder_fk", bidderId, BiddersPersistence.biddersQuery)(_.id)
    def itemFK = foreignKey("item_fk", itemId, itemsQuery)(_.id)
  }
  val winningBidsQuery = TableQuery[WinningBids]

  private def db = Database.forDataSource(DB.getDataSource())

  def load: Boolean = {
    db withSession {
      implicit session =>
        itemsQuery.list() map { _.copy() } foreach { itemsActor ! _ }
        winningBidsQuery.list() map { _.copy().toWinningBid } foreach { itemsActor ! _ }
        true
    }
  }

  def create(item: Item): Option[Item] = {
    db withSession {
      implicit session =>
        val newItemId = (itemsQuery returning itemsQuery.map(_.id)) += item
        Some(Item(Some(newItemId), item.itemNumber, item.category, item.donor, item.description, item.minbid))
    }
  }

  def create(winningBid: WinningBid): Option[WinningBid] = {
    db withSession {
      implicit session =>
        val newWinningBidId = (winningBidsQuery returning winningBidsQuery.map(_.id)) += WinningBidRow.fromWinningBid(winningBid)
        Some(WinningBid(Some(newWinningBidId), winningBid.bidder, winningBid.item, winningBid.amount))
    }
  }

  def delete(item: Item): Option[Item] = {
    db withSession {
      implicit session =>
        if(itemsQuery.where(_.id === item.id).delete == 1) {
          Some(item)
        } else {
          None
        }
    }
  }

  def delete(winningBid: WinningBid): Option[WinningBid] = {
    db withSession {
      implicit session =>
        if(winningBidsQuery.where(_.id === winningBid.id).delete == 1) {
          Some(winningBid)
        } else {
          None
        }
    }
  }

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Option[WinningBid] = {
    db withSession {
      implicit session =>
        val row = WinningBidRow(Some(winningBidId), bidder.id.get, item.id.get, amount)
        if(winningBidsQuery.where(_.id === winningBidId).update(row) == 1)
          Some(row.toWinningBid)
        else
          None
    }
  }
}

class ItemsActor extends Actor {
  import ItemsActor._

  private var items: Set[ItemInfo] = Set.empty
  private var winningBids: Map[WinningBid, ItemInfo] = Map.empty

  private def findItemInfo(item: Item): Option[ItemInfo] = {
    items find { _.item == item }
  }

  private def findItemInfo(id: Long): Option[ItemInfo] = {
    items find { _.item.id.get == id }
  }

  private def findItemInfo(itemNumber: String): Option[ItemInfo] = {
    items find { _.item.itemNumber.equals(itemNumber) }
  }

  private def findItemInfoWithWinningBid(winningBidId: Long): Option[ItemInfo] = {
    winningBids.keySet.find(_.id.get == winningBidId).flatMap(winningBids get)
  }

  private def sortedItems = items.map { _.item }.toList.sortBy { _.itemNumber }

  private val itemStringList = (f: ItemInfo => String) => items.map(f).toList.distinct.sorted

  private def createItemInfo(item: Item): ItemInfo = {
    val itemInfo = new ItemInfo(item)
    items += itemInfo
    itemInfo
  }

  private def createWinningBid(itemInfo: ItemInfo, winningBid: WinningBid): WinningBid = {
    itemInfo.addWinningBid(winningBid)
    winningBids += (winningBid -> itemInfo)
    winningBid
  }

  private def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Option[WinningBid] = {
    (winningBids.filterKeys(_.id.get == winningBidId) map { case (winningBid, _) =>
      deleteWinningBid(winningBid) flatMap { _ =>
        findItemInfo(item) flatMap { newItemInfo =>
          val newWinningBid = WinningBid(Some(winningBidId), bidder, item, amount)
          Some(createWinningBid(newItemInfo, newWinningBid))
        }
      }
    }).head
  }

  private def deleteWinningBid(winningBid: WinningBid): Option[WinningBid] = {
    findItemInfoWithWinningBid(winningBid.id.get) flatMap { itemInfo =>
      winningBids -= winningBid
      itemInfo.deleteWinningBid(winningBid.id.get)
    }
  }

  override def receive = {
    case LoadFromDataSource =>
      sender ! ItemsPersistence.load

    case GetItems =>
      sender ! sortedItems

    case GetItem(id) =>
      sender ! (findItemInfo(id) map { _.item })

    case GetItemsByCategory(category) =>
      sender ! sortedItems.filter { _.category.equals(category) }

    case GetCategories =>
      val cats = itemStringList { _.item.category }
      sender ! cats

    case GetDonors =>
      val ds = itemStringList { _.item.donor }
      sender ! ds

    case newItem @ Item(None, itemNumber, category, donor, description, minbid) =>
      findItemInfo(itemNumber) match {
        case Some(_) => sender ! None
        case None =>
          sender ! ItemsPersistence.create(newItem).map { item =>
            createItemInfo(item)
            item
          }
      }

    case item @ Item(idOpt @ Some(id), itemNumber, category, donor, description, minbid) =>
      findItemInfo(itemNumber) match {
        case Some(_) =>
        case None =>
          createItemInfo(item)
      }

    case DeleteItem(id) =>
      findItemInfo(id) match {
        case Some(itemInfo) if itemInfo.getWinningBids.isEmpty =>
          sender ! ItemsPersistence.delete(itemInfo.item).map { item =>
            items = items filter { _.item.id.get != id }
            item
          }
        case _ =>
          sender ! None
      }

    case newWinningBid @ WinningBid(None, bidder, item, amount) =>
      sender ! (findItemInfo(item) flatMap {
        itemInfo =>
          ItemsPersistence.create(newWinningBid).map { winningBid =>
            createWinningBid(itemInfo, winningBid)
            winningBid
          }
      })

    case winningBid @ WinningBid(idOpt @ Some(id), bidder, item, amount) =>
      findItemInfo(item) map { itemInfo =>
        createWinningBid(itemInfo, winningBid)
      }

    case EditWinningBid(id, bidder, item, amount) =>
      sender ! (findItemInfo(item) flatMap { itemInfo =>
        ItemsPersistence.editWinningBid(id, bidder, item, amount) flatMap { winningBid =>
          editWinningBid(id, bidder, item, amount)
        }
      })

    case DeleteWinningBid(id) =>
      sender ! (winningBids.filterKeys(_.id.get == id).headOption flatMap { case (winningBid, _) =>
        ItemsPersistence.delete(winningBid) flatMap { _ =>
          deleteWinningBid(winningBid)
        }
      })

    case WinningBidsByBidder(bidder) =>
      sender ! Option(winningBids.keySet.filter(_.bidder == bidder).toList.sortBy(_.id))

    case WinningBidsByItem(item) =>
      sender ! Option(winningBids.keySet.filter(_.item == item).toList.sortBy(_.id))
  }
}
