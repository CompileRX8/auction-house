package controllers

import models.{Bid, BidException, Bidder, Item}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

object BidController extends Controller with Secured {

  val logger = Logger(BidController.getClass)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]

  def bids = Action.async { implicit request =>
    Bid.allByEvent(eventId) map { bids =>
      Ok(Json.toJson(bids))
    } recover {
      case e @ Throwable =>
        logger.error("Unable to send bids", e)
        BadRequest(e.getMessage)
    }
  }

  def newBid(itemId: Long) = Action.async(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]

    Item.get(itemId) flatMap { itemOpt =>
      Bidder.get(bidderId) flatMap { bidderOpt =>
        (itemOpt, bidderOpt) match {
          case (Some(item), Some(bidder)) =>
            Bid.create(bidder, item, amount)
          case _ =>
            Future.failed(new BidException(s"Unable to find item ID $itemId and/or bidder ID $bidderId"))
        }
      }
    } map {
      case Some(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Added bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case None =>
        val msg = s"Unable to create bid for item ID $itemId and bidder ID $bidderId"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to create bid", e)
        BadRequest(e.getMessage)
    }
  }

  def editBid(bidId: Long) = Action.async(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val itemId = (request.body \ "itemId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]

    Item.get(itemId) flatMap { itemOpt =>
      Bidder.get(bidderId) flatMap { bidderOpt =>
        (itemOpt, bidderOpt) match {
          case (Some(item), Some(bidder)) => Bid.edit(bidId, bidder, item, amount)
          case _ => Future.failed(new BidException(s"Unable to find item ID $itemId and/or bidder ID $bidderId"))
        }
      }
    } map {
      case Some(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Edited bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case None =>
        val msg = s"Unable to edit bid for item ID $itemId and bidder ID $bidderId"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to edit bid", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteBid(bidId: Long) = Action.async { implicit request =>
    Bid.delete(bidId) map {
      case Some(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Deleted winning bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case None =>
        val msg = s"Unable to find bid ID $bidId to delete"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to delete winning bid", e)
        BadRequest(e.getMessage)
    }
  }

}
