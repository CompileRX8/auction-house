package persistence.slick

import javax.inject.{Inject, Singleton}

import akka.actor.ActorRef
import models._
import persistence.ItemsPersistence
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ItemsPersistenceSlick @Inject()(dbConfigProvider: DatabaseConfigProvider, val biddersPersistenceSlick: BiddersPersistenceSlick)(implicit val ec: ExecutionContext) extends SlickPersistence with ItemsPersistence {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  val db = dbConfig.db

  case class ItemRow(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal) {
    def toItem: Future[Item] = Future.successful(Item(id, itemNumber, category, donor, description, minbid, estvalue))
  }

  object ItemRow extends ((Option[Long], String, String, String, String, BigDecimal, BigDecimal) => ItemRow) {
    def fromItem(item: Item): Future[ItemRow] = Future.successful(ItemRow(item.id, item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue))

    def toItem(row: ItemRow): Future[Item] = row.toItem
  }

  class Items(tag: Tag) extends Table[ItemRow](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def itemNumber = column[String]("item_number")
    def category = column[String]("category")
    def donor = column[String]("donor")
    def description = column[String]("description")
    def minbid = column[BigDecimal]("minbid")
    def estvalue = column[BigDecimal]("estvalue")
    def * = (id.?, itemNumber, category, donor, description, minbid, estvalue) <> ( ItemRow.tupled, ItemRow.unapply )

    def itemNumberIdx = index("item_item_number_idx", itemNumber, unique = true)
  }
  val itemsQuery = TableQuery[Items]

  case class WinningBidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
    def toWinningBid: Future[WinningBid] = biddersPersistenceSlick.bidderById(bidderId).zip(itemById(itemId)) map {
      case (Some(bidder), Some(item)) => WinningBid(id, bidder, item, amount)
      case (Some(_), None) => throw ItemException(s"Unable to create winning bid with invalid item id $itemId")
      case (None, Some(_)) => throw BidderException(s"Unable to create winning bid with invalid bidder id $bidderId")
      case _ => throw ItemException(s"Unable to create winning bid with invalid item id $itemId and bidder id $bidderId")
    }

    def toWinningBid(bidder: Bidder, item: Item): Future[WinningBid] = Future.successful(WinningBid(id, bidder, item, amount))

    def toWinningBid(bidder: Bidder): Future[WinningBid] = itemById(itemId) flatMap {
      case Some(item) => toWinningBid(bidder, item)
      case None => throw ItemException(s"Unable to create winning bid with invalid item id $itemId")
    }

    def toWinningBid(item: Item): Future[WinningBid] = biddersPersistenceSlick.bidderById(bidderId) flatMap {
      case Some(bidder) => toWinningBid(bidder, item)
      case None => throw BidderException(s"Unable to create winning bid with invalid bidder id $bidderId")
    }
  }
  object WinningBidRow extends ((Option[Long], Long, Long, BigDecimal) => WinningBidRow) {
    def fromWinningBid(winningBid: WinningBid): Future[WinningBidRow] = Future.successful(WinningBidRow(winningBid.id, winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount))

    def toWinningBid(row: WinningBidRow) = row.toWinningBid

    def toWinningBid(rowTuple: (WinningBidRow, biddersPersistenceSlick.BidderRow, ItemRow)): Future[WinningBid] = rowTuple._2.toBidder flatMap { bidder =>
      rowTuple._3.toItem flatMap { item =>
        rowTuple._1.toWinningBid(bidder, item)
      }
    }

    def toWinningBid(item: Item)(rowTuple: (WinningBidRow, biddersPersistenceSlick.BidderRow)): Future[WinningBid] = rowTuple._2.toBidder flatMap { bidder =>
      rowTuple._1.toWinningBid(bidder, item)
    }

    def toWinningBid(bidder: Bidder)(rowTuple: (WinningBidRow, ItemRow)): Future[WinningBid] = rowTuple._2.toItem flatMap { item =>
      rowTuple._1.toWinningBid(bidder, item)
    }
  }

  class WinningBids(tag: Tag) extends Table[WinningBidRow](tag, "winningbid") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def itemId = column[Long]("item_id")
    def amount = column[BigDecimal]("amount")
    def * = (id.?, bidderId, itemId, amount) <> ( WinningBidRow.tupled, WinningBidRow.unapply )

    def bidderFK = foreignKey("winningbid_bidder_id_fk", bidderId, biddersPersistenceSlick.biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemFK = foreignKey("winningbid_item_id_fk", itemId, itemsQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemIdbidderIdIdx = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId), unique = true)
    def bidderIditemIdIdx = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId), unique = true)
  }
  val winningBidsQuery = TableQuery[WinningBids]

  override def load(itemsActor: ActorRef): Future[Boolean] = {
/*    val items = for(i <- itemsQuery) yield i
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
    } */
    Future.successful(true)
  }

  override def create(item: Item): Future[Item] = {
    ItemRow.fromItem(item) flatMap { itemRow =>
      for {
        newItem <- db.run(
          itemsQuery returning itemsQuery.map {
            _.id
          } += itemRow
        ).map(id => item.copy(id = Some(id)))
      } yield newItem
    }
  }

  override def create(winningBid: WinningBid): Future[WinningBid] = {
    WinningBidRow.fromWinningBid(winningBid) flatMap { winningBidRow =>
      for {
        newWinningBid <- db.run(
          winningBidsQuery returning winningBidsQuery.map {
            _.id
          } += winningBidRow
        ).map(id => winningBid.copy(id = Some(id)))
      } yield newWinningBid
    }
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
    val itemUpdate = (item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue)
    db.run {
      itemsQuery.filter(_.id === item.id.get).map( i =>
        (i.itemNumber, i.category, i.donor, i.description, i.minbid, i.estvalue)
      ).update(itemUpdate).map {
        case 1 => item
        case _ => throw ItemException(s"Unable to update item with unique ID ${item.id.get}")
      }
    }
  }

  override def edit(winningBid: WinningBid): Future[WinningBid] = {
    val winningBidUpdate = (winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount)
    db.run {
      winningBidsQuery.filter(_.id === winningBid.id.get).map( wb =>
        (wb.bidderId, wb.itemId, wb.amount)
      ).update(winningBidUpdate).map {
        case 1 => winningBid
        case _ => throw ItemException(s"Unable to edit winning bid with unique ID ${winningBid.id.get}")
      }
    }
  }
  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] = ???

  override def winningBidById(id: Long): Future[Option[WinningBid]] = {
    val q = for {
      wbRow <- winningBidsQuery if wbRow.id === id
      bRow <- biddersPersistenceSlick.biddersQuery if bRow.id === wbRow.bidderId
      iRow <- itemsQuery if iRow.id === wbRow.itemId
    } yield (wbRow, bRow, iRow)
    db.run(q.result.map(mapSeq(WinningBidRow.toWinningBid))).flatMap(Future.sequence(_)).map(_.headOption)
  }
