package actors

import javax.inject.Inject

import akka.actor.{Actor, Props}
import models.{Bidder, BidderException, Payment}
import persistence.{BiddersPersistence, ItemsPersistence}
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.util.Failure

object BiddersActor {

  case object LoadFromDataSource

  case object GetBidders

  case class GetBidder(id: Long)

  case class DeleteBidder(id: Long)

  case class EditBidder(bidder: Bidder)

  case class Payments(bidder: Bidder)

  def props = Props[BiddersActor]

  //  val biddersActor = Akka.system.actorOf(BiddersActor.props)
}


class BiddersActor @Inject()(biddersPersistence: BiddersPersistence, itemsPersistence: ItemsPersistence) extends Actor {

  import BiddersActor._

  private def findBidder(bidder: Bidder): Future[Option[Bidder]] = bidder.id match {
    case Some(id) => findBidder(id)
    case None => findBidder(bidder.name)
  }

  private def findBidder(id: Long): Future[Option[Bidder]] = biddersPersistence.bidderById(id)

  private def findBidder(name: String): Future[Option[Bidder]] = biddersPersistence.bidderByName(name)

  override def receive = {
    case LoadFromDataSource =>
      biddersPersistence.load(self) onComplete {
        sender ! _
      }

    case GetBidders =>
      biddersPersistence.sortedBidders onComplete {
        sender ! _
      }

    case GetBidder(id) =>
      findBidder(id) onComplete {
        sender ! _
      }

    case newBidder@Bidder(None, name) =>
      findBidder(name).flatMap {
        case Some(bidder) => Future.failed(new BidderException(s"Bidder name $name already exists as ID ${bidder.id.get}"))
        case None => biddersPersistence.create(newBidder)
      } onComplete {
        sender ! _
      }

    case bidder@Bidder(idOpt@Some(id), name) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case DeleteBidder(id) =>
      findBidder(id) flatMap {
        case Some(bidder) =>
          itemsPersistence.winningBidsByBidder(bidder) flatMap {
            case Nil =>
              biddersPersistence.paymentsByBidder(bidder) flatMap {
                case Nil =>
                  biddersPersistence.delete(bidder)
                case payments =>
                  Future.failed(new BidderException(s"Cannot delete bidder ${bidder.name} with payments"))
              }
            case winningBids =>
              Future.failed(new BidderException(s"Cannot delete bidder ${bidder.name} with winning bids"))
          }
        case None =>
          Future.failed(new BidderException(s"Cannot find bidder ID $id"))
      } onComplete {
        sender ! _
      }

    case EditBidder(bidder@Bidder(idOpt@Some(id), name)) =>
      biddersPersistence.edit(bidder) onComplete {
        sender ! _
      }

    case EditBidder(bidder@Bidder(None, name)) =>
      sender ! Failure(new BidderException(s"Cannot edit bidder without bidder ID"))

    case newPayment@Payment(None, bidder, description, amount) =>
      findBidder(bidder).flatMap {
        case Some(bidderInfo) => biddersPersistence.create(newPayment)
        case None => Future.failed(new BidderException(s"Cannot find bidder $bidder"))
      } onComplete {
        sender ! _
      }

    case p@Payment(idOpt@Some(id), bidder, description, amount) =>
    // Do nothing since not maintaining our own Set[BidderInfo] anymore

    case Payments(bidder) =>
      biddersPersistence.paymentsByBidder(bidder) onComplete {
        sender ! _
      }

    case _ =>
  }

}
