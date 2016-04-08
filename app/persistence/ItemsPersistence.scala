package persistence

import akka.actor.ActorRef
import com.google.inject.ImplementedBy
import models.{Bidder, Item, WinningBid}
import persistence.slick.ItemsPersistenceSlick

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[ItemsPersistenceSlick])
trait ItemsPersistence {

  def load(itemsActor: ActorRef): Future[Boolean]

  def create(item: Item): Future[Item]

  def create(winningBid: WinningBid): Future[WinningBid]

  def delete(item: Item): Future[Item]

  def delete(winningBid: WinningBid): Future[WinningBid]

  def edit(item: Item): Future[Item]

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid]

  def winningBidById(id: Long): Future[Option[WinningBid]]

  def winningBidsByItem(item: Item): Future[List[WinningBid]]

  def winningBidsByBidder(bidder: Bidder): Future[List[WinningBid]]

  def sortedItems: Future[List[Item]]

  def itemById(id: Long): Future[Option[Item]]

  def itemByItemNumber(itemNumber: String): Future[Option[Item]]
}
