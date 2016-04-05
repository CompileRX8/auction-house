package persistence

import models.Organization

import scala.concurrent.Future

trait OrganizationsPersistence {
  def all(): Future[List[Organization]]
  def forId(id: Long): Future[Option[Organization]]
  def forName(name: String): Future[Option[Organization]]
  def create(org: Organization): Future[Organization]
  def delete(id: Long): Future[Option[Organization]]
  def edit(org: Organization): Future[Option[Organization]]
}
