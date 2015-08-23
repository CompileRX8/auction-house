package persistence.anorm

import anorm._
import anorm.SqlParser._
import akka.actor.ActorRef
import models.{ItemException, Bidder, Item, WinningBid}
import persistence.ItemsPersistence
import play.api.Play.current

import scala.util.Try

object ItemsPersistenceAnorm extends AnormPersistence with ItemsPersistence {
  val itemSQL =
    """
      |select "id", "item_number", "category", "donor", "description", "minbid", "estvalue"
      |from "item"
    """.stripMargin


  val itemMapper = {
    get[Long]("id") ~
      get[String]("item_number") ~
      get[String]("category") ~
      get[String]("donor") ~
      get[String]("description") ~
      get[BigDecimal]("minbid") ~
      get[BigDecimal]("estvalue") map {
      case id ~ itemNumber ~ category ~ donor ~ description ~ minbid ~ estvalue =>
        Item(Some(id), itemNumber, category, donor, description, minbid, estvalue)
    }
  }

  val winningBidSQL =
    """
      |select wb."id", wb."bidder_id", wb."item_id", wb."amount", b."name",
      |i."item_number", i."category", i."donor", i."description", i."minbid",
      |i."estvalue"
      |from "winningbid" wb
      |inner join "bidder" b on wb."bidder_id" = b."id"
      |inner join "item" i on wb."item_id" = i."id"
    """.stripMargin

  val winningBidMapper = {
    get[Long]("id") ~
      get[Long]("bidder_id") ~
      get[Long]("item_id") ~
      get[BigDecimal]("amount") ~
      get[String]("name") ~
      get[String]("item_number") ~
      get[String]("category") ~
      get[String]("donor") ~
      get[String]("description") ~
      get[BigDecimal]("minbid") ~
      get[BigDecimal]("estvalue") map {
      case id ~ bidderId ~ itemId ~ amount ~
        name ~
        itemNumber ~ category ~ donor ~ description ~ minbid ~ estvalue =>
        WinningBid(
          Some(id),
          Bidder(Some(bidderId), name),
          Item(Some(itemId), itemNumber, category, donor, description, minbid, estvalue),
          amount
        )
    }
  }

  override def load(itemsActor: ActorRef): Try[Boolean] = Try {
    db.withConnection {
      implicit c =>
        SQL(itemSQL).as(itemMapper *).foreach { item =>
          itemsActor ! item
        }

        SQL(winningBidSQL).as(winningBidMapper *).foreach { winningBid =>
          itemsActor ! winningBid
        }

        true
    }
  }

  val itemByIdSQL = SQL(itemSQL + """ where "id" = {id} """.stripMargin)

