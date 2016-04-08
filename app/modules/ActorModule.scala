package modules

import actors.{AuctionDemoActor, BiddersActor, ItemsActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * Created by ryan on 4/6/16.
  */
class ActorModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[BiddersActor]("bidders-actor")
    bindActor[ItemsActor]("items-actor")
    bindActor[AuctionDemoActor]("auction-demo-actor")
  }
}
