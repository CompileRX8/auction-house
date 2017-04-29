package misc

import javax.inject.{Inject, Singleton}

import actors.AuctionDemoActor
import akka.actor.ActorSystem
import play.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import models.{BidderHandler, ItemHandler}

@Singleton
class Global @Inject()(app: Application, bidderHandler: BidderHandler, itemHandler: ItemHandler, actorSystem: ActorSystem, implicit val ec: ExecutionContext) {

    val loadSuccessful: Future[Boolean] = bidderHandler.loadFromDataSource zip itemHandler.loadFromDataSource map {
      case (bidderSuccess, itemSuccess) =>
        bidderSuccess && itemSuccess
    }
    Await.result(loadSuccessful, Util.defaultAwaitTimeout)

    if(app.mode != Mode.Prod) {
      // Just need to reference this
      val auctionDemoActor = actorSystem.actorOf(AuctionDemoActor.props)
    }
}
