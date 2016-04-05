package persistence.slick

import _root_.slick.driver.PostgresDriver.api._
import models._
import persistence.ItemsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class ItemRow(id: Option[Long], eventId: Long, itemNumber: String, description: String, minbid: BigDecimal) {
  def toItem: Future[Item] = Events.forId(eventId) map {
    case Some(event) =>
      Item(id, event, itemNumber, description, minbid)
    case None =>
      throw new ItemException(s"Unable to find event ID $eventId to convert item row to item")
  }
}

object ItemRow extends ((Option[Long], Long, String, String, BigDecimal) => ItemRow) {
  def fromItem(item: Item): Future[ItemRow] = Future.successful(ItemRow(item.id, item.event.id.get, item.itemNumber, item.description, item.minbid))

  def toItem(row: ItemRow): Future[Item] = row.toItem
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

object Items extends TableQuery(new Items(_)) with ItemsPersistence with SlickPersistence {

  private val findById = this.findBy(_.id)
  private val findByEventId = this.findBy(_.eventId)
  private val findByItemNumberAndEventId = (itemNumber: String, eventId: Long) =>
    this.filter {
      _.eventId === eventId
    } filter {
      _.itemNumber === itemNumber
    }

  def all(): Future[List[Item]] =
    db.run(this.result.map(mapSeq(ItemRow.toItem))).flatMap(Future.sequence(_))

  def forId(id: Long): Future[Option[Item]] = for {
    optItemRow <- db.run(findById(id).result.headOption)
    futureItem <- optItemRow.map(_.toItem)
    item <- futureItem
  } yield item

  def forEventId(eventId: Long): Future[List[Item]] =
    db.run(findByEventId(eventId).result.map(mapSeq(ItemRow.toItem))).flatMap(Future.sequence(_))

  def forEventIdAndItemNumber(eventId: Long, itemNumber: String): Future[Option[Item]] = for {
    optItemRow <- db.run(findByItemNumberAndEventId(itemNumber, eventId).result.headOption)
    futureItem <- optItemRow.map(_.toItem)
    item <- futureItem
  } yield item

  def create(item: Item): Future[Item] = for {
    itemRow <- ItemRow.fromItem(item)
    insertQuery <- db.run(
      this returning map {
        _.id
      } into {
        (newItemRow, id) => newItemRow.copy(id = Some(id)).toItem
      } += itemRow
    )
    item <- insertQuery
  } yield item

  def delete(id: Long): Future[Option[Item]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(item: Item): Future[Option[Item]] = {
    item.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            ItemRow.fromItem(item) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }
}
