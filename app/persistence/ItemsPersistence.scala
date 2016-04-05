package persistence
import models.Item

import scala.concurrent.Future

/**
  * Created by ryan on 11/24/15.
  */
trait ItemsPersistence {

  def all(): Future[List[Item]]

  def forId(id: Long): Future[Option[Item]]

  def forEventId(eventId: Long): Future[List[Item]]

  def forEventIdAndItemNumber(eventId: Long, itemNumber: String): Future[Option[Item]]

  def create(item: Item): Future[Item]

  def delete(id: Long): Future[Option[Item]]

  def edit(item: Item): Future[Option[Item]]
}
