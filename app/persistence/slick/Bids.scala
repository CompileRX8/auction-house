package persistence.slick

import _root_.slick.driver.PostgresDriver.api._
import models._
import persistence.BidsPersistence
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

case class BidRow(id: Option[Long], bidderId: Long, itemId: Long, amount: BigDecimal) {
  def toBid: Future[Bid] =
    Bidders.forId(bidderId) flatMap {
      case Some(bidder) =>
        Items.forId(itemId) map {
          case Some(item) =>
            Bid(id, bidder, item, amount)
          case None =>
            throw new BidException(s"Unable to find item ID $itemId to convert bid row to bid")
        }
      case None =>
        throw new BidException(s"Unable to find bidder ID $bidderId to convert bid row to bid")
    }
}

object BidRow extends ((Option[Long], Long, Long, BigDecimal) => BidRow) {
  def fromBid(bid: Bid): Future[BidRow] =
    Future.successful(BidRow(bid.id, bid.bidder.id.get, bid.item.id.get, bid.amount))

  def toBid(row: BidRow): Future[Bid] = row.toBid
}

class Bids(tag: Tag) extends Table[BidRow](tag, "BID") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def bidderId = column[Long]("bidder_id")

  def itemId = column[Long]("item_id")

  def amount = column[BigDecimal]("amount")

  def * = (id.?, bidderId, itemId, amount) <>(BidRow.tupled, BidRow.unapply)

  def fkBidder = foreignKey("winningbid_bidder_id_fk", bidderId, Bidders)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Restrict)

  def fkItem = foreignKey("winningbid_item_id_fk", itemId, Items)(_.id, ForeignKeyAction.Restrict, ForeignKeyAction.Restrict)

  def idxItemIdBidderId = index("winningbid_item_id_bidder_id_idx", (itemId, bidderId))

  def idxBidderIdItemId = index("winningbid_bidder_id_item_id_idx", (bidderId, itemId))

  def idxBidderIdItemIdAmount = index("bid_bidder_id_item_id_amount_idx", (bidderId, itemId, amount), unique = true)
}

object Bids extends TableQuery(new Bids(_)) with BidsPersistence with SlickPersistence {
  private val findById = this.findBy(_.id)
  private val findByItemId = this.findBy(_.itemId)
  private val findByBidderId = this.findBy(_.bidderId)
  private val findByEventId = (eventId: Long) =>
    this.join(Items) on { case (b, i) =>
      b.itemId === i.id
    } filter { case (_, i) =>
      i.eventId === eventId
    } map { case (b, _) => b }
  private val findByBidderIdWithinEventId = (bidderId: Long, eventId: Long) =>
    findByEventId(eventId).join(Bidders) on { case (bids, bidders) =>
      bidders.id === bidderId
    } map { case (bids, _) =>
      bids
    }

  def forId(id: Long): Future[Option[Bid]] = for {
    optFutureBidRow <- db.run(findById(id).result.headOption)
    futureBid <- optFutureBidRow.map(_.toBid)
    bid <- futureBid
  } yield bid

  def forEventId(eventId: Long): Future[List[Bid]] =
    db.run(findByEventId(eventId).result.map(mapSeq(BidRow.toBid))).flatMap(Future.sequence(_))

  def forItemId(itemId: Long): Future[List[Bid]] =
    db.run(findByItemId(itemId).result.map(mapSeq(BidRow.toBid))).flatMap(Future.sequence(_))

  def forBidderId(bidderId: Long): Future[List[Bid]] =
    db.run(findByBidderId(bidderId).result.map(mapSeq(BidRow.toBid))).flatMap(Future.sequence(_))

  def forEventIdAndBidderId(eventId: Long, bidderId: Long): Future[List[Bid]] =
    db.run(findByBidderIdWithinEventId(bidderId, eventId).result.map(mapSeq(BidRow.toBid))).flatMap(Future.sequence(_))

  def create(bid: Bid): Future[Option[Bid]] = for {
    bidRow <- BidRow.fromBid(bid)
    insertQuery <- db.run(
      this returning map {
        _.id
      } into { (newBidRow, id) =>
        Some(newBidRow.copy(id = Some(id)).toBid)
      } += bidRow
    )
    bid <- insertQuery
  } yield bid

  def delete(id: Long): Future[Option[Bid]] =
    deleteHandler(id, forId) {
      findById(id).delete
    }

  def edit(bid: Bid): Future[Option[Bid]] = {
    bid.id match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            BidRow.fromBid(bid) flatMap { row =>
              db.run(findById(id).update(row))
            }
            opt
          case None => None
        }
      case None => Future(None)
    }
  }

}
