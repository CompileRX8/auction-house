package persistence.slick

import _root_.slick.driver.PostgresDriver.api._
import models._
import persistence.ContactsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class ContactRow(id: Option[Long], organizationId: Long, name: String, email: Option[String], phone: Option[String]) {
  def toContact: Future[Contact] = Organizations.forId(organizationId) map {
    case Some(org) =>
      Contact(id, org, name, email, phone)
    case None =>
      throw new ContactException(s"Unable to find organization ID $organizationId to convert contact row to contact")
  }
}

object ContactRow extends ((Option[Long], Long, String, Option[String], Option[String]) => ContactRow) {
  def fromContact(contact: Contact): Future[ContactRow] = Future.successful(ContactRow(contact.id, contact.organization.id.get, contact.name, contact.email, contact.phone))

  def toContact(row: ContactRow): Future[Contact] = row.toContact
}

class Contacts(tag: Tag) extends Table[ContactRow](tag, "CONTACT") {
  def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

  def organizationId = column[Long]("ORGANIZATION_ID")

  def name = column[String]("NAME")

  def email = column[Option[String]]("EMAIL")

  def phone = column[Option[String]]("PHONE")

  def * = (id.?, organizationId, name, email, phone) <>(ContactRow.tupled, ContactRow.unapply)

  def fkOrganization = foreignKey("contact_organization_id_fk", organizationId, Organizations)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

  def idxOrganization = index("contact_organization_id_idx", organizationId)
}

object Contacts extends TableQuery(new Contacts(_)) with ContactsPersistence with SlickPersistence {
  private val findById = this.findBy(_.id)
  private val findByOrgId = this.findBy(_.organizationId)
  private val findByNameAndOrgId = (name: String, orgId: Long) => this.filter(_.name === name).filter(_.organizationId === orgId)

  def all(): Future[List[Contact]] = db.run(this.result.map(mapSeq(ContactRow.toContact))).flatMap(Future.sequence(_))

  def forId(id: Long): Future[Option[Contact]] = for {
    optContactRow <- db.run(findById(id).result.headOption)
    futureContact <- optContactRow.map(_.toContact)
    contact <- futureContact
  } yield contact

  def forOrgId(orgId: Long): Future[List[Contact]] =
    db.run(findByOrgId(orgId).result.map(mapSeq(ContactRow.toContact))).flatMap(Future.sequence(_))

  def forOrgIdAndName(orgId: Long, name: String): Future[List[Contact]] =
    db.run(findByNameAndOrgId(name, orgId).result.map(mapSeq(ContactRow.toContact))).flatMap(Future.sequence(_))

  def create(contact: Contact): Future[Contact] = for {
    contactRow <- ContactRow.fromContact(contact)
    insertQuery <- db.run(
      this returning map {
        _.id
      } into { (newContactRow, id) =>
        newContactRow.copy(id = Some(id)).toContact
      } += contactRow
    )
    contact <- insertQuery
  } yield contact

  def delete(id: Long): Future[Option[Contact]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(contact: Contact): Future[Option[Contact]] = {
    contact.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            ContactRow.fromContact(contact) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }
}
