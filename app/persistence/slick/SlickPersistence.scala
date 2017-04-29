package persistence.slick

import models._
import play.api.db.slick._
import play.api.Play.current
import slick.jdbc.JdbcProfile

trait SlickPersistence {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile](current)

  import dbConfig.profile.api._

  val db = dbConfig.db

  type PGMoney = Double

  class Bidders(tag: Tag) extends Table[Bidder](tag, "bidder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def * = (id.?, name) <> ( Bidder.tupled , Bidder.unapply )

    def nameIdx = index("bidder_name_idx", name, unique = true)
  }
  val biddersQuery = TableQuery[Bidders]

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: PGMoney)
  object PaymentRow extends ((Option[Long], Long, String, PGMoney) => PaymentRow)

  class Payments(tag: Tag) extends Table[PaymentRow](tag, "payment") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def description = column[String]("description")
    def amount = column[PGMoney]("amount")

    def bidderFK = foreignKey("payment_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def bidderIdx = index("payment_bidder_id_idx", bidderId)

    def * = (id.?, bidderId, description, amount) <> ( PaymentRow.tupled , PaymentRow.unapply )
  }
  val paymentsQuery = TableQuery[Payments]

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

    def bidderFK = foreignKey("winningbid_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemFK = foreignKey("winningbid_item_id_fk", itemId, itemsQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemIdbidderIdIdx = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId), unique = true)
    def bidderIditemIdIdx = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId), unique = true)
  }
  val winningBidsQuery = TableQuery[WinningBids]

}
