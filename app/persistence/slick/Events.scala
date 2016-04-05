package persistence.slick

import _root_.slick.driver.PostgresDriver.api._
import models._
import persistence.EventsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class EventRow (id: Option[Long], organizationId: Long, description: String) {
  def toEvent: Future[Event] = Organizations.forId(organizationId) map {
    case Some(org) =>
      Event(id, org, description)
    case None =>
      throw new EventException(s"Unable to find organization ID $organizationId to convert event row to event")
  }
}

object EventRow extends ((Option[Long], Long, String) => EventRow) {
  def fromEvent(event: Event): Future[EventRow] = Future.successful(EventRow(event.id, event.organization.id.get, event.description))

  def toEvent(row: EventRow): Future[Event] = row.toEvent
}

class Events(tag: Tag) extends Table[EventRow](tag, "EVENT") {
  def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

  def organizationId = column[Long]("ORGANIZATION_ID")

  def name = column[String]("NAME")

  def * = (id.?, organizationId, name) <>(EventRow.tupled, EventRow.unapply)

  def fkOrganization = foreignKey("event_organization_id_fk", organizationId, Organizations)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

  def organizationIdx = index("event_organization_id_idx", organizationId)
}

object Events extends TableQuery(new Events(_)) with EventsPersistence with SlickPersistence {
  private val findById = this.findBy(_.id)
  private val findByOrgId = this.findBy(_.organizationId)

  def all(): Future[List[Event]] = db.run(this.result.map(mapSeq(EventRow.toEvent))).flatMap(Future.sequence(_))

  def forId(id: Long): Future[Option[Event]] = for {
    optEventRow <- db.run(findById(id).result.headOption)
    futureEvent <- optEventRow.map(_.toEvent)
    event <- futureEvent
  } yield event

  def forOrgId(orgId: Long): Future[List[Event]] =
    db.run(findByOrgId(orgId).result.map(mapSeq(EventRow.toEvent))).flatMap(Future.sequence(_))

  def create(event: Event): Future[Event] = {
    for {
      eventRow <- EventRow.fromEvent(event)
      insertQuery <- db.run(
        this returning map {
          _.id
        } into { (newEventRow, id) =>
          newEventRow.copy(id = Some(id)).toEvent
        } += eventRow
      )
      event <- insertQuery
    } yield event
  }

  def delete(id: Long): Future[Option[Event]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(event: Event): Future[Option[Event]] = {
    event.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            EventRow.fromEvent(event) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }
}
