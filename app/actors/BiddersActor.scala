package actors

import javax.inject.Inject

import akka.actor.{Actor, ActorRef}
import models.{Bidder, BidderException, Payment}
import persistence.{BiddersPersistence, ItemsPersistence}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

object BiddersActor {
  case object LoadFromDataSource

  case object GetBidders
  case class GetBidder(id: Long)
  case class DeleteBidder(id: Long)
  case class EditBidder(bidder: Bidder)

  case class Payments(bidder: Bidder)
}

class BiddersActor @Inject()(biddersPersistence: BiddersPersistence, itemsPersistence: ItemsPersistence) extends Actor {
  import BiddersActor._

  private def findBidder(bidder: Bidder): Future[Option[Bidder]] = bidder.id match {
    case Some(id) => findBidder(id)
    case None => findBidder(bidder.name)
  }

  private def findBidder(id: Long): Future[Option[Bidder]] = biddersPersistence.bidderById(id)

  private def findBidder(name: String): Future[Option[Bidder]] = biddersPersistence.bidderByName(name)

  private def tryOrSendFailure(sender: ActorRef)(f: (ActorRef) => Unit) = {
    try {
      f(sender)
    } catch {
      case e: Exception =>
        sender ! akka.actor.Status.Failure(e)
        throw e
    }
  }

  override def receive = {
/*    case LoadFromDataSource =>
      tryOrSendFailure(sender) { s =>
        s ! biddersPersistence.load(self)
      }

    case GetBidders => tryOrSendFailure(sender) { s =>
      s ! biddersPersistence.sortedBidders
    }

    case GetBidder(id) => tryOrSendFailure(sender) { s =>
      s ! findBidder(id)
    }

    case newBidder @ Bidder(None, name) => tryOrSendFailure(sender) { s =>
      s ! findBidder(name).map {
        case Some(bidder) => Failure(BidderException(s"Bidder name $name already exists as ID ${bidder.id.get}"))
        case None => biddersPersistence.create(newBidder)
      }
    }

    case bidder @ Bidder(idOpt @ Some(id), name) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case DeleteBidder(id) => tryOrSendFailure(sender) { s =>
      s ! findBidder(id).map {
        case Some(bidder) =>
          itemsPersistence.winningBidsByBidder(bidder).map {
            case Nil =>
              biddersPersistence.paymentsByBidder(bidder).map {
                case Nil =>
                  biddersPersistence.delete(bidder)
                case payments =>
                  Failure(BidderException(s"Cannot delete bidder ${bidder.name} with payments"))
              }
            case winningBids =>
              Failure(BidderException(s"Cannot delete bidder ${bidder.name} with winning bids"))
          }
        case None =>
          Failure(BidderException(s"Cannot find bidder ID $id"))
      }
    }

    case EditBidder(bidder @ Bidder(idOpt @ Some(id), name)) => tryOrSendFailure(sender) { s =>
      s ! biddersPersistence.edit(bidder)
    }

    case EditBidder(bidder @ Bidder(None, name)) =>
      sender ! Failure(BidderException(s"Cannot edit bidder without bidder ID"))

    case newPayment @ Payment(None, bidder, description, amount) => tryOrSendFailure(sender) { s =>
      s ! findBidder(bidder).map {
        case Some(bidderInfo) => biddersPersistence.create(newPayment)
        case None => Failure(BidderException(s"Cannot find bidder $bidder"))
      }
    }

    case p @ Payment(idOpt @ Some(id), bidder, description, amount) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case Payments(bidder) => tryOrSendFailure(sender) { s =>
      s ! biddersPersistence.paymentsByBidder(bidder)
    }
*/
    case _ =>
  }

}
