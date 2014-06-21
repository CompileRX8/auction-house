package controllers

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumeratee, Concurrent}

import scala.util.Random
import play.api.Routes
import play.api.libs.EventSource
import models.{Bidder, Item}

object AppController extends Controller with Secured{

  def index = withAuth {
    username => implicit request =>
      Ok(views.html.app.index())
  }

  val (biddersOut, biddersChannel) = Concurrent.broadcast[JsValue]
  val (itemsOut, itemsChannel) = Concurrent.broadcast[JsValue]

  /** Enumeratee for detecting disconnect of SSE stream */
  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone{ () => println(addr + " - SSE disconnected") }

  /** Controller action serving bidders */
  def biddersFeed = Action { req =>
    println(req.remoteAddress + " - SSE connected: Bidders")
    Bidder.updateBidders()
    Ok.feed(biddersOut
      &> Concurrent.buffer(50)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()
    ).as("text/event-stream")
  }

  /** Controller action serving items */
  def itemsFeed = Action { req =>
    println(req.remoteAddress + " - SSE connected: Items")
    Item.updateItems()
    Ok.feed(itemsOut
      &> Concurrent.buffer(50)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()
    ).as("text/event-stream")
  }

  def javascriptRoutes = Action {
    implicit request =>
      Ok(
        Routes.javascriptRouter("jsRoutes")(
          routes.javascript.AppController.login,
          routes.javascript.AppController.logout,
          routes.javascript.BidderController.newBidder,
          routes.javascript.ItemController.newItem,
          routes.javascript.ItemController.addWinningBid,
          routes.javascript.PaymentController.newPayment
        )
      ).as(JAVASCRIPT)
  }

  private var tokens = Map[String, JsValue]()

  /**
   * Log-in a user. Pass the credentials as JSON body.
   * @return The token needed for subsequent requests
   */
  def login() = Action(parse.json) { implicit request =>
    (request.session.get(Security.username) flatMap { sessionToken: String =>
      tokens.get(sessionToken) map { userObj =>
        Ok(userObj)
      }
    }).getOrElse {
      val email = (request.body \ "email").as[String]
      val password = (request.body \ "password").as[String]

      val token = java.util.UUID.randomUUID().toString
      val userObj = Json.obj("token" -> token, "firstName" -> "Ryan", "lastName" -> "Highley", "age" -> 41, "email" -> email)
      tokens += ( token -> userObj )

      println(s"userObj: $userObj")

      Ok(userObj).withSession(Security.username -> token)
    }
  }

  /** Logs the user out, i.e. invalidated the token. */
  def logout() = Action(parse.json) { implicit request =>
    request.session.get(Security.username) map { sessionToken =>
      val token = (request.body \ "token").as[String]
      println(s"token: $token sessionToken: $sessionToken")
      tokens -= token
    }

    Ok.withNewSession
  }

}

trait Secured {
  def username(request: RequestHeader) = {
    //verify or create session, this should be a real login
    request.session.get(Security.username) 
  }

  def unauthF(request: RequestHeader) = {
    // TODO: This is where to implement the list of valid users and devices
    val newId: String = new Random().nextInt().toString
    Redirect(routes.AppController.index).withSession(Security.username -> newId)
  }

  def withAuth(f: => String => Request[_ >: AnyContent] => Result): EssentialAction = {
    Security.Authenticated(username, unauthF) {
      username =>
        Action(request => f(username)(request))
    }
  }
}

