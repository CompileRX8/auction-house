package controllers

import play.api.Logger
import play.api.mvc.{Action, Controller}
import models._
import play.api.libs.json.Json

import scala.util.{Failure, Success}

object ItemController extends Controller with Secured {

  val logger = Logger(ItemController.getClass)

  implicit val itemFormat = Json.format[Item]
  implicit val bidFormat = Json.format[Bid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items = Action { implicit request =>
    Item.currentItems(eventId) match {
      case Success(items) => Ok(Json.toJson(items))
      case Failure(e) =>
        logger.error("Unable to send items", e)
        BadRequest(e.getMessage)
    }
  }

  def newItem = Action(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    Item.create(eventId, itemNumber, description, minbid) match {
      case Success(item) =>
        AppController.pushItems()
        Ok(s"Created item ${item.itemNumber} ${item.description}")
      case Failure(e) =>
        logger.error("Unable to create item", e)
        BadRequest(e.getMessage)
    }
  }

  def deleteItem(itemId: Long) = Action(parse.json) { implicit request =>
    Item.delete(itemId) match {
      case Success(item) =>
        AppController.pushItems()
        Ok(s"Deleted item ${item.itemNumber} ${item.description}")
      case Failure(e) =>
        logger.error("Unable to delete item", e)
        BadRequest(e.getMessage)
    }
  }

  def editItem(itemId: Long) = Action(parse.json) { implicit request =>
    val itemNumber = (request.body \ "item_num").as[String]
    val description = (request.body \ "description").as[String]
    val minbid = (request.body \ "min_bid").as[BigDecimal]
    Item.edit(itemId, itemNumber, description, minbid) match {
      case Success(item) =>
        AppController.pushItems()
        Ok(s"Edited item ${item.itemNumber} ${item.description} $$ ${item.minbid}")
      case Failure(e) =>
        logger.error("Unable to edit item", e)
        BadRequest(e.getMessage)
    }
  }
}
