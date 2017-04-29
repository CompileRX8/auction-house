package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import models.{Bidder, BidderException, Payment}
import persistence.BiddersPersistence

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BiddersPersistenceSlick @Inject() extends SlickPersistence with BiddersPersistence  {

  import dbConfig.profile.api._

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
