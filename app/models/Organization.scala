package models

import akka.util.Timeout
import akka.pattern.ask
import misc.Util
import play.api.libs.json.Json

import scala.util.{Failure, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

case class OrganizationException(message: String, cause: Exception = null) extends Exception(message, cause)

case class OrganizationData(organization: Organization, events: List[Event], contacts: List[Contact])

case class Organization(id: Option[Long], name: String)
object Organization extends ((Option[Long], String) => Organization) {
  import actors.OrganizationsActor._

  implicit val organizationFormat = Json.format[Organization]
  implicit val eventFormat = Json.format[Event]
  implicit val organizationDataFormat = Json.format[OrganizationData]

  implicit val timeout = Timeout(3 seconds)

  def events(organization: Organization) =
    Util.wait { (organizationsActor ? Events(organization)).mapTo[Try[List[Event]]] }

  def contacts(organization: Organization) =
    Util.wait { (organizationsActor ? Contacts(organization)).mapTo[Try[List[Contact]]] }

  def addEvent(organizationId: Long, description: String): Try[Event] =
    get(organizationId) flatMap {
      case Some(organization) =>
        Event.create(organization, description)
      case None =>
        Failure(new OrganizationException(s"Cannot find organization ID $organizationId to add event"))
    }

  def addContact(organizationId: Long, name: String, email: Option[String], phone: Option[String]): Try[Contact] =
    get(organizationId) flatMap {
      case Some(organization) =>
        Contact.create(organization, name, email, phone)
      case None =>
        Failure(new OrganizationException(s"Cannot find organization ID $organizationId to add contact"))
    }

  def all() = Util.wait { (organizationsActor ? GetOrganizations).mapTo[Try[List[Organization]]] }

  def get(id: Long) = Util.wait { (organizationsActor ? GetOrganization(id)).mapTo[Try[Option[Organization]]] }

  def create(name: String) = Util.wait { (organizationsActor ? Organization(None, name)).mapTo[Try[Organization]] }

  def delete(id: Long) = Util.wait { (organizationsActor ? DeleteOrganization(id)).mapTo[Try[Organization]] }

  def edit(id: Long, name: String) =
    Util.wait { (organizationsActor ? EditOrganization(Organization(Some(id), name))).mapTo[Try[Organization]] }

  def currentOrganizations(): Try[List[OrganizationData]] = {
    Organization.all() map { orgs =>
      orgs map { org =>
        val events = Organization.events(org).getOrElse(List())
        val contacts = Organization.contacts(org).getOrElse(List())
        OrganizationData(org, events, contacts)
      }
    }
  }

  def loadFromDataSource() = {
    (organizationsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class EventException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Event(id: Option[Long], organization: Organization, description: String)
object Event {
  import actors.OrganizationsActor._

  implicit val eventFormat = Json.format[Event]

  implicit val timeout = Timeout(3 seconds)

  def all() = Util.wait { (organizationsActor ? GetEvents).mapTo[Try[List[Event]]] }

  def get(id: Long) = Util.wait { (organizationsActor ? GetEvent(id)).mapTo[Try[Option[Event]]] }

  def create(organization: Organization, description: String) =
    Util.wait { (organizationsActor ? Event(None, organization, description)).mapTo[Try[Event]] }

  def delete(id: Long) = Util.wait { (organizationsActor ? DeleteEvent(id)).mapTo[Try[Event]] }

  def edit(id: Long, description: String) =
    get(id) flatMap {
      case Some(e @ Event(Some(_), org, oldDesc)) =>
        Util.wait { (organizationsActor ? EditEvent(Event(Some(id), org, description))).mapTo[Try[Event]] }
      case None =>
        Failure(new EventException(s"Unable to find event ID $id to edit"))
    }

  def loadFromDataSource() = {
  }
}

case class ContactException(message: String, cause: Exception = null) extends Exception(message, cause)

case class Contact(id: Option[Long], organization: Organization, name: String, email: Option[String], phone: Option[String])
object Contact {
  import actors.OrganizationsActor._

  implicit val contactFormat = Json.format[Contact]

  implicit val timeout = Timeout(3 seconds)

  def all() = Util.wait { (organizationsActor ? GetContacts).mapTo[Try[List[Contact]]] }

  def get(id: Long) = Util.wait { (organizationsActor ? GetContact(id)).mapTo[Try[Option[Contact]]] }

  def create(organization: Organization, name: String, email: Option[String], phone: Option[String]) =
    Util.wait { (organizationsActor ? Contact(None, organization, name, email, phone)).mapTo[Try[Contact]] }

  def delete(id: Long) = Util.wait { (organizationsActor ? DeleteContact(id)).mapTo[Try[Contact]] }

  def edit(id: Long, name: String, email: Option[String], phone: Option[String]) =
    get(id) flatMap {
      case Some(c @ Contact(Some(_), org, _, _, _)) =>
        Util.wait { (organizationsActor ? Contact(Some(id), org, name, email, phone)).mapTo[Try[Contact]] }
      case None =>
        Failure(new ContactException(s"Unable to find contact ID $id to edit"))
    }

  def loadFromDataSource() = {
  }
}
