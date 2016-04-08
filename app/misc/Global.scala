package misc

import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import models.{BidderService, ItemService}
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.{Await, Future}

@Singleton
class Global @Inject()(bidderService: BidderService, itemService: ItemService, @Named("auction-demo-actor") auctionDemoActor: ActorRef) extends GlobalSettings {

  override def onStart(app: Application) = {
    val loadSuccessful = bidderService.loadFromDataSource flatMap { triedBidder =>
      itemService.loadFromDataSource map { triedItem =>
        triedBidder flatMap { bidderSuccess =>
          triedItem map { itemSuccess => bidderSuccess && itemSuccess }
        }
      }
    }
    Await.result(loadSuccessful, Util.defaultAwaitTimeout)

//    if(app.mode != Mode.Prod) {
//val actionSchedule = Akka.system.scheduler.schedule(10 seconds, 10 seconds, auctionDemoActor, AuctionAction)
//    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    println("HEY!!! Can't find this: " + request.path)
    Future(Redirect(controllers.routes.AppController.index))
  }

  override def onStop(app: Application) = {
  }

}
