package persistence.slick

import java.sql.SQLException

import akka.actor.ActorRef
import models.{Bidder, BidderException, Payment}
import persistence.BiddersPersistence

import scala.util.Try

object BiddersPersistenceSlick extends SlickPersistence with BiddersPersistence  {

  import dbConfig.profile.api._

  override def load(biddersActor: ActorRef): Boolean = {
    db withSession {
      implicit session =>
//        try {
          biddersQuery.list map {
            _.copy()
          } foreach {
            biddersActor ! _
          }
          paymentsQuery.list map {
            _.copy().toPayment
          } foreach {
            biddersActor ! _
          }
          true
//        } catch {
//          case sqle: SQLException =>
//            (biddersQuery.ddl ++ paymentsQuery.ddl).create
//            true
//        }
    }
  }

  override def create(bidder: Bidder): Try[Bidder] = Try {
    db withSession {
      implicit session =>
        val newBidderId = (biddersQuery returning biddersQuery.map(_.id)) += bidder
        Bidder(Some(newBidderId), bidder.name)
    }
  }

  override def create(payment: Payment): Try[Payment] = Try {
    db withSession {
      implicit session =>
        val newPaymentId = (paymentsQuery returning paymentsQuery.map(_.id)) += PaymentRow.fromPayment(payment)
        Payment(Some(newPaymentId), payment.bidder, payment.description, payment.amount)
    }
  }

  override def delete(bidder: Bidder): Try[Bidder] = Try {
    db withSession {
      implicit session =>
        if (biddersQuery.filter(_.id === bidder.id.get).delete == 1) {
          bidder
        } else {
          throw new BidderException(s"Unable to delete bidder with unique ID ${bidder.id.get}")
        }
    }
  }

  override def edit(bidder: Bidder): Try[Bidder] = Try {
    db withSession {
      implicit session =>
        if (biddersQuery.filter(_.id === bidder.id.get).map(_.name).update(bidder.name) == 1) {
          bidder
        } else {
          throw new BidderException(s"Unable to edit bidder with unique ID ${bidder.id.get}")
        }
    }
  }

  override def paymentsByBidder(bidder: Bidder): Try[List[Payment]] = Try {
    db withSession {
      implicit session =>
        paymentsQuery.filter(_.bidderId === bidder.id.get).list.map { row => row.toPayment }
    }
  }

  override def bidderById(id: Long): Try[Option[Bidder]] = Try {
    db withSession {
      implicit session =>
        biddersQuery.filter(_.id === id).list.headOption.map { row => row.copy() }
    }
  }

  override def bidderByName(name: String): Try[Option[Bidder]] = Try {
    db withSession {
      implicit session =>
        biddersQuery.filter(_.name === name).list.headOption.map { row => row.copy() }
    }
  }

  override def sortedBidders: Try[List[Bidder]] = Try {
    db withSession {
      implicit session =>
        biddersQuery.sortBy(_.name).list.map { row => row.copy() }
    }
  }
}
