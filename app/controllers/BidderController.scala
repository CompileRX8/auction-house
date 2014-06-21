package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{BidderData, WinningBid, Payment, Bidder}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import misc.Util
import play.api.libs.json.Json

object BidderController extends Controller {

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val bidderDataFormat = Json.format[BidderData]

  def bidders = Action { implicit request =>
    try {
      val futureBs = Bidder.all() map { bidders =>
        val dataFuture = bidders map { bidder =>
          WinningBid.allByBidder(bidder) flatMap { winningBidsOpt =>
            Bidder.payments(bidder) map { paymentsOpt =>
              BidderData(bidder, paymentsOpt.get, winningBidsOpt.get)
            }
          }
        }
        dataFuture.map { Await.result(_, Util.defaultAwaitTimeout) }
      }
      val bs = Await.result(futureBs, Util.defaultAwaitTimeout)
      Ok(Json.toJson(bs))
    } catch {
      case e: Exception =>
        BadRequest(Json.toJson(e.getMessage))
    }
  }

  def newBidder = Action(parse.json) { implicit request =>
    val name = (request.body \ "name").as[String]
    Bidder.create(name)
    Ok("")
  }

  def deleteBidder(bidderId: Long) = TODO
}
