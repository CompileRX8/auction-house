package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc.{Action, Controller}
import models._
import play.api.libs.json.Json

import scala.util.{Failure, Success}

class ItemController @Inject()(itemService: ItemService, bidderService: BidderService, winningBidService: WinningBidService, appController: AppController) extends Controller {

  val logger = Logger(classOf[ItemController])

  implicit val itemFormat = Json.format[Item]
  implicit val bidderFormat = Json.format[Bidder]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items = Action { implicit request =>
    itemService.currentItems() match {
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
    val estvalue = (request.body \ "est_value").as[BigDecimal]
    itemService.create(itemNumber, category, donor, description, minbid, estvalue) match {
      case Success(item) =>
        appController.pushItems()
        Ok(s"Created item ${item.itemNumber} ${item.description}")
      case Failure(e: ItemException) =>
        logger.error("Unable to create item", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to create item", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteItem(itemId: Long) = Action(parse.json) { implicit request =>
    itemService.delete(itemId) match {
      case Success(item) =>
        appController.pushItems()
        Ok(s"Deleted item ${item.itemNumber} ${item.description}")
      case Failure(e: ItemException) =>
        logger.error("Unable to delete item", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to delete item", e)
        BadRequest(e.getMessage)
    }
  }

  def editItem(itemId: Long) = Action(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val category = (request.body \ "category").as[String]
    val donor = (request.body \ "donor").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    val estvalue = (request.body \ "est_value").as[BigDecimal]
    itemService.edit(itemId, itemNumber, category, donor, description, minbid, estvalue) match {
      case Success(item) =>
        appController.pushItems()
        Ok(s"Edited item ${item.itemNumber} ${item.category} ${item.donor} ${item.description} $$ ${item.minbid}")
      case Failure(e: ItemException) =>
        logger.error("Unable to edit item", e)
        BadRequest(e.message)
      case Failure(e) =>
        logger.error("Unable to edit item", e)
        BadRequest(e.getMessage)
    }
  }

  def addWinningBid(itemId: Long) = Action(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]
    itemService.get(itemId) flatMap { itemOpt =>
      bidderService.get(bidderId) flatMap { bidderOpt =>
        itemService.addWinningBid(bidderOpt.get, itemOpt.get, amount)
      }
    } match {
      case Success(winningBid) =>
        appController.pushBidders()
        appController.pushItems()
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

    winningBidService.get(winningBidId) flatMap { winningBidOpt =>
      itemService.get(itemId) flatMap { itemOpt =>
        bidderService.get(bidderId) flatMap { bidderOpt =>
          (itemOpt, bidderOpt) match {
            case (Some(item), Some(bidder)) => itemService.editWinningBid(winningBidId, bidder, item, amount)
            case _ => Failure(new ItemException(s"Unable to find item ID $itemId or bidder ID $bidderId"))
          }
        }
      }
    } match {
      case Success(winningBid) =>
        appController.pushBidders()
        appController.pushItems()
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
    itemService.deleteWinningBid(winningBidId) match {
      case Success(winningBid) =>
        appController.pushBidders()
        appController.pushItems()
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
