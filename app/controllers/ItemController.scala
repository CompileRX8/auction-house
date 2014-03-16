package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{Bidder, WinningBid, Item}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import misc.Util

object ItemController extends Controller with Secured {

  private def allItemData: Future[(Map[Item, List[WinningBid]], List[String], List[String])] = {
    val itemData = Item.all() map { items =>
      val tuples = items map { item =>
        Item.winningBids(item) map { bidsOpt =>
          item -> bidsOpt.get
        }
      }
      val data = tuples.map { Await.result(_, Util.defaultAwaitTimeout) }
      data.toMap
    }
    (itemData zip Item.allCategories() zip Item.allDonors()).collect { case ((a, b), c) => (a, b, c) }
  }

  def items = withAuthFuture { userId => implicit request =>
    allItemData map { case (items, categories, donors) => Ok(views.html.app.item(items, categories, donors, newItemForm, newWinningBidForm)) }
  }

  val newItemForm = Form(
    tuple (
      "itemNumber" -> nonEmptyText(1, 5),
      "category" -> nonEmptyText(1, 15),
      "donor" -> nonEmptyText(1, 15),
      "description" -> nonEmptyText(1, 45),
      "minbid" -> bigDecimal
    )
  )

  def newItem = withAuthFuture {
    userId => implicit request =>
      newItemForm.bindFromRequest.fold(
        errors => {
          allItemData map { case (items, categories, donors) =>
            BadRequest(views.html.app.item(items, categories, donors, errors, newWinningBidForm))
          }
        },
        itemTuple => {
          val (itemNum, cat, donor, desc, minbid) = itemTuple
          Item.create(itemNum, cat, donor, desc, minbid) flatMap {
            case Some(item) => allItemData map { _ => Redirect(routes.ItemController.items) }
            case None => allItemData map { case (items, categories, donors) =>
              Conflict(views.html.app.item(items, categories, donors, newItemForm.withError("itemNumber", "Item #" + itemNum + " already exists"), newWinningBidForm))
            }
          }

        }
      )
  }

  def deleteItem(itemId: Long) = withAuthFuture {
    userId => implicit request =>
      Item.delete(itemId) flatMap {
        case Some(item) => allItemData map { _ => Redirect(routes.ItemController.items) }
        case None => allItemData map {
          case (items, categories, donors) => BadRequest(views.html.app.item(items, categories, donors,
            newItemForm.withGlobalError("Unable to delete item with item id" + itemId), newWinningBidForm))
        }
      }

  }

  val newWinningBidForm = Form(
    tuple (
      "bidderId" -> longNumber,
      "amount" -> bigDecimal
    )
  )

  def addWinningBid(itemId: Long) = withAuthFuture {
    userId => implicit request =>
      newWinningBidForm.bindFromRequest.fold(
        errors => {
          allItemData map { case (items, categories, donors) =>
            BadRequest(views.html.app.item(items, categories, donors, newItemForm, errors))
          }
        },
        winningBidTuple => {
          val (bidderId, amount) = winningBidTuple
          Item.get(itemId) flatMap { itemOpt =>
            Bidder.get(bidderId) map { bidderOpt =>
              (itemOpt, bidderOpt)
            }
          } flatMap {
            case (Some(item), Some(bidder)) =>
              Item.addWinningBid(bidder, item, amount) flatMap {
                case Some(winningBid) => allItemData map { _ => Redirect(routes.ItemController.items) }
                case None => allItemData map {
                  case (items, categories, donors) => BadRequest(views.html.app.item(items, categories, donors,
                    newItemForm, newWinningBidForm.withGlobalError("Unable to register winning bid for bidder #" + bidderId)))
                }
              }
          }
        }
      )
  }
}
