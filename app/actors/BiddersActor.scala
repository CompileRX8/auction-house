package actors

import akka.actor.{Props, Actor}
import models.{BidderException, Item, Payment, Bidder}
import misc.Util
import scala.concurrent.Await
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka

import scala.util.{Failure, Success, Try}

object BiddersActor {
  case object LoadFromDataSource

  case object GetBidders
  case class GetBidder(id: Long)
  case class DeleteBidder(id: Long)

  case class Payments(bidder: Bidder)

  def props = Props(classOf[BiddersActor])

  val biddersActor = Akka.system.actorOf(BiddersActor.props)
}

object BiddersPersistence {
  import BiddersActor._
  import play.api.db.slick.Config.driver.simple._
  import scala.slick.jdbc.JdbcBackend
  import java.sql.SQLException

  class Bidders(tag: Tag) extends Table[Bidder](tag, "bidder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.NotNull)
    def * = (id.?, name) <> ( Bidder.tupled , Bidder.unapply )

    def nameIdx = index("bidder_name_idx", name, unique = true)
  }
  val biddersQuery = TableQuery[Bidders]

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
    def bidder(implicit session: JdbcBackend#SessionDef) = biddersQuery.filter(_.id === bidderId).first
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

    def bidderFK = foreignKey("payment_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def bidderIdx = index("payment_bidder_id_idx", bidderId)

    def * = (id.?, bidderId, description, amount) <> ( PaymentRow.tupled, PaymentRow.unapply )
  }
  val paymentsQuery = TableQuery[Payments]

  def load: Boolean = {
    Util.db withSession {
      implicit session =>
        try {
          biddersQuery.list map { _.copy() } foreach { biddersActor ! _ }
          paymentsQuery.list map { _.copy().toPayment } foreach { biddersActor ! _ }
          true
        } catch {
          case sqle: SQLException =>
            (biddersQuery.ddl ++ paymentsQuery.ddl).create
            true
        }
    }
  }

  def create(bidder: Bidder): Try[Bidder] = {
    Util.db withSession {
      implicit session =>
        Try {
          val newBidderId = (biddersQuery returning biddersQuery.map(_.id)) += bidder
          Bidder(Some(newBidderId), bidder.name)
        }
    }
  }

  def create(payment: Payment): Try[Payment] = {
    Util.db withSession {
      implicit session =>
        Try {
          val newPaymentId = (paymentsQuery returning paymentsQuery.map(_.id)) += PaymentRow.fromPayment(payment)
          Payment(Some(newPaymentId), payment.bidder, payment.description, payment.amount)
        }
    }
  }
  
  def delete(bidder: Bidder): Try[Bidder] = {
    Util.db withSession {
      implicit session =>
        if(biddersQuery.filter(_.id === bidder.id).delete == 1) {
          Success(bidder)
        } else {
          Failure(new BidderException(s"Unable to delete bidder with unique ID ${bidder.id}"))
        }
    }
  }

  def paymentsByBidder(bidder: Bidder): Try[List[Payment]] = {
    Util.db withSession {
      implicit session =>
        Try(paymentsQuery.filter(_.bidderId === bidder.id.get).list.map { row => row.toPayment })
    }
  }

  def bidderById(id: Long): Try[Option[Bidder]] = {
    Util.db withSession {
      implicit session =>
        Try(biddersQuery.filter(_.id === id).list.headOption.map { row => row.copy() })
    }
  }

  def bidderByName(name: String): Try[Option[Bidder]] = {
    Util.db withSession {
      implicit session =>
        Try(biddersQuery.filter(_.name === name).list.headOption.map { row => row.copy() })
    }
  }

  def sortedBidders: Try[List[Bidder]] = {
    Util.db withSession {
      implicit session =>
        Try(biddersQuery.sortBy(_.name).list.map { row => row.copy() } )
    }
  }

}

class BiddersActor extends Actor {
  import BiddersActor._

  private def findBidder(bidder: Bidder): Try[Option[Bidder]] = bidder.id match {
    case Some(id) => findBidder(id)
    case None => findBidder(bidder.name)
  }

  private def findBidder(id: Long): Try[Option[Bidder]] = BiddersPersistence.bidderById(id)

  private def findBidder(name: String): Try[Option[Bidder]] = BiddersPersistence.bidderByName(name)

  override def receive = {
    case LoadFromDataSource =>
      sender ! BiddersPersistence.load

    case GetBidders =>
      sender ! BiddersPersistence.sortedBidders

    case GetBidder(id) =>
      sender ! findBidder(id)

    case newBidder @ Bidder(None, name) =>
      sender ! findBidder(name).flatMap {
        case Some(bidder) => Failure(new BidderException(s"Bidder name $name already exists as ID ${bidder.id.get}"))
        case None => BiddersPersistence.create(newBidder)
      }

    case bidder @ Bidder(idOpt @ Some(id), name) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case DeleteBidder(id) =>
      sender ! findBidder(id).flatMap {
        case Some(bidder) =>
          ItemsPersistence.winningBidsByBidder(bidder).flatMap {
            case Nil =>
              BiddersPersistence.paymentsByBidder(bidder).flatMap {
                case Nil =>
                  BiddersPersistence.delete(bidder)
                case payments =>
                  Failure(new BidderException(s"Cannot delete bidder ${bidder.name} with payments"))
              }
            case winningBids =>
              Failure(new BidderException(s"Cannot delete bidder ${bidder.name} with winning bids"))
          }
        case None =>
          Failure(new BidderException(s"Cannot find bidder ID $id"))
      }

    case newPayment @ Payment(None, bidder, description, amount) =>
      sender ! findBidder(bidder).flatMap {
        case Some(bidderInfo) => BiddersPersistence.create(newPayment)
        case None => Failure(new BidderException(s"Cannot find bidder $bidder"))
      }

    case p @ Payment(idOpt @ Some(id), bidder, description, amount) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case Payments(bidder) =>
      sender ! BiddersPersistence.paymentsByBidder(bidder)

    case _ =>
  }

}
