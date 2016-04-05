package persistence

import models.Event

import scala.concurrent.Future

trait EventsPersistence {
  def all(): Future[List[Event]]
  def forId(id: Long): Future[Option[Event]]
  def forOrgId(orgId: Long): Future[List[Event]]
  def create(event: Event): Future[Event]
  def delete(id: Long): Future[Option[Event]]
  def edit(event: Event): Future[Option[Event]]
}
