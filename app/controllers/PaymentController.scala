package controllers

import javax.inject.Inject

import play.api.mvc.{Action, Controller}
import models.{BidderException, BidderHandler}

import scala.concurrent.ExecutionContext

class PaymentController @Inject()(appController: AppController, bidderHandler: BidderHandler)(implicit val ec: ExecutionContext) extends Controller with Secured {

  def newPayment(bidderId: Long) = Action.async(parse.json) { implicit request =>
    val desc = (request.body \ "description").as[String]
    val amount = (request.body \ "amount").as[Double]
    bidderHandler.addPayment(bidderId, desc, amount) map {
      case payment =>
        appController.pushBidders()
        Ok(s"Created payment for ${payment.bidder.id.get} ${payment.bidder.name} for $$ ${payment.amount}")
    } recover {
      case e: BidderException =>
        BadRequest(e.message)
      case e =>
        BadRequest(e.getMessage)
    }
  }
}
