package persistence
import models.Contact

import scala.concurrent.Future

trait ContactsPersistence {

  def all(): Future[List[Contact]]

  def forId(id: Long): Future[Option[Contact]]

  def forOrgId(orgId: Long): Future[List[Contact]]

  def forOrgIdAndName(orgId: Long, name: String): Future[List[Contact]]

  def create(contact: Contact): Future[Contact]

  def delete(id: Long): Future[Option[Contact]]

  def edit(contact: Contact): Future[Option[Contact]]
}
