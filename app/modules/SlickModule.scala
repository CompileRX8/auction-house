package modules

import com.google.inject.AbstractModule
import persistence.{BiddersPersistence, ItemsPersistence}
import persistence.slick.{BiddersPersistenceSlick, ItemsPersistenceSlick}

class SlickModule extends AbstractModule {
  def configure = {
    bind(classOf[BiddersPersistence]).to(classOf[BiddersPersistenceSlick])
    bind(classOf[ItemsPersistence]).to(classOf[ItemsPersistenceSlick])
  }
}
