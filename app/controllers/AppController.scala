package controllers

import javax.inject.Inject

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.iteratee.{Concurrent, Enumeratee}

import scala.util.Random
import play.api.Logger
import play.api.libs.EventSource
import models._
import play.api.routing.JavaScriptReverseRouter

import scala.concurrent.ExecutionContext

class AppController @Inject()(webJarAssets: WebJarAssets, bidderHandler: BidderHandler, itemHandler: ItemHandler, implicit val ec: ExecutionContext) extends Controller with Secured{

  val logger = Logger(classOf[AppController])

  def index = withAuth {
    username => implicit request =>
      Ok(views.html.app.index(webJarAssets))
  }

  val (biddersOut, biddersChannel) = Concurrent.broadcast[JsValue]
  val (itemsOut, itemsChannel) = Concurrent.broadcast[JsValue]

  /** Enumeratee for detecting disconnect of SSE stream */
  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone{ () => println(addr + " - SSE disconnected") }

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]
  implicit val itemDataFormat = Json.format[ItemData]

  /** Controller action serving bidders */
  def biddersFeed = Action { req =>
    println(req.remoteAddress + " - SSE connected: Bidders")

    Ok.feed(biddersOut
      &> Concurrent.buffer(50)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()
    ).as("text/event-stream")
  }

  def pushBidders = Action.async { req =>
    bidderHandler.currentBidders() map {
      case biddersData =>
        logger.debug(s"biddersData: $biddersData")
        val jsonBiddersData = Json.toJson(biddersData)
        logger.debug(s"jsonBiddersData: $jsonBiddersData")
        biddersChannel.push(jsonBiddersData)
        Ok
    } recover {
      case e =>
        logger.error("Failed to push bidders", e)
        BadRequest(e.getMessage)
    }
  }

  /** Controller action serving items */
  def itemsFeed = Action { req =>
    println(req.remoteAddress + " - SSE connected: Items")

    Ok.feed(itemsOut
      &> Concurrent.buffer(50)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()
    ).as("text/event-stream")
  }

  def pushItems = Action.async { req =>
    itemHandler.currentItems() map {
      case itemsData =>
        logger.debug(s"itemsData: $itemsData")
        val jsonItemsData = Json.toJson(itemsData)
        logger.debug(s"jsonItemsData: $jsonItemsData")
        itemsChannel.push(jsonItemsData)
        Ok
    } recover {
      case e =>
        logger.error("Failed to push items", e)
        BadRequest(e.getMessage)
    }
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AppController.login,
        routes.javascript.AppController.logout,
        routes.javascript.BidderController.newBidder,
        routes.javascript.ItemController.newItem,
        routes.javascript.ItemController.addWinningBid,
        routes.javascript.PaymentController.newPayment
      )
    ).as("text/javascript")
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

