package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import models.{Bidder, BidderException, Payment}
import persistence.BiddersPersistence
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class BiddersPersistenceSlick @Inject()(dbConfigProvider: DatabaseConfigProvider, implicit val ec: ExecutionContext) extends SlickPersistence with BiddersPersistence  {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  val db = dbConfig.db

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

  override def load(biddersActor: ActorRef): Future[Boolean] = {
    val bidders = for(b <- biddersQuery) yield b
    val payments = for(p <- paymentsQuery) yield p
    val biddersUpdate = db.run(bidders.to[List].result).collect {
      case b =>
        biddersActor ! b
        true
    }
    val paymentsUpdate = db.run(payments.to[List].result).collect {
      case p =>
        biddersActor ! p
        true
    }
    biddersUpdate.zip(paymentsUpdate).map {
      case (b, p) => b && p
      case _ => false
    }
  }

  override def create(bidder: Bidder): Future[Bidder] = {
    db.run {
      val newBidderIdOp = (biddersQuery returning biddersQuery.map(_.id)) += bidder
      newBidderIdOp.map(bId => Bidder(Some(bId), bidder.name) )
    }
  }

  override def create(payment: Payment): Future[Payment] = {
    val createTuple = PaymentRow(None, payment.bidder.id.get, payment.description, payment.amount)
    for {
      bidder <- db.run(
        biddersQuery.filter(_.id === payment.bidder.id.get).result.head
      )
      newPayment <- db.run(
        paymentsQuery returning paymentsQuery.map {
          _.id
        } += createTuple
      ).map(id => Payment(Some(id), bidder, payment.description, payment.amount))
    } yield newPayment
  }

  override def delete(bidder: Bidder): Future[Bidder] = {
    db.run {
      biddersQuery.filter(_.id === bidder.id.get).delete map {
        case 1 => bidder
        case _ => throw BidderException(s"Unable to delete bidder with unique ID ${bidder.id.get}")
      }
    }
  }

  override def edit(bidder: Bidder): Future[Bidder] = {
    db.run {
      biddersQuery.filter(_.id === bidder.id.get).map(_.name).update(bidder.name) map {
        case 1 => bidder
        case _ => throw BidderException(s"Unable to edit bidder with unique ID ${bidder.id.get}")
      }
    }
  }

  override def paymentsByBidder(bidder: Bidder): Future[List[Payment]] = {
    val bidderId = bidder.id.get
    val bidderPayments = for {
      b <- biddersQuery if b.id === bidderId
      p <- paymentsQuery if p.bidderId === bidderId
    } yield {
      (p.id.?, b, p.description, p.amount).mapTo[Payment]
    }
    db.run(bidderPayments.to[List].result)
  }

  override def bidderById(id: Long): Future[Option[Bidder]] = {
    val bidderOpt = for(
      b <- biddersQuery if b.id === id
    ) yield b
    db.run(bidderOpt.result.headOption)
  }

  override def bidderByName(name: String): Future[Option[Bidder]] = {
    val bidderOpt = for(
      b <- biddersQuery if b.name === name
    ) yield b
    db.run(bidderOpt.result.headOption)
  }

  override def sortedBidders: Future[List[Bidder]] = {
    val bidders = for(b <- biddersQuery) yield b
    db.run(bidders.sortBy(_.name).to[List].result)
  }
}
