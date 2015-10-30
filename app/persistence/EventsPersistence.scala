package persistence

import misc.Util
import models._
import persistence.OrganizationsPersistence.organizations
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object EventsPersistence extends SlickPersistence {

  case class EventRow(id: Option[Long], organizationId: Long, description: String) {
    def toEvent: Event = Util.wait(organizations.forId(organizationId) map {
      case Some(org) =>
        Event(id, org, description)
      case None =>
        throw new EventException(s"Unable to find organization ID $organizationId to convert event row to event")
    })
  }

  object EventRow extends ((Option[Long], Long, String) => EventRow) {
    def fromEvent(event: Event) = EventRow(event.id, event.organization.id.get, event.description)

    def toEvent(row: EventRow) = row.toEvent
  }

  class Events(tag: Tag) extends Table[EventRow](tag, "EVENT") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def organizationId = column[Long]("ORGANIZATION_ID")

    def name = column[String]("NAME")

    def * = (id.?, organizationId, name) <>(EventRow.tupled, EventRow.unapply)

    def fkOrganization = foreignKey("event_organization_id_fk", organizationId, organizations)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

    def organizationIdx = index("event_organization_id_idx", organizationId)
  }

  object events extends TableQuery(new Events(_)) {
    private val findById = this.findBy(_.id)
    private val findByOrgId = this.findBy(_.organizationId)

    def all(): Future[List[Event]] = db.run(this.result.map(mapSeq(EventRow.toEvent)))

    def forId(id: Long): Future[Option[Event]] =
      db.run(findById(id).result.headOption.map(mapOption(EventRow.toEvent)))

    def forOrgId(orgId: Long): Future[List[Event]] =
      db.run(findByOrgId(orgId).result.map(mapSeq(EventRow.toEvent)))

    def create(event: Event): Future[Event] =
      db.run(
        this returning map {
          _.id
        } into { (newEventRow, id) =>
          newEventRow.copy(id = Some(id)).toEvent
        } += EventRow.fromEvent(event)
      )

    def delete(id: Long): Future[Option[Event]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(event: Event): Future[Option[Event]] =
      editHandler(event.id, event, forId) { id =>
        findById(id).update(EventRow.fromEvent(event))
      }
  }

}
