package actors

import akka.actor.{Actor, Props}
import models.{Bidder, BidderException}
import persistence.BiddersPersistence.bidders
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object BiddersActor {
  case object LoadFromDataSource

  case object GetBidders
  case class GetBidder(id: Long)
  case class DeleteBidder(id: Long)
  case class EditBidder(bidder: Bidder)

  case class BiddersForEvent(eventId: Long)

  def props = Props(classOf[BiddersActor])

  val biddersActor = Akka.system.actorOf(BiddersActor.props)
}

class BiddersActor extends Actor {
  import BiddersActor._

  private def findBidder(bidder: Bidder): Future[Option[Bidder]] = bidder.id match {
    case Some(id) => findBidder(id)
    case None => findBidder(bidder.event.id.get, bidder.contact.name)
  }

  private def findBidder(id: Long): Future[Option[Bidder]] = bidders.forId(id)

  private def findBidder(eventId: Long, name: String): Future[Option[Bidder]] = bidders.forEventIdAndName(eventId, name)

  override def receive = {
    case LoadFromDataSource =>
      //sender ! biddersPersistence.load(self)

    case GetBidders =>
      bidders.all onComplete { sender ! _ }

    case GetBidder(id) =>
      bidders.forId(id) onComplete { sender ! _ }

    case newBidder @ Bidder(None, event, bidderNumber, contact) =>
      findBidder(newBidder) andThen {
        case Success(Some(bidder)) =>
          sender ! Failure(new BidderException(s"Bidder ${bidder.contact.name} at event ID ${event.id.get} already exists as Bidder Number ${bidder.bidderNumber}"))
        case Success(None) =>
          bidders.create(newBidder) onComplete { sender ! _ }
        case f @ Failure(_) => sender ! f
      }

    case bidder @ Bidder(idOpt @ Some(id), event, bidderNumber, contact) =>

    case DeleteBidder(id) =>
      bidders.delete(id) onComplete { sender ! _ }

    case EditBidder(bidder @ Bidder(idOpt @ Some(id), event, bidderNumber, contact)) =>
      bidders.edit(bidder) onComplete { sender ! _ }

    case EditBidder(bidder @ Bidder(None, event, bidderNumber, contact)) =>
      sender ! Failure(new BidderException(s"Cannot edit bidder without bidder ID"))

    case msg @ _ => sender ! Failure(new BidderException(s"Unknown message: $msg"))
  }

}
