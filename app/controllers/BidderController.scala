package controllers

import javax.inject.Inject

import models._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

class BidderController @Inject() (appController: AppController) extends Controller with Secured {

  val logger = Logger(getClass)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val winningBidFormat = Json.format[Bid]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders = Action.async { implicit request =>
    val eventId: Long = request.session.get("eventId").map(Long.unbox(_)).getOrElse(0L)
    Bidder.currentBidders(eventId) map { bidders =>
      Ok(Json.toJson(bidders))
    } recover {
      case e: Throwable =>
        logger.error("Unable to send bidders", e)
        BadRequest(e.getMessage)
    }
  }

  def newBidder = Action.async(parse.json) { implicit request =>
    val eventId: Long = request.session.get("eventId").map(Long.unbox(_)).getOrElse(0L)
    val bidderNumber = (request.body \ "bidderNumber").as[String]
    val contactId = (request.body \ "contactId").as[Long]
    Bidder.create(eventId, bidderNumber, contactId) map { bidder =>
      appController.pushBidders()
      Ok(s"Created bidder ${bidder.bidderNumber} ${bidder.contact.name}")
    } recover {
      case e: Throwable =>
        logger.error("Unable to create bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def editBidder(bidderId: Long) = Action.async(parse.json) { implicit request =>
    val bidderNumber = (request.body \ "bidderNumber").as[String]
    val contactId = (request.body \ "contactId").as[Long]
    Bidder.edit(bidderId, bidderNumber, contactId) map {
      case Some(bidder) =>
        appController.pushBidders()
        Ok(s"Edited bidder ${bidder.bidderNumber} ${bidder.contact.name}")
      case None =>
        val msg = s"Unable to find bidder ID $bidderId to edit"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e: Throwable =>
        logger.error("Unable to edit bidder", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteBidder(bidderId: Long) = Action.async(parse.json) { implicit request =>
    Bidder.delete(bidderId) map {
      case Some(bidder) =>
        appController.pushBidders()
        Ok(s"Deleted bidder ${bidder.bidderNumber} ${bidder.contact.name}")
      case None =>
        val msg = s"Unable to find bidder $bidderId to delete"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e: Throwable =>
        logger.error("Unable to delete bidder", e)
        BadRequest(e.getMessage)
    }
  }
}
