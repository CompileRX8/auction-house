package persistence
import models.Bidder

import scala.concurrent.Future

/**
  * Created by ryan on 11/24/15.
  */
trait BiddersPersistence {

  def all(): Future[List[Bidder]]

  def forId(id: Long): Future[Option[Bidder]]

  def forEventId(eventId: Long): Future[List[Bidder]]

  def forContactId(contactId: Long): Future[List[Bidder]]

  def forEventIdAndName(eventId: Long, name: String): Future[Option[Bidder]]

  def create(bidder: Bidder): Future[Bidder]

  def delete(id: Long): Future[Option[Bidder]]

  def edit(bidder: Bidder): Future[Option[Bidder]]
}
