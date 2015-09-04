package persistence

import akka.actor.ActorRef
import models.{Organization, Event}

import scala.util.Try

trait OrganizationsPersistence {

  def load(organizationsActor: ActorRef): Try[Boolean]

  def create(organization: Organization): Try[Organization]

  def create(event: Event): Try[Event]

  def delete(organization: Organization): Try[Organization]

  def delete(event: Event): Try[Event]

  def edit(organization: Organization): Try[Organization]

  def edit(event: Event): Try[Event]

  def eventsByOrganization(organization: Organization): Try[List[Event]]

}
