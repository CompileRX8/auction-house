package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Await, Future}
import models.{Payment, Bidder}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import misc.Util

object PaymentController extends Controller with Secured {

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

  val newPaymentForm = Form(
    tuple (
      "bidderId" -> longNumber,
      "description" -> nonEmptyText(1, 45),
      "amount" -> bigDecimal
    )
  )

  def payments = withAuthFuture {
    userId => implicit request =>
      allBiddersData map { biddersData => Ok(views.html.app.payment(biddersData, newPaymentForm))}
  }

  def newPayment(bidderId: Long) = withAuthFuture {
    userId => implicit request =>
      newPaymentForm.bindFromRequest.fold(
        errors => {
          allBiddersData map { biddersData => BadRequest(views.html.app.payment(biddersData, errors)) }
        },
        paymentTuple => {
          val (bidderId, description, amount) = paymentTuple
          Bidder.addPayment(bidderId, description, amount) flatMap {
            case Some(payment) => allBiddersData map { _ => Redirect(routes.PaymentController.payments) }
            case None => allBiddersData map {
              biddersData => Conflict(views.html.app.payment(biddersData, newPaymentForm.withGlobalError("Unable to record payment--please notify Ryan immediately!")))
            }
          }
        }
      )
  }

}
