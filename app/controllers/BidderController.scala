package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{Payment, Bidder}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import misc.Util

object BidderController extends Controller with Secured {

  private def allBiddersData: Future[Map[Bidder, (BigDecimal, BigDecimal, List[Payment])]] = {
    Bidder.all() map { bidders =>
      val tuples = bidders map { bidder =>
        Bidder.owes(bidder) flatMap { owesOpt =>
          Bidder.total(bidder) flatMap { totalOpt =>
            Bidder.payments(bidder) map { paymentsOpt =>
              bidder -> (owesOpt.get, totalOpt.get, paymentsOpt.get)
            }
          }
        }
      }
      val data = tuples.map { Await.result(_, Util.defaultAwaitTimeout) }
      data.toMap
    }
  }

  def bidders = withAuthFuture { userId => implicit request =>
    allBiddersData map { biddersData => Ok(views.html.app.bidder(biddersData, newBidderForm)) }
  }

  val newBidderForm = Form(
    "name" -> nonEmptyText(1, 15)
  )

  def newBidder = withAuthFuture {
    userId => implicit request =>
      newBidderForm.bindFromRequest.fold(
        errors => {
          allBiddersData map { biddersData => BadRequest(views.html.app.bidder(biddersData, errors)) }
        },
        name => {
          Bidder.create(name) flatMap {
            case Some(bidder) => allBiddersData map { _ => Redirect(routes.BidderController.bidders) }
            case None => allBiddersData map {
              biddersData => Conflict(views.html.app.bidder(biddersData, newBidderForm.withError("name", "Bidder named \"" + name + "\" already exists")))
            }
          }
        }
      )
  }

  def deleteBidder(bidderId: Long) = withAuthFuture {
    userId => implicit request =>
      Bidder.delete(bidderId) flatMap {
        case Some(bidder) => allBiddersData map { _ => Redirect(routes.BidderController.bidders) }
        case None => allBiddersData map {
          biddersData => BadRequest(views.html.app.bidder(biddersData,
            newBidderForm.withGlobalError("Unable to delete bidder with id #" + bidderId)))
        }
      }
  }

}
