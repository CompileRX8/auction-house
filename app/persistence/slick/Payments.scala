package persistence.slick

import _root_.slick.driver.PostgresDriver.api._
import models._
import persistence.PaymentsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
  def toPayment: Future[Payment] = Bidders.forId(bidderId) map {
    case Some(bidder) =>
      Payment(id, bidder, description, amount)
    case None =>
      throw new PaymentException(s"Unable to find bidder ID $bidderId to convert payment row to payment")
  }
}

object PaymentRow extends ((Option[Long], Long, String, BigDecimal) => PaymentRow) {
  def fromPayment(payment: Payment): Future[PaymentRow] = Future.successful(PaymentRow(payment.id, payment.bidder.id.get, payment.description, payment.amount))

  def toPayment(row: PaymentRow): Future[Payment] = row.toPayment
}

class Payments(tag: Tag) extends Table[PaymentRow](tag, "PAYMENT") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def bidderId = column[Long]("bidder_id")

  def description = column[String]("description")

  def amount = column[BigDecimal]("amount")

  def fkBidder = foreignKey("payment_bidder_id_fk", bidderId, Bidders)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

  def idxBidder = index("payment_bidder_id_idx", bidderId)

  def * = (id.?, bidderId, description, amount) <>(PaymentRow.tupled, PaymentRow.unapply)
}

object Payments extends TableQuery(new Payments(_)) with PaymentsPersistence with SlickPersistence {

  private val findById = this.findBy(_.id)
  private val findByBidderId = this.findBy(_.bidderId)
  private val findByEventId = (eventId: Long) =>
    this join Bidders on { case (p, b) =>
      b.eventId === eventId
    } map { case (p, _) => p }
  private val findByContactId = (contactId: Long) =>
    this join Bidders on { case (p, b) =>
      p.bidderId === b.id
    } filter { case (p, b) =>
      b.contactId === contactId
    } map { case (p, b) => p }
  private val findByOrganizationId = (organizationId: Long) =>
    this join Bidders on { case (p, b) =>
      p.bidderId === b.id
    } join Contacts on { case ((p, b), c) =>
      b.contactId === c.id
    } filter { case ((p, b), c) =>
      c.organizationId === organizationId
    } map { case ((p, b), c) => p }

  def all(): Future[List[Payment]] =
    db.run(this.result.map(mapSeq(PaymentRow.toPayment))).flatMap(Future.sequence(_))

  def forId(id: Long): Future[Option[Payment]] = for {
    optFuturePaymentRow <- db.run(findById(id).result.headOption)
    futurePayment <- optFuturePaymentRow.map(_.toPayment)
    payment <- futurePayment
  } yield payment

  def forBidderId(bidderId: Long): Future[List[Payment]] =
    db.run(findByBidderId(bidderId).result.map(mapSeq(PaymentRow.toPayment))).flatMap(Future.sequence(_))

  def forEventId(eventId: Long): Future[List[Payment]] =
    db.run(findByEventId(eventId).result.map(mapSeq(PaymentRow.toPayment))).flatMap(Future.sequence(_))

  def forContactId(contactId: Long): Future[List[Payment]] =
    db.run(findByContactId(contactId).result.map(mapSeq(PaymentRow.toPayment))).flatMap(Future.sequence(_))

  def forOrganizationId(organizationId: Long): Future[List[Payment]] =
    db.run(findByOrganizationId(organizationId).result.map(mapSeq(PaymentRow.toPayment))).flatMap(Future.sequence(_))

  def create(payment: Payment): Future[Payment] = for {
    paymentRow <- PaymentRow.fromPayment(payment)
    insertQuery <- db.run(
      this returning map {
        _.id
      } into {
        (newPaymentRow, id) =>
          newPaymentRow.copy(id = Some(id)).toPayment
      } += paymentRow
    )
    payment <- insertQuery
  } yield payment

  def delete(id: Long): Future[Option[Payment]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(payment: Payment): Future[Option[Payment]] ={
    payment.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            PaymentRow.fromPayment(payment) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }
}
