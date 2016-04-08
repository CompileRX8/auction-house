package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc.{Action, Controller}
import models._
import play.api.libs.json.Json

import scala.util.{Failure, Success}

class BidderController @Inject()(bidderService: BidderService, itemService: ItemService, appController: AppController) extends Controller {

  val logger = Logger(classOf[BidderController])

  implicit val itemFormat = Json.format[Item]
  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders = Action { implicit request =>
    bidderService.currentBidders() match {
      case Success(bidders) =>
        Ok(Json.toJson(bidders))
      case Failure(e: BidderException) =>
        logger.error("Unable to send bidders", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to send bidders", e)
        BadRequest(e.getMessage)
    }
  }

  def newBidder = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    bidderService.create(name) match {
      case Success(bidder) =>
        appController.pushBidders()
        Ok(s"Created bidder ${bidder.id.get} ${bidder.name}")
      case Failure(e: BidderException) =>
        logger.error("Unable to create bidder", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to create bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def editBidder(bidderId: Long) = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    bidderService.edit(bidderId, name) match {
      case Success(bidder) =>
        appController.pushBidders()
        Ok(s"Edited bidder ${bidder.id.get} ${bidder.name}")
      case Failure(e: BidderException) =>
        logger.error("Unable to edit bidder", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to edit bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteBidder(bidderId: Long) = Action(parse.json) { implicit request =>
    bidderService.delete(bidderId) match {
      case Success(bidder) =>
        appController.pushBidders()
        Ok(s"Deleted bidder ${bidder.id.get} ${bidder.name}")
      case Failure(e: BidderException) =>
        logger.error("Unable to delete bidder", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to delete bidder", e)
        BadRequest(e.getMessage)
    }
  }
}
