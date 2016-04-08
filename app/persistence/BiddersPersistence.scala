package persistence

import akka.actor.ActorRef
import com.google.inject.ImplementedBy
import models.{Bidder, Payment}
import persistence.slick.BiddersPersistenceSlick

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[BiddersPersistenceSlick])
trait BiddersPersistence {

  def load(biddersActor: ActorRef): Future[Boolean]

  def create(bidder: Bidder): Future[Bidder]

  def create(payment: Payment): Future[Payment]

  def delete(bidder: Bidder): Future[Bidder]

  def edit(bidder: Bidder): Future[Bidder]

  def paymentsByBidder(bidder: Bidder): Future[List[Payment]]

  def bidderById(id: Long): Future[Option[Bidder]]

  def bidderByName(name: String): Future[Option[Bidder]]

  def sortedBidders: Future[List[Bidder]]
}
