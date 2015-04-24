package persistence.anorm

import java.sql.SQLException

import anorm._
import play.api.db.DB
import play.api.Play.current
import akka.actor.ActorRef
import models.{Bidder, Payment}
import persistence.BiddersPersistence

import scala.util.Try

object BiddersPersistenceAnorm extends AnormPersistence with BiddersPersistence {
  override def load(biddersActor: ActorRef): Boolean = ??? /*{
    db withConnection {
      implicit c =>
        try {
          biddersQueryAll() map { row =>
            _.copy()
          } foreach {
            biddersActor ! _
          }
          paymentsQueryAll() map { row =>
            _.copy().toPayment
          } foreach {
            biddersActor ! _
          }
          true
        } catch {
          case sqle: SQLException =>
            (biddersQuery.ddl ++ paymentsQuery.ddl).create
            true
        }
    }
  } */

  override def paymentsByBidder(bidder: Bidder): Try[List[Payment]] = ???

  override def sortedBidders: Try[List[Bidder]] = ???

  override def bidderById(id: Long): Try[Option[Bidder]] = ???

  override def edit(bidder: Bidder): Try[Bidder] = ???

  override def delete(bidder: Bidder): Try[Bidder] = ???

  override def bidderByName(name: String): Try[Option[Bidder]] = ???

  override def create(bidder: Bidder): Try[Bidder] = ???

  override def create(payment: Payment): Try[Payment] = ???
}
