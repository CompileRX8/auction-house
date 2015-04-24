package persistence.anorm

import akka.actor.ActorRef
import models.{Bidder, Item, WinningBid}
import persistence.ItemsPersistence

import scala.util.Try

object ItemsPersistenceAnorm extends AnormPersistence with ItemsPersistence {
  override def load(itemsActor: ActorRef): Boolean = ???

  override def itemById(id: Long): Try[Option[Item]] = ???

  override def winningBidById(id: Long): Try[Option[WinningBid]] = ???

  override def winningBidsByBidder(bidder: Bidder): Try[List[WinningBid]] = ???

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[WinningBid] = ???

  override def edit(item: Item): Try[Item] = ???

  override def delete(item: Item): Try[Item] = ???

  override def delete(winningBid: WinningBid): Try[WinningBid] = ???

  override def itemByItemNumber(itemNumber: String): Try[Option[Item]] = ???

  override def winningBidsByItem(item: Item): Try[List[WinningBid]] = ???

  override def sortedItems: Try[List[Item]] = ???

  override def create(item: Item): Try[Item] = ???

  override def create(winningBid: WinningBid): Try[WinningBid] = ???
}
