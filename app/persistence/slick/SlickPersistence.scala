package persistence.slick

import models.{Bidder, Item, Payment, WinningBid}
import play.api.db.slick.HasDatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait SlickPersistence extends HasDatabaseConfigProvider[JdbcProfile] {
  import driver.api._

  class Bidders(tag: Tag) extends Table[Bidder](tag, "bidder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def * = (id.?, name) <> ( Bidder.tupled , Bidder.unapply )

    def nameIdx = index("bidder_name_idx", name, unique = true)
  }
  val biddersQuery = TableQuery[Bidders]

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
    def bidder = db.run(biddersQuery.filter(_.id === bidderId).result.head)
    def toPayment: Future[Payment] = bidder map { b => Payment(id, b, description, amount) }
  }
  object PaymentRow extends ((Option[Long], Long, String, BigDecimal) => PaymentRow) {
    def fromPayment(payment: Payment): PaymentRow = PaymentRow(payment.id, payment.bidder.id.get, payment.description, payment.amount)
  }

  class Payments(tag: Tag) extends Table[PaymentRow](tag, "payment") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def description = column[String]("description")
    def amount = column[BigDecimal]("amount")

    def bidderFK = foreignKey("payment_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def bidderIdx = index("payment_bidder_id_idx", bidderId)

    def * = (id.?, bidderId, description, amount) <> ( PaymentRow.tupled, PaymentRow.unapply )
  }
  val paymentsQuery = TableQuery[Payments]

  class Items(tag: Tag) extends Table[Item](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def itemNumber = column[String]("item_number")
    def category = column[String]("category")
    def donor = column[String]("donor")
    def description = column[String]("description")
    def minbid = column[BigDecimal]("minbid")
    def estvalue = column[BigDecimal]("estvalue")
    def * = (id.?, itemNumber, category, donor, description, minbid, estvalue) <> ( Item.tupled, Item.unapply )

    def itemNumberIdx = index("item_item_number_idx", itemNumber, unique = true)
  }
  val itemsQuery = TableQuery[Items]

  case class WinningBidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
    def bidder = db.run(biddersQuery.filter(_.id === bidderId).result.head)
    def item = db.run(itemsQuery.filter(_.id === itemId).result.head)
    def toWinningBid: Future[WinningBid] = for {
      b <- bidder
      i <- item
    } yield {
      WinningBid(id, b, i, amount)
    }
  }
  object WinningBidRow extends ((Option[Long], Long, Long, BigDecimal) => WinningBidRow) {
    def fromWinningBid(winningBid: WinningBid): WinningBidRow =
      WinningBidRow(winningBid.id, winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount)
  }

  class WinningBids(tag: Tag) extends Table[WinningBidRow](tag, "winningbid") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def itemId = column[Long]("item_id")
    def amount = column[BigDecimal]("amount")
    def * = (id.?, bidderId, itemId, amount) <> ( WinningBidRow.tupled, WinningBidRow.unapply )

    def bidderFK = foreignKey("winningbid_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemFK = foreignKey("winningbid_item_id_fk", itemId, itemsQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def itemIdbidderIdIdx = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId), unique = true)
    def bidderIditemIdIdx = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId), unique = true)
  }
  val winningBidsQuery = TableQuery[WinningBids]

}
