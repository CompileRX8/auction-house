package persistence

import akka.actor.ActorRef
import models.{Payment, Bidder}

import scala.util.Try

trait BiddersPersistence {

  def load(biddersActor: ActorRef): Try[Boolean]

  def create(bidder: Bidder): Try[Bidder]

  def create(payment: Payment): Try[Payment]

  def delete(bidder: Bidder): Try[Bidder]

  def edit(bidder: Bidder): Try[Bidder]

  def paymentsByBidder(bidder: Bidder): Try[List[Payment]]

  def bidderById(id: Long): Try[Option[Bidder]]

  def bidderByName(name: String): Try[Option[Bidder]]

  def sortedBidders: Try[List[Bidder]]
}
