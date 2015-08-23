package models

import akka.util.Timeout
import akka.pattern.ask
import misc.Util
import play.api.libs.json.Json

import scala.concurrent.{Await, Awaitable}
import scala.util.{Success, Failure, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

case class OrganizationException(message: String, cause: Exception = null) extends Exception

case class OrganizationData(organization: Organization, events: List[Event])

case class Organization(id: Option[Long], name: String)
object Organization extends ((Option[Long], String) => Organization) {
  import actors.OrganizationsActor._

  implicit val organizationFormat = Json.format[Organization]
  implicit val eventFormat = Json.format[Event]
  implicit val organizationDataFormat = Json.format[OrganizationData]

  implicit val timeout = Timeout(3 seconds)

  private def wait[T](awaitable: Awaitable[T]): T = {
    Await.result(awaitable, Util.defaultAwaitTimeout)
  }

  def events(organization: Organization) =
    wait { (organizationsActor ? Events(organization)).mapTo[Try[List[Event]]] }

  def addEvent(organizationId: Long, description: String): Try[Event] =
    get(organizationId) flatMap {
      case Some(organization) =>
        wait { (organizationsActor ? Event(None, organization, description)).mapTo[Try[Event]] }
      case None =>
        Failure(new OrganizationException(s"Cannot find organization ID $organizationId to add event"))
    }

  def all() = wait { (organizationsActor ? GetOrganizations).mapTo[Try[List[Organization]]] }

  def get(id: Long) = wait { (organizationsActor ? GetOrganization(id)).mapTo[Try[Option[Organization]]] }

  def create(name: String) = wait { (organizationsActor ? Organization(None, name)).mapTo[Try[Organization]] }

  def delete(id: Long) = wait { (organizationsActor ? DeleteOrganization(id)).mapTo[Try[Organization]] }

  def edit(id: Long, name: String) = wait { (organizationsActor ? EditOrganization(Organization(Some(id), name))).mapTo[Try[Organization]] }

  def loadFromDataSource() = {
    (organizationsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class EventException(message: String, cause: Exception = null) extends Exception

case class Event(id: Option[Long], organization: Organization, description: String)
object Event {

  implicit val eventFormat = Json.format[Event]

  implicit val timeout = Timeout(3 seconds)

  private def wait[T](awaitable: Awaitable[T]): T = {
    Await.result(awaitable, Util.defaultAwaitTimeout)
  }

  def all() = Success(List[Event]())
  def get(id: Long) = Success(None)
  def create(organization: Organization, description: String) = Success(Some(Event(None, organization, description)))
  def delete(id: Long) = Success(None)
  def edit(id: Long, description: String) = Success(Some(Event(Some(id), null, description)))
  def loadFromDataSource() = {
    ???
  }
}
