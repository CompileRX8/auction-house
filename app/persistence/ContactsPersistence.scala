package persistence

import misc.Util
import models._
import persistence.OrganizationsPersistence.organizations
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object ContactsPersistence extends SlickPersistence {

  case class ContactRow(id: Option[Long], organizationId: Long, name: String, email: Option[String], phone: Option[String]) {
    def toContact: Contact = Util.wait(organizations.forId(organizationId) map {
      case Some(org) =>
        Contact(id, org, name, email, phone)
      case None =>
        throw new ContactException(s"Unable to find organization ID $organizationId to convert contact row to contact")
    })
  }

  object ContactRow extends ((Option[Long], Long, String, Option[String], Option[String]) => ContactRow) {
    def fromContact(contact: Contact) = ContactRow(contact.id, contact.organization.id.get, contact.name, contact.email, contact.phone)

    def toContact(row: ContactRow) = row.toContact
  }

  class Contacts(tag: Tag) extends Table[ContactRow](tag, "CONTACT") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def organizationId = column[Long]("ORGANIZATION_ID")

    def name = column[String]("NAME")

    def email = column[Option[String]]("EMAIL")

    def phone = column[Option[String]]("PHONE")

    def * = (id.?, organizationId, name, email, phone) <>(ContactRow.tupled, ContactRow.unapply)

    def fkOrganization = foreignKey("contact_organization_id_fk", organizationId, organizations)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

    def idxOrganization = index("contact_organization_id_idx", organizationId)
  }

  object contacts extends TableQuery(new Contacts(_)) {
    private val findById = this.findBy(_.id)
    private val findByOrgId = this.findBy(_.organizationId)
    private val findByNameAndOrgId = (name: String, orgId: Long) => this.filter(_.name === name).filter(_.organizationId === orgId)

    def all(): Future[List[Contact]] = db.run(this.result.map(mapSeq(ContactRow.toContact)))

    def forId(id: Long): Future[Option[Contact]] =
      db.run(findById(id).result.headOption.map(mapOption(ContactRow.toContact)))

    def forOrgId(orgId: Long): Future[List[Contact]] =
      db.run(findByOrgId(orgId).result.map(mapSeq(ContactRow.toContact)))

    def forOrgIdAndName(orgId: Long, name: String): Future[List[Contact]] =
      db.run(findByNameAndOrgId(name, orgId).result.map(mapSeq(ContactRow.toContact)))

    def create(contact: Contact): Future[Contact] =
      db.run(
        this returning map {
          _.id
        } into { (newContactRow, id) =>
          newContactRow.copy(id = Some(id)).toContact
        } += ContactRow.fromContact(contact)
      )

    def delete(id: Long): Future[Option[Contact]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(contact: Contact): Future[Option[Contact]] =
      editHandler(contact.id, contact, forId) { id =>
        findById(id).update(ContactRow.fromContact(contact))
      }
  }

}
