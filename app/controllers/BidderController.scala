package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{BidderData, WinningBid, Payment, Bidder}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import misc.Util
import play.api.libs.json.Json

object BidderController extends Controller {

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders = Action { implicit request =>
    Ok(Json.toJson(Bidder.updateBidders()))
  }

  def newBidder = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    Bidder.create(name)
    AppController.pushBidders()
    Ok("")
  }

  def deleteBidder(bidderId: Long) = TODO
}
