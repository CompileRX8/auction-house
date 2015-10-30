package persistence

import misc.Util
import models._
import persistence.EventsPersistence.events
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object ItemsPersistence extends SlickPersistence {

  case class ItemRow(id: Option[Long], eventId: Long, itemNumber: String, description: String, minbid: BigDecimal) {
    def toItem: Item = Util.wait(events.forId(eventId) map {
      case Some(event) =>
        Item(id, event, itemNumber, description, minbid)
      case None =>
        throw new ItemException(s"Unable to find event ID $eventId to convert item row to item")
    })
  }

  object ItemRow extends ((Option[Long], Long, String, String, BigDecimal) => ItemRow) {
    def fromItem(item: Item) = ItemRow(item.id, item.event.id.get, item.itemNumber, item.description, item.minbid)

    def toItem(row: ItemRow) = row.toItem
  }

  class Items(tag: Tag) extends Table[ItemRow](tag, "item") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def eventId = column[Long]("event_id")

    def itemNumber = column[String]("item_number")

    def description = column[String]("description")

    def minbid = column[BigDecimal]("minbid")

    def * = (id.?, eventId, itemNumber, description, minbid) <>(ItemRow.tupled, ItemRow.unapply)

    def idxEvent = index("item_event_id_idx", eventId)

    def idxEventIdItemNumber = index("item_event_id_item_number_idx", (eventId, itemNumber), unique = true)
  }

  object items extends TableQuery(new Items(_)) {
    private val findById = this.findBy(_.id)
    private val findByEventId = this.findBy(_.eventId)
    private val findByItemNumberAndEventId = (itemNumber: String, eventId: Long) =>
      this.filter {
        _.eventId === eventId
      } filter {
        _.itemNumber === itemNumber
      }

    def all(): Future[List[Item]] =
      db.run(this.result.map(mapSeq(ItemRow.toItem)))

    def forId(id: Long): Future[Option[Item]] =
      db.run(findById(id).result.headOption.map(mapOption(ItemRow.toItem)))

    def forEventId(eventId: Long): Future[List[Item]] =
      db.run(findByEventId(eventId).result.map(mapSeq(ItemRow.toItem)))

    def forEventIdAndItemNumber(eventId: Long, itemNumber: String): Future[Option[Item]] =
      db.run(findByItemNumberAndEventId(itemNumber, eventId).result.headOption.map(mapOption(ItemRow.toItem)))

    def create(item: Item): Future[Item] =
      db.run(
        this returning map {
          _.id
        } into {
          (newItemRow, id) => newItemRow.copy(id = Some(id)).toItem
        } += ItemRow.fromItem(item)
      )

    def delete(id: Long): Future[Option[Item]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(item: Item): Future[Option[Item]] =
      editHandler(item.id, item, forId) { id =>
        findById(id).update(ItemRow.fromItem(item))
      }
  }

}
