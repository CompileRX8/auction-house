package controllers

import models.Bidder
import play.api.Logger
import play.api.mvc.{Action, Controller}

object PaymentController extends Controller with Secured {
  val logger = Logger(PaymentController.getClass)

  def newPayment(bidderId: Long) = Action.async(parse.json) { implicit request =>
    val desc = (request.body \ "description").as[String]
    val amount = (request.body \ "amount").as[BigDecimal]
    Bidder.addPayment(bidderId, desc, amount) map { payment =>
      AppController.pushBidders()
      Ok(s"Created payment for ${payment.bidder.bidderNumber} ${payment.bidder.contact.name} for $$ ${payment.amount}")
    } recover {
      case e @ Throwable =>
        logger.error("Unable to create payment", e)
        BadRequest(e.getMessage)
    }
  }
}
