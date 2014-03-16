package actors

import akka.actor.{Props, Actor}
import models.{Item, Payment, Bidder}
import misc.Util
import scala.concurrent.Await
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka

object BiddersActor {
  case object LoadFromDataSource

  case object GetBidders
  case class GetBidder(id: Long)
  case class DeleteBidder(id: Long)

  case class Payments(bidder: Bidder)
  case class PaymentsTotal(bidder: Bidder)

  class BidderInfo(val bidder: Bidder) {
    private var payments: List[Payment] = Nil

    def addPayment(payment: Payment) {
      payments :+= payment
    }

    def getPayments = payments
  }

  def props = Props(classOf[BiddersActor])

  val biddersActor = Akka.system.actorOf(BiddersActor.props)
}

object BiddersPersistence {
  import BiddersActor._
  import play.api.Play.current
  import play.api.db.DB
  import scala.slick.driver.PostgresDriver.simple._
  import scala.slick.jdbc.JdbcBackend

  class Bidders(tag: Tag) extends Table[Bidder](tag, "bidder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.NotNull)
    def * = (id.?, name) <> ( Bidder.tupled , Bidder.unapply )
  }
  val biddersQuery = TableQuery[Bidders]

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
    def bidder(implicit session: JdbcBackend#SessionDef) = biddersQuery.where(_.id === bidderId).first
    def toPayment(implicit session: JdbcBackend#SessionDef): Payment = Payment(id, bidder, description, amount)
  }
  object PaymentRow extends ((Option[Long], Long, String, BigDecimal) => PaymentRow) {
    def fromPayment(payment: Payment): PaymentRow = PaymentRow(payment.id, payment.bidder.id.get, payment.description, payment.amount)
  }

  class Payments(tag: Tag) extends Table[PaymentRow](tag, "payment") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id", O.NotNull)
    def description = column[String]("description", O.NotNull)
    def amount = column[BigDecimal]("amount", O.NotNull)

    def bidderFK = foreignKey("bidder_fk", bidderId, biddersQuery)(_.id)

    def * = (id.?, bidderId, description, amount) <> ( PaymentRow.tupled, PaymentRow.unapply )
  }
  val paymentsQuery = TableQuery[Payments]

  private def db = Database.forDataSource(DB.getDataSource())

  def load: Boolean = {
    db withSession {
      implicit session =>
        biddersQuery.list() map { _.copy() } foreach { biddersActor ! _ }
        paymentsQuery.list() map { _.copy().toPayment } foreach { biddersActor ! _ }
        true
    }
  }

  def create(bidder: Bidder): Option[Bidder] = {
    db withSession {
      implicit session =>
        val newBidderId = (biddersQuery returning biddersQuery.map(_.id)) += bidder
        Some(Bidder(Some(newBidderId), bidder.name))
    }
  }

  def create(payment: Payment): Option[Payment] = {
    db withSession {
      implicit session =>
        val newPaymentId = (paymentsQuery returning paymentsQuery.map(_.id)) += PaymentRow.fromPayment(payment)
        Some(Payment(Some(newPaymentId), payment.bidder, payment.description, payment.amount))
    }
  }
  
  def delete(bidder: Bidder): Option[Bidder] = {
    db withSession {
      implicit session =>
        if(biddersQuery.where(_.id === bidder.id).delete == 1) {
          Some(bidder)
        } else {
          None
        }
    }
  }

}

class BiddersActor extends Actor {
  import BiddersActor._

  private var bidders: Set[BidderInfo] = Set.empty

  private def findBidderInfo(bidder: Bidder): Option[BidderInfo] = {
    bidders find { bi => bi.bidder == bidder }
  }

  private def findBidderInfo(id: Long): Option[BidderInfo] = {
    bidders find { bi => bi.bidder.id.get == id }
  }

  private def findBidderInfo(name: String): Option[BidderInfo] = {
    bidders find { bi => bi.bidder.name.equals(name) }
  }

  private def winningBidsIsEmpty(bidderInfo: BidderInfo): Boolean = {
    val isEmptyFuture = Item.winningBids(bidderInfo.bidder) map { paymentsOpt =>
      paymentsOpt map { payments => payments.isEmpty } getOrElse true
    }
    Await.result(isEmptyFuture, Util.defaultAwaitTimeout)
  }

  private def createBidderInfo(bidder: Bidder): BidderInfo = {
    val bidderInfo = new BidderInfo(bidder)
    bidders += bidderInfo
    bidderInfo
  }

  override def receive = {
    case LoadFromDataSource =>
      sender ! BiddersPersistence.load

    case GetBidders =>
      val bs = bidders.map(_.bidder).toList.sortBy(_.name)
      sender ! bs

    case GetBidder(id) =>
      sender ! (findBidderInfo(id) map { _.bidder })

    case newBidder @ Bidder(None, name) =>
      findBidderInfo(name) match {
        case Some(_) => sender ! None
        case None =>
          sender ! BiddersPersistence.create(newBidder).map { bidder =>
            createBidderInfo(bidder)
            bidder
          }
      }

    case bidder @ Bidder(idOpt @ Some(id), name) =>
      findBidderInfo(name) match {
        case Some(_) =>
        case None =>
          createBidderInfo(bidder)
      }

    case DeleteBidder(id) =>
      findBidderInfo(id) match {
        case Some(bidderInfo) if bidderInfo.getPayments.isEmpty && winningBidsIsEmpty(bidderInfo) =>
          sender !  BiddersPersistence.delete(bidderInfo.bidder).map { bidder =>
            bidders = bidders filter { _.bidder.id.get != id }
            bidder
          }
        case _ =>
          sender ! None
      }

    case newPayment @ Payment(None, bidder, description, amount) =>
      findBidderInfo(bidder) match {
        case Some(bidderInfo) =>
          sender ! BiddersPersistence.create(newPayment).map { payment =>
            bidderInfo.addPayment(payment)
            payment
          }
        case None =>
          sender ! None
      }

    case p @ Payment(idOpt @ Some(id), bidder, description, amount) =>
      findBidderInfo(bidder) match {
        case Some(bidderInfo) =>
          bidderInfo.addPayment(p)
        case None =>
      }

    case Payments(bidder) =>
      sender ! (findBidderInfo(bidder) map { bi => bi.getPayments })

    case PaymentsTotal(bidder) =>
      val total = findBidderInfo(bidder) map { bi => (bi.getPayments map { _.amount }).sum }
      sender ! total

    case _ =>
  }

}
