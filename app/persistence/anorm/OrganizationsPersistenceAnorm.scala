package persistence.anorm

import anorm._
import anorm.SqlParser._
import akka.actor.ActorRef
import models.{Event, Organization}
import persistence.OrganizationsPersistence
import play.api.Play.current
import scala.language.postfixOps

import scala.util.Try

object OrganizationsPersistenceAnorm extends AnormPersistence with OrganizationsPersistence {
  val organizationSQL =
    """
      |select id, name from organization
    """.stripMargin

  val organizationMapper = {
    get[Long]("id") ~
    get[String]("name") map {
      case id ~ name => Organization(Some(id), name)
    }
  }

  val eventSQL =
    """
      |select e.id, e.organization_id, e.name, o.name
      |from event e
      |inner join organization o on e.organization_id = o.id
    """.stripMargin

  val eventMapper = {
    get[Long]("e.id") ~
    get[Long]("e.organization_id") ~
    get[String]("e.name") ~
    get[String]("o.name") map {
      case id ~ orgId ~ eventName ~ orgName =>
        Event(Some(id), Organization(Some(orgId), orgName), eventName)
    }
  }

  override def load(organizationsActor: ActorRef): Try[Boolean] = withDBConnection {
    implicit c =>
      SQL(organizationSQL).as(organizationMapper *).foreach { org =>
        organizationsActor ! org
      }

      SQL(eventSQL).as(eventMapper *).foreach { event =>
        organizationsActor ! event
      }

      true
  }

  override def edit(organization: Organization): Try[Organization] = ???

  override def edit(event: Event): Try[Event] = ???

  override def delete(organization: Organization): Try[Organization] = ???

  override def delete(event: Event): Try[Event] = ???

  override def create(event: Event): Try[Event] = ???

  override def create(organization: Organization): Try[Organization] = ???

  override def eventsByOrganization(organization: Organization): Try[List[Event]] = ???

}
