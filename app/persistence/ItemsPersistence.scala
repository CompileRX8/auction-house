package persistence

import akka.actor.ActorRef
import models.{Bidder, Bid$, Item}

import scala.util.Try

trait ItemsPersistence {

  def load(itemsActor: ActorRef): Try[Boolean]

  def create(item: Item): Try[Item]

  def create(winningBid: Bid): Try[Bid]

  def delete(item: Item): Try[Item]

  def delete(winningBid: Bid): Try[Bid]

  def edit(item: Item): Try[Item]

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[Bid]

  def winningBidById(id: Long): Try[Option[Bid]]

  def winningBidsByItem(item: Item): Try[List[Bid]]

  def winningBidsByBidder(bidder: Bidder): Try[List[Bid]]

  def sortedItems: Try[List[Item]]

  def itemById(id: Long): Try[Option[Item]]

  def itemByItemNumber(itemNumber: String): Try[Option[Item]]
}
