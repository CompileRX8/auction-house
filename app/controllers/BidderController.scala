package controllers

import play.api.Logger
import play.api.mvc.{Action, Controller}
import models._
import play.api.libs.json.Json

import scala.util.{Failure, Success}

object BidderController extends Controller with Secured {

  val logger = Logger(BidderController.getClass)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[Bid]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders = Action { implicit request =>
    Bidder.currentBidders(eventId) match {
      case Success(bidders) =>
        Ok(Json.toJson(bidders))
      case Failure(e) =>
        logger.error("Unable to send bidders", e)
        BadRequest(e.getMessage)
    }
  }

  def newBidder = Action(parse.json) { implicit request =>
    val bidderNumber = (request.body \ "bidderNumber").as[String]
    val contactId = (request.body \ "contactId").as[Long]
    Bidder.create(eventId, bidderNumber, contactId) match {
      case Success(bidder) =>
        AppController.pushBidders()
        Ok(s"Created bidder ${bidder.bidderNumber} ${bidder.contact.name}")
      case Failure(e) =>
        logger.error("Unable to create bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def editBidder(bidderId: Long) = Action(parse.json) { implicit request =>
    val bidderNumber = (request.body \ "bidderNumber").as[String]
    val contactId = (request.body \ "contactId").as[Long]
    Bidder.edit(bidderId, bidderNumber, contactId) match {
      case Success(bidder) =>
        AppController.pushBidders()
        Ok(s"Edited bidder ${bidder.bidderNumber} ${bidder.contact.name}")
      case Failure(e) =>
        logger.error("Unable to edit bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteBidder(bidderId: Long) = Action(parse.json) { implicit request =>
    Bidder.delete(bidderId) match {
      case Success(bidder) =>
        AppController.pushBidders()
        Ok(s"Deleted bidder ${bidder.bidderNumber} ${bidder.contact.name}")
      case Failure(e) =>
        logger.error("Unable to delete bidder", e)
        BadRequest(e.getMessage)
    }
  }
}
