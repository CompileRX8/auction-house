package misc

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
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future(Redirect(controllers.routes.AppController.index))
  }

  override def onStop(app: Application) = {
  }

}
