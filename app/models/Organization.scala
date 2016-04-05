package models

import javax.inject.Inject

import akka.util.Timeout
import persistence.{ContactsPersistence, EventsPersistence, OrganizationsPersistence}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class OrganizationException(message: String, cause: Exception = null) extends Exception(message, cause)

case class OrganizationData(organization: Organization, events: List[Event], contacts: List[Contact])

case class Organization(id: Option[Long], name: String)

object Organization extends ((Option[Long], String) => Organization) {

  @Inject
  val organizationsPersistence: OrganizationsPersistence = null

  @Inject
  val eventsPersistence: EventsPersistence = null

  @Inject
  val contactsPersistence: ContactsPersistence = null

  implicit val organizationFormat = Json.format[Organization]
  implicit val contactFormat = Json.format[Contact]
  implicit val eventFormat = Json.format[Event]
  implicit val organizationDataFormat = Json.format[OrganizationData]

  implicit val timeout = Timeout(3 seconds)

  def events(organization: Organization) =
    eventsPersistence.forOrgId(organization.id.get)

  def contacts(organization: Organization) =
    contactsPersistence.forOrgId(organization.id.get)

  def addEvent(organizationId: Long, description: String): Future[Event] =
    get(organizationId) flatMap {
      case Some(organization) =>
        Event.create(organization, description)
      case None =>
        Future.failed(new OrganizationException(s"Cannot find organization ID $organizationId to add event"))
    }

  def addContact(organizationId: Long, name: String, email: Option[String], phone: Option[String]): Future[Contact] =
    get(organizationId) flatMap {
      case Some(organization) =>
        Contact.create(organization, name, email, phone)
      case None =>
        Future.failed(new OrganizationException(s"Cannot find organization ID $organizationId to add contact"))
    }

  def all() = organizationsPersistence.all()

  def get(id: Long) = organizationsPersistence.forId(id)

  def create(name: String) = organizationsPersistence.create(Organization(None, name))

  def delete(id: Long) = organizationsPersistence.delete(id)

  def edit(id: Long, name: String) =
    organizationsPersistence.edit(Organization(Some(id), name))

  def currentOrganizations(): Future[List[OrganizationData]] = {
    for {
      orgs <- Organization.all()
      org <- orgs
      events <- Organization.events(org)
      contacts <- Organization.contacts(org)
    } yield {
      OrganizationData(org, events, contacts)
    }
  }

  def loadFromDataSource() = {
  }
}

case class EventException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Event(id: Option[Long], organization: Organization, description: String)

object Event extends ((Option[Long], Organization, String) => Event) {

  @Inject
  val eventsPersistence: EventsPersistence = null

  implicit val organizationFormat = Json.format[Organization]
  implicit val eventFormat = Json.format[Event]

  implicit val timeout = Timeout(3 seconds)

  def all(): Future[List[Event]] = eventsPersistence.all()

  def get(id: Long): Future[Option[Event]] = eventsPersistence.forId(id)

  def create(organization: Organization, description: String): Future[Event] =
    eventsPersistence.create(Event(None, organization, description))

  def delete(id: Long): Future[Option[Event]] = eventsPersistence.delete(id)

  def edit(id: Long, description: String): Future[Option[Event]] =
    get(id) flatMap {
      case Some(e@Event(Some(_), org, _)) =>
        eventsPersistence.edit(Event(Some(id), org, description))
      case None =>
        Future.failed(new EventException(s"Unable to find event ID $id to edit"))
    }

  def loadFromDataSource() = {
  }
}

case class ContactException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Contact(id: Option[Long], organization: Organization, name: String, email: Option[String], phone: Option[String])

object Contact extends ((Option[Long], Organization, String, Option[String], Option[String]) => Contact) {

  @Inject
  val contactsPersistence: ContactsPersistence = null

  implicit val organizationFormat = Json.format[Organization]
  implicit val contactFormat = Json.format[Contact]

  implicit val timeout = Timeout(3 seconds)

  def all() = contactsPersistence.all()

  def get(id: Long) = contactsPersistence.forId(id)

  def create(organization: Organization, name: String, email: Option[String], phone: Option[String]) =
    contactsPersistence.create(Contact(None, organization, name, email, phone))

  def delete(id: Long) = contactsPersistence.delete(id)

  def edit(id: Long, name: String, email: Option[String], phone: Option[String]) =
    get(id) flatMap {
      case Some(c@Contact(Some(_), org, _, _, _)) =>
        contactsPersistence.edit(c)
      case None =>
        Future.failed(new ContactException(s"Unable to find contact ID $id to edit"))
    }

  def loadFromDataSource() = {
  }
}
