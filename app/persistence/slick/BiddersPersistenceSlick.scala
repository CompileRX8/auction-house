package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import com.google.inject.Provides
import models.{Bidder, BidderException, Payment}
import persistence.BiddersPersistence
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

class BiddersPersistenceSlick @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends SlickPersistence with BiddersPersistence  {
  import driver.api._

  override def load(biddersActor: ActorRef): Future[Boolean] = {
    db.run(biddersQuery.result) flatMap { bidderRows =>
      bidderRows foreach { bidder =>
        biddersActor ! bidder
      }
      db.run(paymentsQuery.result) map { paymentRows =>
        paymentRows foreach { payment =>
          biddersActor ! payment
        }
        true
      }
    }
  }

  override def create(bidder: Bidder): Future[Bidder] = {
    val newBidderIdQuery = (biddersQuery returning paymentsQuery.map(_.id)) += bidder
    db.run(newBidderIdQuery) map { newBidderId =>
      Bidder(Some(newBidderId), bidder.name)
    }
  }

  override def create(payment: Payment): Future[Payment] = {
    val newPaymentIdQuery = (paymentsQuery returning paymentsQuery.map(_.id)) += PaymentRow.fromPayment(payment)
    db.run(newPaymentIdQuery) map { newPaymentId =>
      Payment(Some(newPaymentId), payment.bidder, payment.description, payment.amount)
    }
  }

  override def delete(bidder: Bidder): Future[Bidder] = {
    val q = biddersQuery.filter(_.id === bidder.id.get)
    db.run(q.result) flatMap { rows =>
      if(rows.length == 1) {
        db.run(q.delete) map { _ => rows.head }
      } else {
        Future.failed(new BidderException(s"Unable to delete bidder with unique ID ${bidder.id.get}"))
      }
    }
  }

  override def edit(bidder: Bidder): Future[Bidder] = {
    db.run(biddersQuery.filter(_.id === bidder.id.get).map(_.name).update(bidder.name)) flatMap { updatedRows =>
      if(updatedRows == 1) {
        Future.successful(bidder)
      } else {
        Future.failed(new BidderException(s"Unable to edit bidder with unique ID ${bidder.id.get}"))
      }
    }
  }

  override def paymentsByBidder(bidder: Bidder): Future[List[Payment]] = {
    val futPaymentRows = db.run(paymentsQuery.filter(_.bidderId === bidder.id.get).to[List].result)
    futPaymentRows flatMap { rows: List[PaymentRow] =>
      val lfPayments = rows map { row => row.toPayment }
      Future.sequence(lfPayments)
    }
  }

  override def bidderById(id: Long): Future[Option[Bidder]] = {
    db.run(biddersQuery.filter(_.id === id).result.headOption)
  }

  override def bidderByName(name: String): Future[Option[Bidder]] = {
    db.run(biddersQuery.filter(_.name === name).result.headOption)
  }

  override def sortedBidders: Future[List[Bidder]] = {
    db.run(biddersQuery.sortBy(_.name).to[List].result)
  }
}
