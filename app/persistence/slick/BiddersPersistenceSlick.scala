package persistence.slick

import javax.inject.{Inject, Singleton}

import akka.actor.ActorRef
import models.{Bidder, BidderException, ItemException, Payment}
import persistence.BiddersPersistence
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BiddersPersistenceSlick @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit val ec: ExecutionContext) extends SlickPersistence with BiddersPersistence  {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  val db = dbConfig.db

  case class BidderRow(id: Option[Long], name: String) {
    def toBidder: Future[Bidder] = Future.successful(Bidder(id, name))
  }
  object BidderRow extends ((Option[Long], String) => BidderRow) {
    def fromBidder(bidder: Bidder): Future[BidderRow] = Future.successful(BidderRow(bidder.id, bidder.name))

    def toBidder(row: BidderRow) = row.toBidder
  }

  class Bidders(tag: Tag) extends Table[BidderRow](tag, "bidder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def * = (id.?, name) <> ( BidderRow.tupled , BidderRow.unapply )

    def nameIdx = index("bidder_name_idx", name, unique = true)
  }
  val biddersQuery = TableQuery[Bidders]

  case class PaymentRow(id: Option[Long], bidderId: Long, description: String, amount: BigDecimal) {
    def toPayment: Future[Payment] = bidderById(bidderId) map {
      case Some(bidder) => Payment(id, bidder, description, amount)
      case None => throw ItemException(s"Unable to create payment from row with bidderId $bidderId")
    }
    def toPayment(bidder: Bidder) : Future[Payment] = Future.successful(Payment(id, bidder, description, amount))
  }
  object PaymentRow extends ((Option[Long], Long, String, BigDecimal) => PaymentRow) {
    def fromPayment(payment: Payment): Future[PaymentRow] = Future.successful(PaymentRow(payment.id, payment.bidder.id.get, payment.description, payment.amount))

    def toPayment(row: PaymentRow) = row.toPayment
  }

  class Payments(tag: Tag) extends Table[PaymentRow](tag, "payment") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def bidderId = column[Long]("bidder_id")
    def description = column[String]("description")
    def amount = column[BigDecimal]("amount")

    def bidderFK = foreignKey("payment_bidder_id_fk", bidderId, biddersQuery)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
    def bidderIdx = index("payment_bidder_id_idx", bidderId)

    def * = (id.?, bidderId, description, amount) <> ( PaymentRow.tupled , PaymentRow.unapply )
  }
  val paymentsQuery = TableQuery[Payments]


  override def load(biddersActor: ActorRef): Future[Boolean] = {
/*    val bidders = for(b <- biddersQuery) yield b
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
    } */
    Future.successful(true)
  }

  override def create(bidder: Bidder): Future[Bidder] = {
    BidderRow.fromBidder(bidder).flatMap { bidderRow =>
      for {
        newBidder <- db.run(
          biddersQuery returning biddersQuery.map(
            _.id
          ) += bidderRow
        ).map(id => bidder.copy(id = Some(id)))
      } yield newBidder
    }
  }

  override def create(payment: Payment): Future[Payment] = {
    PaymentRow.fromPayment(payment) flatMap { paymentRow =>
      for {
        newPayment <- db.run(
          paymentsQuery returning paymentsQuery.map(
            _.id
          ) += paymentRow
        ).map(id => payment.copy(id = Some(id)))
      } yield newPayment
    }
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

  override def paymentsByBidder(bidder: Bidder): Future[List[Payment]] =
    db.run(paymentsQuery.filter(_.bidderId === bidder.id.get).result.map(mapSeq(_.toPayment(bidder)))).flatMap(Future.sequence(_))

  override def bidderById(id: Long): Future[Option[Bidder]] =
    db.run(biddersQuery.filter(_.id === id).result.map(mapSeq(_.toBidder))).flatMap(Future.sequence(_)).map(_.headOption)

  override def bidderByName(name: String): Future[Option[Bidder]] =
    db.run(biddersQuery.filter(_.name === name).result.map(mapSeq(_.toBidder))).flatMap(Future.sequence(_)).map(_.headOption)

  override def sortedBidders: Future[List[Bidder]] = {
    db.run(biddersQuery.sortBy(_.name).result.map(mapSeq(_.toBidder))).flatMap(Future.sequence(_))
  }
}
