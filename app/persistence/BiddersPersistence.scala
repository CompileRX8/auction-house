package persistence

import misc.Util
import models._
import persistence.ContactsPersistence.contacts
import persistence.EventsPersistence.events
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object BiddersPersistence extends SlickPersistence {

  case class BidderRow(id: Option[Long], eventId: Long, bidderNumber: String, contactId: Long) {
    def toBidder: Bidder = Util.wait(
      events.forId(eventId) flatMap {
        case Some(event) =>
          contacts.forId(contactId) map {
            case Some(contact) =>
              Bidder(id, event, bidderNumber, contact)
            case None =>
              throw new BidderException(s"Unable to find contact ID $contactId to convert bidder row to bidder")
          }
        case None =>
          throw new BidderException(s"Unable to find event ID $eventId to convert bidder row to bidder")
      }
    )
  }

  object BidderRow extends ((Option[Long], Long, String, Long) => BidderRow) {
    def fromBidder(bidder: Bidder) = BidderRow(bidder.id, bidder.event.id.get, bidder.bidderNumber, bidder.contact.id.get)

    def toBidder(row: BidderRow) = row.toBidder
  }

  class Bidders(tag: Tag) extends Table[BidderRow](tag, "BIDDER") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def eventId = column[Long]("EVENT_ID")

    def bidderNumber = column[String]("BIDDER_NUMBER")

    def contactId = column[Long]("CONTACT_ID")

    def * = (id.?, eventId, bidderNumber, contactId) <>(BidderRow.tupled, BidderRow.unapply)

    def fkEvent = foreignKey("bidder_event_id_fk", eventId, events)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Cascade)

    def idxEventIdBidderNumber = index("bidder_event_id_bidder_number_idx", (eventId, bidderNumber), unique = true)

    def idxContact = index("bidder_contact_id_idx", contactId)
  }

  object bidders extends TableQuery(new Bidders(_)) {
    private val findById = this.findBy(_.id)
    private val findByEventId = this.findBy(_.eventId)
    private val findByContactId = this.findBy(_.contactId)
    private val findByNameWithinEventId = (name: String, eventId: Long) =>
      this.join(contacts) on { case (b, c) =>
        b.contactId === c.id
      } filter { case (b, c) =>
        c.name === name
      } filter { case (b, c) =>
        b.eventId === eventId
      } map { case (b, c) => b }

    def all(): Future[List[Bidder]] =
      db.run(this.result.map(mapSeq(BidderRow.toBidder)))

    def forId(id: Long): Future[Option[Bidder]] =
      db.run(findById(id).result.headOption.map(mapOption(BidderRow.toBidder)))

    def forEventId(eventId: Long): Future[List[Bidder]] =
      db.run(findByEventId(eventId).result.map(mapSeq(BidderRow.toBidder)))

    def forContactId(contactId: Long): Future[List[Bidder]] =
      db.run(findByContactId(contactId).result.map(mapSeq(BidderRow.toBidder)))

    def forEventIdAndName(eventId: Long, name: String): Future[Option[Bidder]] =
      db.run(findByNameWithinEventId(name, eventId).result.headOption.map(mapOption(BidderRow.toBidder)))

    def create(bidder: Bidder): Future[Bidder] = {
//      val btr = this.baseTableRow
      val idMapper = (b: Bidders) => {
        b.id
      }
//      val idMapperShape = btr.id.shaped.shape

      val rowMapper: (BidderRow, Long) => Bidder = { (newBidderRow, id) =>
        newBidderRow.copy(id = Some(id)).toBidder
      }

      val domainMapper: (Bidder) => BidderRow = BidderRow.fromBidder

      val createAction =
        this returning map(idMapper) into rowMapper += domainMapper(bidder)

      db.run(createAction)
      //createHandler[Bidder, BidderRow](bidder, this, idMapper, idMapperShape)(rowMapper)(domainMapper)
    }

    def delete(id: Long): Future[Option[Bidder]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(bidder: Bidder): Future[Option[Bidder]] =
      editHandler(bidder.id, bidder, forId) { id =>
        findById(id).update(BidderRow.fromBidder(bidder))
      }
  }

}
