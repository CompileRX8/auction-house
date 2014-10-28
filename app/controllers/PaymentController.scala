package controllers

import play.api.mvc.{Action, Controller}
import models.{BidderException, Bidder}

import scala.util.{Failure, Success}

object PaymentController extends Controller with Secured {

  def newPayment(bidderId: Long) = Action(parse.json) { implicit request =>
    val desc = (request.body \ "description").as[String]
    val amount = (request.body \ "amount").as[BigDecimal]
    Bidder.addPayment(bidderId, desc, amount) match {
      case Success(payment) =>
        AppController.pushBidders()
        Ok(s"Created payment for ${payment.bidder.id.get} ${payment.bidder.name} for $$ ${payment.amount}")
      case Failure(e: BidderException) =>
        BadRequest(e.message)
      case Failure(e) =>
        BadRequest(e.getMessage)
    }
  }
}
