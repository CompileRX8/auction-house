package actors

import akka.actor.{Actor, Props}
import models._
import persistence.OrganizationsPersistence.organizations
import persistence.SlickPersistence
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.language.postfixOps
import scala.util.{Success, Try, Failure}

object OrganizationsActor {
  case object LoadFromDataSource

  case object GetOrganizations
  case class GetOrganization(id: Long)
  case class DeleteOrganization(id: Long)
  case class EditOrganization(organization: Organization)

  case object GetEvents
  case class GetEvent(id: Long)
  case class DeleteEvent(id: Long)
  case class EditEvent(event: Event)

  case object GetContacts
  case class GetContact(id: Long)
  case class DeleteContact(id: Long)
  case class EditContact(contact: Contact)

  case class Events(organization: Organization)
  case class Contacts(organization: Organization)

  def props = Props(classOf[OrganizationsActor])

  val organizationsActor = Akka.system.actorOf(OrganizationsActor.props)
}

class OrganizationsActor extends Actor {
  import OrganizationsActor._

  var organizationsMap: Map[Long, Organization] = Map.empty
  var eventsMap: Map[Long, Event] = Map.empty
  var contactsMap: Map[Long, Contact] = Map.empty

  private def nextId(m: Map[Long, _]): Long = {
    (0L /: m.keys) { (mx, id) => Math.max(mx, id) } + 1
  }

  private def find[T](id: Long, m: Map[Long, T]): Try[Option[T]] = {
    Try(m.get(id))
  }

  private def findOrg(id: Long): Try[Option[Organization]] = find(id, organizationsMap)
  private def findEvent(id: Long): Try[Option[Event]] = find(id, eventsMap)
  private def findContact(id: Long): Try[Option[Contact]] = find(id, contactsMap)

  override def receive = {
    case LoadFromDataSource =>

    case org @ Organization(None, name) =>
      organizations.create(org) onComplete { sender ! _ }

    case org @ Organization(Some(id), name) =>

    case GetOrganizations =>
      sender ! Try(organizationsMap.values)

    case GetOrganization(id) =>
      sender ! findOrg(id)

    case DeleteOrganization(id) =>
      sender ! (findOrg(id) flatMap {
        case Some(org) =>
          organizationsMap -= id
          Success(org)
        case None =>
          Failure(new OrganizationException(s"Unable to find organization ID $id to delete"))
      })

    case EditOrganization(org @ Organization(idOpt @ Some(id), _)) =>
      organizationsMap += (id -> org)
      sender ! Success(org)

    case EditOrganization(org @ Organization(None, _)) =>
      sender ! Failure(new OrganizationException(s"Unable to edit organization without an organization ID"))

    case event @ Event(None, org, description) =>
      val id = nextId(eventsMap)
      val event = Event(Some(id), org, description)
      eventsMap += (id -> event)
      sender ! Success(event)

    case event @ Event(Some(id), org, description) =>

    case GetEvents =>
      sender ! Try(eventsMap.values)

    case GetEvent(id) =>
      sender ! findEvent(id)

    case DeleteEvent(id) =>
      sender ! (findEvent(id) flatMap {
        case Some(event) =>
          eventsMap -= id
          Success(event)
        case None =>
          Failure(new EventException(s"Unable to find event ID $id to delete"))
      })

    case EditEvent(event @ Event(Some(id), _, _)) =>
      eventsMap += (id -> event)
      sender ! Success(event)

    case EditEvent(event @ Event(None, _, _)) =>
      sender ! Failure(new EventException(s"Unable to edit event without an event ID"))

    case contact @ Contact(None, org, name, email, phone) =>
      val id = nextId(contactsMap)
      val contact = Contact(Some(id), org, name, email, phone)
      contactsMap += (id -> contact)
      sender ! Success(contact)

    case contact @ Contact(Some(id), org, name, email, phone) =>

    case GetContacts =>
      sender ! Try(contactsMap.values)

    case GetContact(id) =>
      sender ! findContact(id)

    case DeleteContact(id) =>
      sender ! (findContact(id) flatMap {
        case Some(contact) =>
          contactsMap -= id
          Success(contact)
        case None =>
          Failure(new ContactException(s"Unable to find contact ID $id to delete"))
      })

    case EditContact(contact @ Contact(Some(id), _, _, _, _)) =>
      contactsMap += (id -> contact)
      sender ! Success(contact)

    case EditContact(contact @ Contact(None, _, _, _, _)) =>
      sender ! Failure(new ContactException(s"Unable to edit contact without a contact ID"))

    case Events(org @ Organization(Some(orgId), _)) =>
      sender ! Success(eventsMap.values filter { _.organization.id.get == orgId })

    case Events(org @ Organization(None, _)) =>
      sender ! Failure(new EventException(s"Unable to get events for organization with an organization ID"))

    case Contacts(org @ Organization(Some(orgId), _)) =>
      sender ! Success(contactsMap.values filter { _.organization.id.get == orgId })

    case Contacts(org @ Organization(None, _)) =>
      sender ! Failure(new EventException(s"Unable to get contacts for organization without an organization ID"))

    case _ =>
  }
}