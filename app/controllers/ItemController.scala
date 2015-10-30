package controllers

import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

object ItemController extends Controller with Secured {

  val logger = Logger(ItemController.getClass)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items = Action.async { implicit request =>
    Item.currentItems(eventId) map { items =>
      Ok(Json.toJson(items))
    } recover {
      case e @ Throwable =>
        logger.error("Unable to send items", e)
        BadRequest(e.getMessage)
    }
  }

  def newItem = Action.async(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    Item.create(eventId, itemNumber, description, minbid) map { item =>
      AppController.pushItems()
      Ok(s"Created item ${item.itemNumber} ${item.description}")
    } recover {
      case e @ Throwable =>
        logger.error("Unable to create item", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteItem(itemId: Long) = Action.async(parse.json) { implicit request =>
    Item.delete(itemId) map {
      case Some(item) =>
        AppController.pushItems()
        Ok(s"Deleted item ${item.itemNumber} ${item.description}")
      case None =>
        val msg = s"Unable to find item ID $itemId to delete"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to delete item", e)
        BadRequest(e.getMessage)
    }
  }

  def editItem(itemId: Long) = Action.async(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    Item.edit(itemId, itemNumber, description, minbid) map {
      case Some(item) =>
        AppController.pushItems()
        Ok(s"Edited item ${item.itemNumber} ${item.description} $$ ${item.minbid}")
      case None =>
        val msg = s"Unable to find item ID $itemId to edit"
        logger.error(msg)
        BadRequest(msg)
    } recover {
      case e @ Throwable =>
        logger.error("Unable to edit item", e)
        BadRequest(e.getMessage)
    }
  }
}
