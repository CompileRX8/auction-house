package controllers

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumeratee, Concurrent}
import play.twirl.api.JavaScript

import scala.util.{Failure, Success, Random}
import play.api.{Logger, Routes}
import play.api.libs.EventSource
import models._

object AppController extends Controller with Secured{

  val logger = Logger(AppController.getClass)

  def index = withAuth {
    username => implicit request =>
      Ok(views.html.app.index())
  }

  val (biddersOut, biddersChannel) = Concurrent.broadcast[JsValue]
  val (itemsOut, itemsChannel) = Concurrent.broadcast[JsValue]
  val (organizationsOut, organizationsChannel) = Concurrent.broadcast[JsValue]

  /** Enumeratee for detecting disconnect of SSE stream */
  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone{ () => println(addr + " - SSE disconnected") }

  implicit val organizationFormat = Json.format[Organization]
  implicit val contactFormat = Json.format[Contact]
  implicit val eventFormat = Json.format[Event]
  implicit val organizationDataFormat = Json.format[OrganizationData]
  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[Bid]
  implicit val bidderDataFormat = Json.format[BidderData]
  implicit val itemFormat = Json.format[Item]
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
    Bidder.currentBidders(eventId) map { biddersData =>
      logger.debug(s"biddersData: $biddersData")
      val jsonBiddersData = Json.toJson(biddersData)
      logger.debug(s"jsonBiddersData: $jsonBiddersData")
      biddersChannel.push(jsonBiddersData)
      Ok
    } recover {
      case e @ Throwable =>
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
    Item.currentItems(eventId) map { itemsData =>
      logger.debug(s"itemsData: $itemsData")
      val jsonItemsData = Json.toJson(itemsData)
      logger.debug(s"jsonItemsData: $jsonItemsData")
      itemsChannel.push(jsonItemsData)
      Ok
    } recover {
      case e @ Throwable =>
        logger.error("Failed to push items", e)
        BadRequest(e.getMessage)
    }
  }

  def organizationsFeed = Action { req =>
    println(req.remoteAddress + " - SSE connected: Organizations")

    Ok.feed(organizationsOut
      &> Concurrent.buffer(50)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()
    ).as("text/event-stream")
  }

  def pushOrganizations = Action.async { req =>
    Organization.currentOrganizations() map { orgsData =>
      logger.debug(s"orgsData: $orgsData")
      val jsonOrgsData = Json.toJson(orgsData)
      logger.debug(s"jsonOrgsData: $jsonOrgsData")
      organizationsChannel.push(jsonOrgsData)
      Ok
    } recover {
      case e @ Throwable =>
        logger.error("Failed to push organizations", e)
        BadRequest(e.getMessage)
    }
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
      val userObj = Json.obj("token" -> token, "firstName" -> "Ryan", "lastName" -> "Highley", "age" -> 42, "email" -> email)
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

