package actors

import akka.actor.{Actor, Props}
import models.{Organization, Event}
import persistence.slick.OrganizationsPersistenceSlick
import play.api.libs.concurrent.Akka

object OrganizationsActor {
  case object LoadFromDataSource

  case object GetOrganizations
  case class GetOrganization(id: Long)
  case class DeleteOrganization(id: Long)
  case class EditOrganization(organization: Organization)

  case class Events(organization: Organization)
  case class EditEvent(id: Long, description: String)
  case class DeleteEvent(id: Long)

  case class GetEvent(id: Long)

  def props = Props(classOf[OrganizationsActor])

  val organizationsActor = Akka.system.actorOf(OrganizationsActor.props)
  val organizationsPersistence = OrganizationsPersistenceSlick
}

class OrganizationsActor extends Actor {
  import OrganizationsActor._

  override def receive = {
    case _ =>
  }
}