package actors

import akka.actor.{Actor, Props}
import play.api.libs.concurrent.Akka

object AuctionDemoActor {
  import scala.language.postfixOps
  import scala.concurrent.duration._

  case object AuctionAction

  def props = Props(classOf[AuctionDemoActor])

  val auctionDemoActor = Akka.system.actorOf(AuctionDemoActor.props)

  val actionSchedule = Akka.system.scheduler.schedule(10 seconds, 10 seconds, auctionDemoActor, AuctionAction)
}

class AuctionDemoActor extends Actor {
  import AuctionDemoActor._

  override def receive = {
    case AuctionAction =>
    // Determine random action and send action message to self
    // Track return values for future actions,
    // e.g. add winning bids for items and bidders already created
  }
}
