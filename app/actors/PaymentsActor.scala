package actors

import akka.actor.{Actor, Props}
import models._
import persistence.PaymentsPersistence.payments
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.util.{Failure, Success}

object PaymentsActor {
  case object LoadFromDataSource

  case object GetPayments
  case class GetPayment(id: Long)

  case class PaymentsForBidder(bidderId: Long)
  case class PaymentsForEvent(eventId: Long)
  case class PaymentsForContact(contactId: Long)
  case class PaymentsForOrganization(organizationId: Long)

  def props = Props(classOf[PaymentsActor])

  val paymentsActor = Akka.system.actorOf(PaymentsActor.props)
}

class PaymentsActor extends Actor {
  import PaymentsActor._

  override def receive = {
    case LoadFromDataSource =>

    case GetPayments =>
      payments.all onComplete { sender ! _ }

    case GetPayment(id: Long) =>
      payments.forId(id) onComplete { sender ! _ }

    case newPayment @ Payment(None, bidder, description, amount) =>
      payments.create(newPayment) onComplete { sender ! _ }

    case p @ Payment(idOpt @ Some(id), bidder, description, amount) =>

    case PaymentsForBidder(bidderId: Long) =>
      payments.forBidderId(bidderId) onComplete { sender ! _ }

    case PaymentsForEvent(eventId: Long) =>
      payments.forEventId(eventId) onComplete { sender ! _ }

    case PaymentsForContact(contactId: Long) =>
      payments.forContactId(contactId) onComplete { sender ! _ }

    case PaymentsForOrganization(organizationId: Long) =>
      payments.forOrganizationId(organizationId) onComplete { sender ! _ }

    case msg @ _ => sender ! Failure(new PaymentException(s"Unknown message: $msg"))
  }
}