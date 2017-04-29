package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Controller, Result}
import models._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.ExecutionContext

class BidderController @Inject()(appController: AppController, bidderHandler: BidderHandler, implicit val ec: ExecutionContext) extends Controller {

  val logger = Logger(classOf[BidderController])

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders: Action[AnyContent] = Action.async { implicit request =>
    bidderHandler.currentBidders() map {
      bidders => Ok(Json.toJson(bidders))
    } recover exceptionAction("send")
  }

  def newBidder: Action[JsValue] = Action.async(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    bidderHandler.create(name) map bidderAction("Created bidder") recover exceptionAction("create")
  }

  def editBidder(bidderId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    bidderHandler.edit(bidderId, name) map bidderAction("Edited bidder") recover exceptionAction("edit")
  }

  def deleteBidder(bidderId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    bidderHandler.delete(bidderId) map bidderAction("Deleted bidder") recover exceptionAction("delete")
  }

  private def bidderAction(msg: String) = { bidder: Bidder =>
    appController.pushBidders()
    Ok(s"$msg ${bidder.id.get} ${bidder.name}")
  }

  private def exceptionAction(msg: String): PartialFunction[Throwable, Result] = {
    case e: BidderException =>
      logger.error(s"Unable to $msg bidder", e)
      BadRequest(e.message)
    case e: Exception =>
      logger.error(s"Unable to $msg bidder", e)
      BadRequest(e.getMessage)
  }
}