  override def itemById(id: Long): Try[Option[Item]] = Try {
    db.withConnection {
      implicit c =>
        itemByIdSQL.on('id -> id).as(itemMapper.singleOpt)
    }
  }

  val winningBidByIdSQL = SQL(winningBidSQL + """ where wb."id" = {id} """.stripMargin)

  override def winningBidById(id: Long): Try[Option[WinningBid]] = Try {
    db.withConnection {
      implicit c =>
        winningBidByIdSQL.on('id -> id).as(winningBidMapper.singleOpt)
    }
  }

  val winningBidsByBidderSQL = SQL(winningBidSQL + """ where wb."bidder_id" = {bidder_id} """.stripMargin)

  override def winningBidsByBidder(bidder: Bidder): Try[List[WinningBid]] = Try {
    db.withConnection {
      implicit c =>
        winningBidsByBidderSQL.on('bidder_id -> bidder.id.get).as(winningBidMapper *)
    }
  }

  val winningBidEditSQL = SQL(
    """
      |update "winningbid"
      |set "bidder_id" = {bidder_id},
      |"item_id" = {item_id},
      |"amount" = {amount}
      |where "id" = {id}
    """.stripMargin
  )

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[WinningBid] = Try {
    db.withConnection {
      implicit c =>
        winningBidEditSQL.on(
          'id -> winningBidId,
          'bidder_id -> bidder.id.get,
          'item_id -> item.id.get,
          'amount -> amount
        ).executeUpdate() match {
          case 1 =>
            val wb = winningBidById(winningBidId)
            wb match {
              case scala.util.Success(Some(w)) => w
              case _ => throw new ItemException(s"Unable to edit winning bid with unique ID $winningBidId")
            }
          case _ => throw new ItemException(s"Unable to edit winning bid with unique ID $winningBidId")
        }
    }
  }

  val itemEditSQL = SQL(
    """
      |update "item"
      |set "item_number" = {item_number},
      |"category" = {category},
      |"donor" = {donor},
      |"description" = {description},
      |"minbid" = {minbid},
      |"estvalue" = {estvalue}
      |where "id" = {id}
    """.stripMargin
  )

  override def edit(item: Item): Try[Item] = Try {
    db.withConnection {
      implicit c =>
        itemEditSQL.on(
          'id -> item.id.get,
          'item_number -> item.itemNumber,
          'category -> item.category,
          'donor -> item.donor,
          'description -> item.description,
          'minbid -> item.minbid,
          'estvalue -> item.estvalue
        ).executeUpdate() match {
          case 1 =>
            item
          case _ => throw new ItemException(s"Unable to update item with unique ID ${item.id.get}")
        }
    }
  }

  val deleteItemSQL = SQL(
    """
      |delete "item"
      |where "id" = {id}
    """.stripMargin
  )

  override def delete(item: Item): Try[Item] = Try {
    db.withConnection {
      implicit c =>
        deleteItemSQL.on('id -> item.id.get).executeUpdate() match {
          case 1 => item
          case _ => throw new ItemException(s"Unable to delete item with unique ID ${item.id.get}")
        }
    }
  }

  val deleteWinningBidSQL = SQL(
    """
      |delete "winningbid"
      |where "id" = {id}
    """.stripMargin
  )

  override def delete(winningBid: WinningBid): Try[WinningBid] = Try {
    db.withConnection {
      implicit c =>
        deleteWinningBidSQL.on('id -> winningBid.id.get).executeUpdate() match {
          case 1 => winningBid
          case _ => throw new ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id.get}")
        }
    }
  }

  val itemByItemNumberSQL = SQL( """ where "item_number" = {item_number} """.stripMargin)

  override def itemByItemNumber(itemNumber: String): Try[Option[Item]] = Try {
    db.withConnection {
      implicit c =>
        itemByItemNumberSQL.on('item_number -> itemNumber).as(itemMapper.singleOpt)
    }
  }

  val winningBidsByItemSQL = SQL(winningBidSQL + """ where wb."item_id" = {item_id} """.stripMargin)

  override def winningBidsByItem(item: Item): Try[List[WinningBid]] = Try {
    db.withConnection {
      implicit c =>
        winningBidsByItemSQL.on('item_id -> item.id.get).as(winningBidMapper *)
    }
  }

  val sortedItemsSQL = SQL(itemSQL + """ order by "item_number" """.stripMargin)

  override def sortedItems: Try[List[Item]] = Try {
    db.withConnection {
      implicit c =>
        sortedItemsSQL.as(itemMapper *)
    }
  }

  val createItemSQL = SQL(
    """
      |insert "item"("item_number", "category", "donor", "description", "minbid", "estvalue")
      |values ({item_number}, {category}, {donor}, {description}, {minbid}, {estvalue})
    """.stripMargin
  )

  override def create(item: Item): Try[Item] = Try {
    db.withConnection {
      implicit c =>
        createItemSQL.on(
          'item_number -> item.itemNumber,
          'category -> item.category,
          'donor -> item.donor,
          'description -> item.description,
          'minbid -> item.minbid,
          'estvalue -> item.estvalue
        ).executeInsert(itemMapper.single)
    }
  }

  val createWinningBidSQL = SQL(
    """
      |insert "winningbid"("bidder_id", "item_id", "amount")
      |values({bidder_id}, {item_id}, {amount})
    """.stripMargin
  )

  override def create(winningBid: WinningBid): Try[WinningBid] = Try {
    db.withConnection {
      implicit c =>
        createWinningBidSQL.on(
          'bidder_id -> winningBid.bidder.id.get,
          'item_id -> winningBid.item.id.get,
          'amount -> winningBid.amount
        ).executeInsert(winningBidMapper.single)
    }
  }
}
