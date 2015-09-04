package controllers

import models.{BidException, Bid, Bidder, Item}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.util.{Success, Failure}

object BidController extends Controller with Secured {

  val logger = Logger(BidController.getClass)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]

  def bids = Action { implicit request =>
    Bid.allByEvent(eventId) match {
      case Success(bids) =>
        Ok(Json.toJson(bids))
      case Failure(e) =>
        logger.error("Unable to send bids", e)
        BadRequest(e.getMessage)
    }
  }

  def newBid(itemId: Long) = Action(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]

    Item.get(itemId) flatMap { itemOpt =>
      Bidder.get(bidderId) flatMap { bidderOpt =>
        (itemOpt, bidderOpt) match {
          case (Some(item), Some(bidder)) =>
            Bid.create(bidder, item, amount)
          case _ =>
            Failure(new BidException(s"Unable to find item ID $itemId and/or bidder ID $bidderId"))
        }
      }
    } match {
      case Success(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Added bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case Failure(e) =>
        logger.error("Unable to create bid", e)
        BadRequest(e.getMessage)
    }
  }

  def editBid(bidId: Long) = Action(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val itemId = (request.body \ "itemId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]

    Item.get(itemId) flatMap { itemOpt =>
      Bidder.get(bidderId) flatMap { bidderOpt =>
        (itemOpt, bidderOpt) match {
          case (Some(item), Some(bidder)) => Bid.edit(bidId, bidder, item, amount)
          case _ => Failure(new BidException(s"Unable to find item ID $itemId and/or bidder ID $bidderId"))
        }
      }
    } match {
      case Success(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Edited bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case Failure(e) =>
        logger.error("Unable to edit winning bid", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteBid(bidId: Long) = Action { implicit request =>
    Bid.delete(bidId) match {
      case Success(bid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Deleted winning bid for ${bid.item.itemNumber} ${bid.item.description} by ${bid.bidder.bidderNumber} ${bid.bidder.contact.name} for ${bid.amount}")
      case Failure(e) =>
        logger.error("Unable to delete winning bid", e)
        BadRequest(e.getMessage)
    }
  }

}
