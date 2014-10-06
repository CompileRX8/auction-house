package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import models.{ItemData, Bidder, WinningBid, Item}
import play.api.libs.json.Json

object ItemController extends Controller {

  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  def items = Action { implicit request =>
    Ok(Json.toJson(Item.currentItems()))
  }

  def newItem = TODO

  def deleteItem(itemId: Long) = TODO

  def addWinningBid(itemId: Long) = Action(parse.json) { implicit request =>
    // TODO: Add Error Handling for bad bidder ids
    val bidderId = (request.body \ "bidderId").as[Long]
    val amount = (request.body \ "amount").as[BigDecimal]
    Item.get(itemId) foreach { item =>
      Bidder.get(bidderId) foreach { bidder =>
        Item.addWinningBid(bidder.get, item.get, amount)
      }
    }
    AppController.pushBidders()
    AppController.pushItems()
    Ok("")
  }

  def editWinningBid(winningBidId: Long) = TODO /* Action(parse.json) { implicit request =>
    try {
      val bidderId = (request.body \ "bidderId").as[Long]
      val itemId = (request.body \ "itemId").as[Long]
      val amount = (request.body \ "amount").as[BigDecimal]
      val resultFuture: Future[SimpleResult] = Item.get(itemId) flatMap { itemOpt =>
        Bidder.get(bidderId) map { bidderOpt =>
          (itemOpt, bidderOpt)
        }
      } flatMap {
        case (Some(item), Some(bidder)) =>
          Item.editWinningBid(winningBidId, bidder, item, amount) map {
            case Some(winningBid) => Ok(Json.toJson(winningBid))
            case None => BadRequest(Json.toJson("Unable to edit winning bid for bidder #" + bidderId + " and item "))
          }
      }
      Await.result(resultFuture, Util.defaultAwaitTimeout)
    } catch {
      case e: Exception =>
        BadRequest(Json.toJson(e.getMessage))
    }
  } */

  def deleteWinningBid(winningBidId: Long) = Action { implicit request =>
    Item.deleteWinningBid(winningBidId)
    AppController.pushBidders()
    AppController.pushItems()
    Ok("")
  }
}
