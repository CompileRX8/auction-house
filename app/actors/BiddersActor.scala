package actors

import akka.actor.{Props, Actor}
import models.{BidderException, Item, Payment, Bidder}
import misc.Util
import persistence.anorm.{BiddersPersistenceAnorm, ItemsPersistenceAnorm}
import scala.concurrent.Await
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka

import scala.util.{Failure, Success, Try}

object BiddersActor {
  case object LoadFromDataSource

  case object GetBidders
  case class GetBidder(id: Long)
  case class DeleteBidder(id: Long)
  case class EditBidder(bidder: Bidder)

  case class Payments(bidder: Bidder)

  case class BiddersForEvent(eventId: Long)

  def props = Props(classOf[BiddersActor])

  val biddersActor = Akka.system.actorOf(BiddersActor.props)

  val biddersPersistence = BiddersPersistenceAnorm
  val itemsPersistence = ItemsPersistenceAnorm
}

class BiddersActor extends Actor {
  import BiddersActor._

  private def findBidder(bidder: Bidder): Try[Option[Bidder]] = bidder.id match {
    case Some(id) => findBidder(id)
    case None => findBidder(bidder.contact.name)
  }

  private def findBidder(id: Long): Try[Option[Bidder]] = biddersPersistence.bidderById(id)

  private def findBidder(name: String): Try[Option[Bidder]] = biddersPersistence.bidderByName(name)

  override def receive = {
    case LoadFromDataSource =>
      sender ! biddersPersistence.load(self)

    case GetBidders =>
      sender ! biddersPersistence.sortedBidders

    case GetBidder(id) =>
      sender ! findBidder(id)

    case newBidder @ Bidder(None, event, bidderNumber, contact) =>
      sender ! findBidder(contact.name).flatMap {
        case Some(bidder) => Failure(new BidderException(s"Bidder name ${contact.name} already exists as Bidder Number ${bidder.bidderNumber}"))
        case None => biddersPersistence.create(newBidder)
      }

    case bidder @ Bidder(idOpt @ Some(id), event, bidderNumber, contact) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case DeleteBidder(id) =>
      sender ! findBidder(id).flatMap {
        case Some(bidder) =>
          itemsPersistence.winningBidsByBidder(bidder).flatMap {
            case Nil =>
              biddersPersistence.paymentsByBidder(bidder).flatMap {
                case Nil =>
                  biddersPersistence.delete(bidder)
                case payments =>
                  Failure(new BidderException(s"Cannot delete bidder ${bidder.contact.name} with payments"))
              }
            case winningBids =>
              Failure(new BidderException(s"Cannot delete bidder ${bidder.contact.name} with winning bids"))
          }
        case None =>
          Failure(new BidderException(s"Cannot find bidder ID $id"))
      }

    case EditBidder(bidder @ Bidder(idOpt @ Some(id), name)) =>
      sender ! biddersPersistence.edit(bidder)

    case EditBidder(bidder @ Bidder(None, name)) =>
      sender ! Failure(new BidderException(s"Cannot edit bidder without bidder ID"))

    case newPayment @ Payment(None, bidder, description, amount) =>
      sender ! findBidder(bidder).flatMap {
        case Some(bidderInfo) => biddersPersistence.create(newPayment)
        case None => Failure(new BidderException(s"Cannot find bidder $bidder"))
      }

    case p @ Payment(idOpt @ Some(id), bidder, description, amount) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case Payments(bidder) =>
      sender ! biddersPersistence.paymentsByBidder(bidder)

    case _ =>
  }

}
