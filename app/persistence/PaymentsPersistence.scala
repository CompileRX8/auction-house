package persistence

import misc.Util
import models._
import persistence.BiddersPersistence.bidders
import persistence.ContactsPersistence.contacts
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object PaymentsPersistence extends SlickPersistence {

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
    def toPayment: Payment = Util.wait(bidders.forId(bidderId) map {
      case Some(bidder) =>
        Payment(id, bidder, description, amount)
      case None =>
        throw new PaymentException(s"Unable to find bidder ID $bidderId to convert payment row to payment")
    })
  }

  object PaymentRow extends ((Option[Long], Long, String, BigDecimal) => PaymentRow) {
    def fromPayment(payment: Payment): PaymentRow = PaymentRow(payment.id, payment.bidder.id.get, payment.description, payment.amount)

    def toPayment(row: PaymentRow) = row.toPayment
  }

  class Payments(tag: Tag) extends Table[PaymentRow](tag, "PAYMENT") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def bidderId = column[Long]("bidder_id")

    def description = column[String]("description")

    def amount = column[BigDecimal]("amount")

    def fkBidder = foreignKey("payment_bidder_id_fk", bidderId, bidders)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

    def idxBidder = index("payment_bidder_id_idx", bidderId)

    def * = (id.?, bidderId, description, amount) <>(PaymentRow.tupled, PaymentRow.unapply)
  }

  object payments extends TableQuery(new Payments(_)) {
    private val findById = this.findBy(_.id)
    private val findByBidderId = this.findBy(_.bidderId)
    private val findByEventId = (eventId: Long) =>
      this join bidders on { case (p, b) =>
        b.eventId === eventId
      } map { case (p, _) => p }
    private val findByContactId = (contactId: Long) =>
      this join bidders on { case (p, b) =>
        p.bidderId === b.id
      } filter { case (p, b) =>
        b.contactId === contactId
      } map { case (p, b) => p }
    private val findByOrganizationId = (organizationId: Long) =>
      this join bidders on { case (p, b) =>
        p.bidderId === b.id
      } join contacts on { case ((p, b), c) =>
          b.contactId === c.id
      } filter { case ((p, b), c) =>
          c.organizationId === organizationId
      } map { case ((p, b), c) => p }

    def all(): Future[List[Payment]] =
      db.run(this.result.map(mapSeq(PaymentRow.toPayment)))

    def forId(id: Long): Future[Option[Payment]] =
      db.run(findById(id).result.headOption.map(mapOption(PaymentRow.toPayment)))

    def forBidderId(bidderId: Long) =
      db.run(findByBidderId(bidderId).result.map(mapSeq(PaymentRow.toPayment)))

    def forEventId(eventId: Long) =
      db.run(findByEventId(eventId).result.map(mapSeq(PaymentRow.toPayment)))

    def forContactId(contactId: Long) =
      db.run(findByContactId(contactId).result.map(mapSeq(PaymentRow.toPayment)))

    def forOrganizationId(organizationId: Long) =
      db.run(findByOrganizationId(organizationId).result.map(mapSeq(PaymentRow.toPayment)))

    def create(payment: Payment): Future[Payment] =
      db.run(
        this returning map {
          _.id
        } into {
          (newPaymentRow, id) =>
            newPaymentRow.copy(id = Some(id)).toPayment
        } += PaymentRow.fromPayment(payment)
      )

    def delete(id: Long): Future[Option[Payment]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def update(payment: Payment): Future[Option[Payment]] =
      editHandler(payment.id, payment, forId) { id =>
        findById(id).update(PaymentRow.fromPayment(payment))
      }
  }


}
