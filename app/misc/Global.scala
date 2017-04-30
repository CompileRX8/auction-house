package misc

import javax.inject.{Inject, Named, Singleton}

import actors.AuctionDemoActor
import akka.actor.ActorRef
import play.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import models.{BidderHandler, ItemHandler}

@Singleton
class Global @Inject()(app: Application, bidderHandler: BidderHandler, itemHandler: ItemHandler, @Named("auctionDemoActor") auctionDemoActor: ActorRef, implicit val ec: ExecutionContext) {

    val loadSuccessful: Future[Boolean] = bidderHandler.loadFromDataSource zip itemHandler.loadFromDataSource map {
      case (bidderSuccess, itemSuccess) =>
        bidderSuccess && itemSuccess
    }
    Await.result(loadSuccessful, Util.defaultAwaitTimeout)

    if(app.mode != Mode.Prod) {
//      auctionDemoActor ! AuctionDemoActor.StartMeUp
    }
}
