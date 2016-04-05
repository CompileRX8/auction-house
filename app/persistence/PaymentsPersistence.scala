package persistence

import models.Payment

import scala.concurrent.Future

trait PaymentsPersistence {
  def all(): Future[List[Payment]]
  def forId(id: Long): Future[Option[Payment]]
  def forBidderId(bidderId: Long): Future[List[Payment]]
  def forEventId(eventId: Long): Future[List[Payment]]
  def forContactId(contactId: Long): Future[List[Payment]]
  def forOrganizationId(organizationId: Long): Future[List[Payment]]
  def create(payment: Payment): Future[Payment]
  def delete(id: Long): Future[Option[Payment]]
  def edit(payment: Payment): Future[Option[Payment]]
}
