package persistence

import _root_.slick.driver.PostgresDriver.api._
import models.Organization

import scala.concurrent.Future

object OrganizationsPersistence extends SlickPersistence {

  class Organizations(tag: Tag) extends Table[Organization](tag, "ORGANIZATION") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("NAME")

    def * = (id.?, name) <>(Organization.tupled, Organization.unapply)
  }

  object organizations extends TableQuery(new Organizations(_)) {
    private val findById = this.findBy(_.id)
    private val findByName = this.findBy(_.name)

    def all() = db.run(this.result.map(mapSeq { _ }))

    def forId(id: Long) = db.run(findById(id).result.headOption)

    def forName(name: String) = db.run(findByName(name).result.headOption)

    def create(org: Organization): Future[Organization] = {
      db.run(
        this returning map {
          _.id
        } into {
          (newOrg, id) => newOrg.copy(id = Some(id))
        } += org
      )
    }

    def delete(id: Long): Future[Option[Organization]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(org: Organization): Future[Option[Organization]] =
      editHandler(org.id, org, forId) { id =>
        findById(id).update(org)
      }
  }

}
