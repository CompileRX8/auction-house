package modules

import com.google.inject.AbstractModule
import persistence._
import persistence.slick._

class SlickModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[BiddersPersistence]).toInstance(Bidders)
    bind(classOf[BidsPersistence]).toInstance(Bids)
    bind(classOf[ContactsPersistence]).toInstance(Contacts)
    bind(classOf[EventsPersistence]).toInstance(Events)
    bind(classOf[ItemsPersistence]).toInstance(Items)
    bind(classOf[OrganizationsPersistence]).toInstance(Organizations)
    bind(classOf[PaymentsPersistence]).toInstance(Payments)
  }
}
