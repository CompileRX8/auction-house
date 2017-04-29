package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Controller, Result}
import models._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

class ItemController @Inject()(appController: AppController, bidderController: BidderController, itemHandler: ItemHandler, bidderHandler: BidderHandler, implicit val ec: ExecutionContext) extends Controller {

  val logger = Logger(classOf[ItemController])

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items: Action[AnyContent] = Action.async { implicit request =>
    itemHandler.currentItems() map {
      items => Ok(Json.toJson(items))
    } recover exceptionAction("send items")
  }

  def newItem: Action[JsValue] = Action.async(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val category = (request.body \ "category").as[String]
    val donor = (request.body \ "donor").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[Double]
    val estvalue = (request.body \ "est_value").as[Double]
    itemHandler.create(itemNumber, category, donor, description, minbid, estvalue) map itemAction("Created") recover exceptionAction("create item")
  }

  def deleteItem(itemId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    itemHandler.delete(itemId) map itemAction("Deleted") recover exceptionAction("delete item")
  }

  def editItem(itemId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val category = (request.body \ "category").as[String]
    val donor = (request.body \ "donor").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[Double]
    val estvalue = (request.body \ "est_value").as[Double]
    itemHandler.edit(itemId, itemNumber, category, donor, description, minbid, estvalue) map itemAction("Edited") recover exceptionAction("edit item")
  }

  def addWinningBid(itemId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[Double]
    itemHandler.get(itemId) flatMap { itemOpt =>
      bidderHandler.get(bidderId) flatMap { bidderOpt =>
        itemHandler.addWinningBid(bidderOpt.get, itemOpt.get, amount)
      }
    } map winningBidAction("Added") recover exceptionAction("create winning bid")
  }

  def editWinningBid(winningBidId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val bidderId = (request.body \ "bidderId").as[Long]
    val itemId = (request.body \ "itemId").as[Long]
    val amount = (request.body \ "amount").as[Double]

    itemHandler.get(winningBidId) flatMap { winningBidOpt =>
      itemHandler.get(itemId) flatMap { itemOpt =>
        bidderHandler.get(bidderId) flatMap { bidderOpt =>
          (itemOpt, bidderOpt) match {
            case (Some(item), Some(bidder)) => itemHandler.editWinningBid(winningBidId, bidder, item, amount)
            case _ => Future.failed(ItemException(s"Unable to find item ID $itemId or bidder ID $bidderId"))
          }
        }
      }
    } map winningBidAction("Edited") recover exceptionAction("edit winning bid")
  }

  def deleteWinningBid(winningBidId: Long): Action[AnyContent] = Action.async { implicit request =>
    itemHandler.deleteWinningBid(winningBidId) map winningBidAction("Deleted") recover exceptionAction("delete winning bid")
  }

  private def itemAction(msg: String) = { item: Item =>
    appController.pushItems()
    Ok(s"$msg item ${item.itemNumber} ${item.category} ${item.donor} ${item.description} $$ ${item.minbid}")
  }

  private def winningBidAction(msg: String) = { winningBid: WinningBid =>
    appController.pushBidders()
    appController.pushItems()
    Ok(s"$msg winning bid for ${winningBid.item.itemNumber} ${winningBid.item.description} won by ${winningBid.bidder.name} for ${winningBid.amount}")
  }

  private def exceptionAction(msg: String): PartialFunction[Throwable, Result] = {
    case e: BidderException =>
      logger.error(s"Unable to $msg", e)
      BadRequest(e.message)
    case e: ItemException =>
      logger.error(s"Unable to $msg", e)
      BadRequest(e.message)
    case e: Exception =>
      logger.error(s"Unable to $msg", e)
      BadRequest(e.getMessage)
  }
}
