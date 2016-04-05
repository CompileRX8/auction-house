package persistence.slick

import models._
import persistence.BiddersPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import _root_.slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class BidderRow(id: Option[Long], eventId: Long, bidderNumber: String, contactId: Long) {
  def toBidder: Future[Bidder] =
    Events.forId(eventId) flatMap {
      case Some(event) =>
        Contacts.forId(contactId) map {
          case Some(contact) =>
            Bidder(id, event, bidderNumber, contact)
          case None =>
            throw new BidderException(s"Unable to find contact ID $contactId to convert bidder row to bidder")
        }
      case None =>
        throw new BidderException(s"Unable to find event ID $eventId to convert bidder row to bidder")
    }
}

object BidderRow extends ((Option[Long], Long, String, Long) => BidderRow) {
  def fromBidder(bidder: Bidder): Future[BidderRow] = Future.successful(BidderRow(bidder.id, bidder.event.id.get, bidder.bidderNumber, bidder.contact.id.get))

  def toBidder(row: BidderRow): Future[Bidder] = row.toBidder
}

class Bidders(tag: Tag) extends Table[BidderRow](tag, "BIDDER") {
  def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

  def eventId = column[Long]("EVENT_ID")

  def bidderNumber = column[String]("BIDDER_NUMBER")

  def contactId = column[Long]("CONTACT_ID")

  def * = (id.?, eventId, bidderNumber, contactId) <>(BidderRow.tupled, BidderRow.unapply)

  def fkEvent = foreignKey("bidder_event_id_fk", eventId, Events)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)
  def fkContact = foreignKey("bidder_contact_id_fk", contactId, Contacts)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

  def idxEventIdBidderNumber = index("bidder_event_id_bidder_number_idx", (eventId, bidderNumber), unique = true)

  def idxContact = index("bidder_contact_id_idx", contactId)
}

object Bidders extends TableQuery(new Bidders(_)) with BiddersPersistence with SlickPersistence {

  private val findById = this.findBy(_.id)
  private val findByEventId = this.findBy(_.eventId)
  private val findByContactId = this.findBy(_.contactId)
  private val findByNameWithinEventId = (name: String, eventId: Long) =>
    this.join(Contacts) on { case (b, c) =>
      b.contactId === c.id
    } filter { case (b, c) =>
      c.name === name
    } filter { case (b, c) =>
      b.eventId === eventId
    } map { case (b, c) => b }

  def all(): Future[List[Bidder]] = db.run(this.result.map(mapSeq(BidderRow.toBidder))).flatMap(Future.sequence(_))

  def forId(id: Long): Future[Option[Bidder]] = for {
    optBidderRow <- db.run(findById(id).result.headOption)
    futureBidder <- optBidderRow.map(_.toBidder)
    bidder <- futureBidder
  } yield bidder

  def forEventId(eventId: Long): Future[List[Bidder]] =
    db.run(findByEventId(eventId).result.map(mapSeq(BidderRow.toBidder))).flatMap(Future.sequence(_))

  def forContactId(contactId: Long): Future[List[Bidder]] =
    db.run(findByContactId(contactId).result.map(mapSeq(BidderRow.toBidder))).flatMap(Future.sequence(_))

  def forEventIdAndName(eventId: Long, name: String): Future[Option[Bidder]] = for {
    optBidderRow <- db.run(findByNameWithinEventId(name, eventId).result.headOption)
    futureBidder <- optBidderRow.map(_.toBidder)
    bidder <- futureBidder
  } yield bidder

  def create(bidder: Bidder): Future[Bidder] =  for {
    bidderRow <- BidderRow.fromBidder(bidder)
    insertQuery <- db.run(
      this returning map {
        _.id
      } into {
        (newBidderRow, id) => newBidderRow.copy(id = Some(id)).toBidder
      } += bidderRow
    )
    bidder <- insertQuery
  } yield bidder

  def delete(id: Long): Future[Option[Bidder]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(bidder: Bidder): Future[Option[Bidder]] ={
    bidder.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            BidderRow.fromBidder(bidder) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }

}
