package misc

import actors.AuctionDemoActor
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{Item, Bidder}


object Global extends GlobalSettings {

  override def onStart(app: Application) = {
    val loadSuccessful = Bidder.loadFromDataSource flatMap { bidderSuccess =>
      Item.loadFromDataSource map { itemSuccess =>
        bidderSuccess && itemSuccess
      }
    }
    Await.result(loadSuccessful, Util.defaultAwaitTimeout)

//    if(app.mode != Mode.Prod) {
      // Just need to reference this
//      val auctionDemoActor = AuctionDemoActor.auctionDemoActor
//    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    println("HEY!!! Can't find this: " + request.path)
    Future(Redirect(controllers.routes.AppController.index))
  }

  override def onStop(app: Application) = {
  }

}
