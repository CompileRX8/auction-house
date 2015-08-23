package persistence

import akka.actor.ActorRef
import models.{Bidder, WinningBid, Item}

import scala.util.Try

trait ItemsPersistence {

  def load(itemsActor: ActorRef): Try[Boolean]

  def create(item: Item): Try[Item]

  def create(winningBid: WinningBid): Try[WinningBid]

  def delete(item: Item): Try[Item]

  def delete(winningBid: WinningBid): Try[WinningBid]

  def edit(item: Item): Try[Item]

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[WinningBid]

  def winningBidById(id: Long): Try[Option[WinningBid]]

  def winningBidsByItem(item: Item): Try[List[WinningBid]]

  def winningBidsByBidder(bidder: Bidder): Try[List[WinningBid]]

  def sortedItems: Try[List[Item]]

  def itemById(id: Long): Try[Option[Item]]

  def itemByItemNumber(itemNumber: String): Try[Option[Item]]
}
