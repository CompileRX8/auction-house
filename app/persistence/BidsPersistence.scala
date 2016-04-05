package persistence
import models.Bid

import scala.concurrent.Future

/**
  * Created by ryan on 11/24/15.
  */
trait BidsPersistence {

  def forId(id: Long): Future[Option[Bid]]

  def forEventId(eventId: Long): Future[List[Bid]]

  def forItemId(itemId: Long): Future[List[Bid]]

  def forBidderId(bidderId: Long): Future[List[Bid]]

  def forEventIdAndBidderId(eventId: Long, bidderId: Long): Future[List[Bid]]

  def create(bid: Bid): Future[Option[Bid]]

  def delete(id: Long): Future[Option[Bid]]

  def edit(bid: Bid): Future[Option[Bid]]
}
