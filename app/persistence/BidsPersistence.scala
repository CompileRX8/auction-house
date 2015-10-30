package persistence

import misc.Util
import models._
import persistence.BiddersPersistence.bidders
import persistence.ItemsPersistence.items
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

object BidsPersistence extends SlickPersistence {

  case class BidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
    def toBid: Bid = Util.wait(
      bidders.forId(bidderId) flatMap {
        case Some(bidder) =>
          items.forId(itemId) map {
            case Some(item) =>
              Bid(id, bidder, item, amount)
            case None =>
              throw new BidException(s"Unable to find item ID $itemId to convert bid row to bid")
          }
        case None =>
          throw new BidException(s"Unable to find bidder ID $bidderId to convert bid row to bid")
      }
    )
  }

  object BidRow extends ((Option[Long], Long, Long, BigDecimal) => BidRow) {
    def fromBid(bid: Bid): BidRow =
      BidRow(bid.id, bid.bidder.id.get, bid.item.id.get, bid.amount)

    def toBid(row: BidRow) = row.toBid
  }

  class Bids(tag: Tag) extends Table[BidRow](tag, "BID") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def bidderId = column[Long]("bidder_id")

    def itemId = column[Long]("item_id")

    def amount = column[BigDecimal]("amount")

    def * = (id.?, bidderId, itemId, amount) <>(BidRow.tupled, BidRow.unapply)

    def fkBidder = foreignKey("winningbid_bidder_id_fk", bidderId, bidders)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Restrict)

    def fkItem = foreignKey("winningbid_item_id_fk", itemId, items)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Restrict)

    def idxItemIdBidderId = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId))

    def idxBidderIdItemId = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId))

    def idxBidderIdItemIdAmount = index("bid_bidder_id_item_id_amount_idx", (bidderId, itemId, amount), unique = true)
  }

  object bids extends TableQuery(new Bids(_)) {
    private val findById = this.findBy(_.id)
    private val findByItemId = this.findBy(_.itemId)
    private val findByBidderId = this.findBy(_.bidderId)
    private val findByEventId = (eventId: Long) =>
      this.join(items) on { case (b, i) =>
        b.itemId === i.id
      } filter { case (_, i) =>
        i.eventId === eventId
      } map { case (b, _) => b }
    private val findByBidderIdWithinEventId = (bidderId: Long, eventId: Long) =>
      findByEventId(eventId).join(bidders) on { case (bids, bidders) =>
        bidders.id === bidderId
      } map { case (bids, _) =>
        bids
      }

    def forId(id: Long): Future[Option[Bid]] =
      db.run(findById(id).result.headOption.map(mapOption(BidRow.toBid)))

    def forEventId(eventId: Long): Future[List[Bid]] =
      db.run(findByEventId(eventId).result.map(mapSeq(BidRow.toBid)))

    def forItemId(itemId: Long): Future[List[Bid]] =
      db.run(findByItemId(itemId).result.map(mapSeq(BidRow.toBid)))

    def forBidderId(bidderId: Long): Future[List[Bid]] =
      db.run(findByBidderId(bidderId).result.map(mapSeq(BidRow.toBid)))

    def forEventIdAndBidderId(eventId: Long, bidderId: Long): Future[List[Bid]] =
      db.run(findByBidderIdWithinEventId(bidderId, eventId).result.map(mapSeq(BidRow.toBid)))

    def create(bid: Bid): Future[Option[Bid]] =
      db.run(
        this returning map {
          _.id
        } into { (newBidRow, id) =>
          Some(newBidRow.copy(id = Some(id)).toBid)
        } += BidRow.fromBid(bid)
      )

    def delete(id: Long): Future[Option[Bid]] =
      deleteHandler(id, forId) {
        findById(id).delete
      }

    def edit(bid: Bid): Future[Option[Bid]] =
      editHandler(bid.id, bid, forId) { id =>
        findById(id).update(BidRow.fromBid(bid))
      }
  }

}
