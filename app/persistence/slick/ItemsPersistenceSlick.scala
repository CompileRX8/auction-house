package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import models.{Bidder, Item, ItemException, WinningBid}
import persistence.ItemsPersistence
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class ItemsPersistenceSlick @Inject()(dbConfigProvider: DatabaseConfigProvider, val biddersPersistenceSlick: BiddersPersistenceSlick, implicit val ec: ExecutionContext) extends SlickPersistence with ItemsPersistence {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  val db = dbConfig.db

  class Items(tag: Tag) extends Table[Item](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def itemNumber = column[String]("item_number")
    def category = column[String]("category")
    def donor = column[String]("donor")
    def description = column[String]("description")
    def minbid = column[PGMoney]("minbid")
    def estvalue = column[PGMoney]("estvalue")
    def * = (id.?, itemNumber, category, donor, description, minbid, estvalue) <> ( Item.tupled, Item.unapply )

    def itemNumberIdx = index("item_item_number_idx", itemNumber, unique = true)
  }
  val itemsQuery = TableQuery[Items]

  case class WinningBidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: PGMoney)
  object WinningBidRow extends ((Option[Long], Long, Long, PGMoney) => WinningBidRow)

  class WinningBids(tag: Tag) extends Table[WinningBidRow](tag, "winningbid") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def itemId = column[Long]("item_id")
    def amount = column[PGMoney]("amount")
    def * = (id.?, bidderId, itemId, amount) <> ( WinningBidRow.tupled, WinningBidRow.unapply )

    def bidderFK = foreignKey("winningbid_bidder_id_fk", bidderId, biddersPersistenceSlick.biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemFK = foreignKey("winningbid_item_id_fk", itemId, itemsQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemIdbidderIdIdx = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId), unique = true)
    def bidderIditemIdIdx = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId), unique = true)
  }
  val winningBidsQuery = TableQuery[WinningBids]

  override def load(itemsActor: ActorRef): Future[Boolean] = {
    val items = for(i <- itemsQuery) yield i
    val winningBids = for(wb <- winningBidsQuery) yield wb
    val itemsUpdate = db.run(items.to[List].result).collect {
      case i =>
        itemsActor ! i
        true
    }
    val winningBidsUpdate = db.run(winningBids.to[List].result).collect {
      case wb =>
        itemsActor ! wb
        true
    }
    itemsUpdate.zip(winningBidsUpdate).map {
      case (i, wb) => i && wb
      case _ => false
    }
  }

  override def create(item: Item): Future[Item] = {
    db.run {
      val newItemIdOp = (itemsQuery returning itemsQuery.map(_.id)) += item
      newItemIdOp.map(iId => Item(Some(iId), item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue))
    }
  }

  override def create(winningBid: WinningBid): Future[WinningBid] = {
    val createTuple = WinningBidRow(None, winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount)
    for {
      bidder <- db.run(
        biddersPersistenceSlick.biddersQuery.filter(_.id === winningBid.bidder.id.get).result.head
      )
      item <- db.run(
        itemsQuery.filter(_.id === winningBid.item.id.get).result.head
      )
      newWinningBid <- db.run(
        winningBidsQuery returning winningBidsQuery.map {
          _.id
        } += createTuple
      ).map(id => WinningBid(Some(id), bidder, item, winningBid.amount))
    } yield newWinningBid
  }

  override def delete(item: Item): Future[Item] = {
    db.run {
      itemsQuery.filter(_.id === item.id.get).delete map {
        case 1 => item
        case _ => throw ItemException(s"Unable to delete item with unique ID ${item.id.get}")
      }
    }
  }

  override def delete(winningBid: WinningBid): Future[WinningBid] = {
    db.run {
      winningBidsQuery.filter(_.id === winningBid.id.get).delete map {
        case 1 => winningBid
        case _ => throw ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id.get}")
      }
    }
  }

  override def edit(item: Item): Future[Item] = {
    val itemUpdate = (item.itemNumber, item.category, item.donor, item.description, item.minbid.toDouble, item.estvalue.toDouble)
    db.run {
      itemsQuery.filter(_.id === item.id.get).map( i =>
        (i.itemNumber, i.category, i.donor, i.description, i.minbid, i.estvalue)
      ).update(itemUpdate).map {
        case 1 => item
        case _ => throw ItemException(s"Unable to update item with unique ID ${item.id.get}")
      }
    }
  }

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: PGMoney): Future[WinningBid] = {
    val winningBidUpdate = (bidder.id.get, item.id.get, amount.toDouble)
    db.run {
      winningBidsQuery.filter(_.id === winningBidId).map( wb =>
        (wb.bidderId, wb.itemId, wb.amount)
      ).update(winningBidUpdate).map {
        case 1 => WinningBid(Some(winningBidId), bidder, item, amount)
        case _ => throw ItemException(s"Unable to edit winning bid with unique ID $winningBidId")
      }
    }
  }

  override def winningBidById(id: Long): Future[Option[WinningBid]] = {
    val winningBidOpt = for {
      wb <- winningBidsQuery if wb.id === id
      b <- biddersPersistenceSlick.biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(winningBidOpt.result.headOption)
  }

  override def winningBidsByItem(item: Item): Future[List[WinningBid]] = {
    val q = for {
      wb <- winningBidsQuery if wb.itemId === item.id.get
      b <- biddersPersistenceSlick.biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(q.to[List].result)
  }

  override def winningBidsByBidder(bidder: Bidder): Future[List[WinningBid]] = {
    val q = for {
      wb <- winningBidsQuery if wb.bidderId === bidder.id.get
      b <- biddersPersistenceSlick.biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(q.to[List].result)
  }

  override def sortedItems: Future[List[Item]] = {
    val items = for(i <- itemsQuery) yield i
    db.run(items.sortBy(_.itemNumber).to[List].result)
  }

  override def itemById(id: Long): Future[Option[Item]] = {
    val itemOpt = for(
      i <- itemsQuery if i.id === id
    ) yield i
    db.run(itemOpt.result.headOption)
  }

  override def itemByItemNumber(itemNumber: String): Future[Option[Item]] = {
    val itemOpt = for(
      i <- itemsQuery if i.itemNumber === itemNumber
    ) yield i
    db.run(itemOpt.result.headOption)
  }

}
