package controllers

import play.api.mvc.{Action, Controller}
import models.Bidder

object PaymentController extends Controller with Secured {

  def newPayment(bidderId: Long) = Action(parse.json) { implicit request =>
    val desc = (request.body \ "description").as[String]
    val amount = (request.body \ "amount").as[BigDecimal]
    Bidder.addPayment(bidderId, desc, amount)
    AppController.pushBidders()
    Ok("")
  }
}
