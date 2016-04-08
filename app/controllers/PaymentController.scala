package controllers

import javax.inject.Inject

import models.{BidderException, BidderService}
import play.api.mvc.{Action, Controller}

import scala.util.{Failure, Success}

class PaymentController @Inject()(bidderService: BidderService, appController: AppController) extends Controller with Secured {

  def newPayment(bidderId: Long) = Action(parse.json) { implicit request =>
    val desc = (request.body \ "description").as[String]
    val amount = (request.body \ "amount").as[BigDecimal]
    bidderService.addPayment(bidderId, desc, amount) match {
      case Success(payment) =>
        appController.pushBidders()
        Ok(s"Created payment for ${payment.bidder.id.get} ${payment.bidder.name} for $$ ${payment.amount}")
      case Failure(e: BidderException) =>
        BadRequest(e.message)
      case Failure(e) =>
        BadRequest(e.getMessage)
    }
  }
}
