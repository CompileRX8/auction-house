package modules

import actors.{AuctionDemoActor, BiddersActor, ItemsActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[BiddersActor]("biddersActor")
    bindActor[ItemsActor]("itemsActor")
    bindActor[AuctionDemoActor]("auctionDemoActor")
  }
}