//    db.run(winningBidsQuery.filter(_.id === id).result.map(mapSeq(_.toWinningBid))).flatMap(Future.sequence(_)).map(_.headOption)

  override def winningBidsByItem(item: Item): Future[List[WinningBid]] = {
    val q = for {
      wbRow <- winningBidsQuery if wbRow.itemId === item.id.get
      bRow <- biddersPersistenceSlick.biddersQuery if bRow.id === wbRow.bidderId
    } yield (wbRow, bRow)
    db.run(q.result.map(mapSeq(WinningBidRow.toWinningBid(item)))).flatMap(Future.sequence(_))
  }
//    db.run(winningBidsQuery.filter(_.itemId === item.id.get).result.map(mapSeq(_.toWinningBid))).flatMap(Future.sequence(_))

  override def winningBidsByBidder(bidder: Bidder): Future[List[WinningBid]] = {
    val q = for {
      wbRow <- winningBidsQuery if wbRow.bidderId === bidder.id.get
      iRow <- itemsQuery if iRow.id === wbRow.itemId
    } yield (wbRow, iRow)
    db.run(q.result.map(mapSeq(WinningBidRow.toWinningBid(bidder)))).flatMap(Future.sequence(_))
  }
//    db.run(winningBidsQuery.filter(_.bidderId === bidder.id.get).result.map(mapSeq(_.toWinningBid))).flatMap(Future.sequence(_))

  override def sortedItems: Future[List[Item]] =
    db.run(itemsQuery.sortBy(_.itemNumber).result.map(mapSeq(_.toItem))).flatMap(Future.sequence(_))

  override def itemById(id: Long): Future[Option[Item]] =
    db.run(itemsQuery.filter(_.id === id).result.map(mapSeq(_.toItem))).flatMap(Future.sequence(_)).map(_.headOption)

  override def itemByItemNumber(itemNumber: String): Future[Option[Item]] =
    db.run(itemsQuery.filter(_.itemNumber === itemNumber).result.map(mapSeq(_.toItem))).flatMap(Future.sequence(_)).map(_.headOption)

}
