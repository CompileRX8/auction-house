package controllers

import play.api.Logger
import play.api.mvc.{Action, Controller}
import models._
import play.api.libs.json.Json

import scala.util.{Failure, Success}

object ItemController extends Controller {

  val logger = Logger(ItemController.getClass)

  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items = Action { implicit request =>
    Item.currentItems() match {
      case Success(items) => Ok(Json.toJson(items))
      case Failure(e: ItemException) =>
        logger.error("Unable to send items", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to send items", e)
        BadRequest(e.getMessage)
    }
  }

  def newItem = Action(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val category = (request.body \ "category").as[String]
    val donor = (request.body \ "donor").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    Item.create(itemNumber, category, donor, description, minbid) match {
      case Success(item) =>
        AppController.pushItems()
        Ok(s"Created item ${item.itemNumber} ${item.description}")
      case Failure(e: ItemException) =>
        logger.error("Unable to create item", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to create item", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteItem(itemId: Long) = Action { implicit request =>
    Item.delete(itemId) match {
      case Success(item) =>
        AppController.pushItems()
        Ok(s"Deleted item ${item.itemNumber} ${item.description}")
      case Failure(e: ItemException) =>
        logger.error("Unable to delete item", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to delete item", e)
        BadRequest(e.getMessage)
    }
  }

  def addWinningBid(itemId: Long) = Action(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]
    Item.get(itemId) flatMap { itemOpt =>
      Bidder.get(bidderId) flatMap { bidderOpt =>
        Item.addWinningBid(bidderOpt.get, itemOpt.get, amount)
      }
    } match {
      case Success(winningBid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Added winning bid for ${winningBid.item.itemNumber} ${winningBid.item.description} won by ${winningBid.bidder.name} for ${winningBid.amount}")
      case Failure(e: ItemException) =>
        logger.error("Unable to create winning bid", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to create winning bid", e)
        BadRequest(e.getMessage)
    }
  }

  def editWinningBid(winningBidId: Long) = Action(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val itemId = (request.body \ "itemId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]

    WinningBid.get(winningBidId) flatMap { winningBidOpt =>
      Item.get(itemId) flatMap { itemOpt =>
        Bidder.get(bidderId) flatMap { bidderOpt =>
          (itemOpt, bidderOpt) match {
            case (Some(item), Some(bidder)) => Item.editWinningBid(winningBidId, bidder, item, amount)
            case _ => Failure(new ItemException(s"Unable to find item ID $itemId or bidder ID $bidderId"))
          }
        }
      }
    } match {
      case Success(winningBid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Edited winning bid for ${winningBid.item.itemNumber} ${winningBid.item.description} won by ${winningBid.bidder.name} for ${winningBid.amount}")
      case Failure(e: BidderException) =>
        logger.error("Unable to edit winning bid", e)
        BadRequest(e.message)
      case Failure(e: ItemException) =>
        logger.error("Unable to edit winning bid", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to edit winning bid", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteWinningBid(winningBidId: Long) = Action { implicit request =>
    Item.deleteWinningBid(winningBidId) match {
      case Success(winningBid) =>
        AppController.pushBidders()
        AppController.pushItems()
        Ok(s"Deleted winning bid for ${winningBid.item.itemNumber} ${winningBid.item.description} won by ${winningBid.bidder.name} for ${winningBid.amount}")
      case Failure(e: ItemException) =>
        logger.error("Unable to delete winning bid", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to delete winning bid", e)
        BadRequest(e.getMessage)
    }
  }
}
