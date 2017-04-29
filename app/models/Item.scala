package models

import javax.inject.{Inject, Singleton}

import actors.ItemsActor

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import actors.ItemsActor._
import akka.actor.ActorSystem
import play.api.libs.json.Json

import scala.language.postfixOps

case class ItemException(message: String, cause: Exception = null) extends Exception

case class ItemData(item: Item, winningBids: List[WinningBid])

case class Item(id: Option[Long], itemNumber: String, category: String, donor: String, description: String, minbid: Double, estvalue: Double)
object Item extends ((Option[Long], String, String, String, String, Double, Double) => Item)

@Singleton
class ItemHandler @Inject()(actorSystem: ActorSystem, implicit val ec: ExecutionContext) {
  implicit val timeout = Timeout(15 seconds)

  private val itemsActor = actorSystem.actorOf(ItemsActor.props)

  implicit val bidderFormat = Json.format[Bidder]
  implicit val paymentFormat = Json.format[Payment]
  implicit val itemFormat = Json.format[Item]
  implicit val winningBidFormat = Json.format[WinningBid]
  implicit val itemDataFormat = Json.format[ItemData]

  def allCategories(): Future[List[String]] = (itemsActor ? GetCategories).mapTo[List[String]]
  def allDonors(): Future[List[String]] = (itemsActor ? GetDonors).mapTo[List[String]]
  def all(): Future[List[Item]] = (itemsActor ? GetItems).mapTo[List[Item]]

  def allByCategory(category: String): Future[List[Item]] = (itemsActor ? GetItemsByCategory(category)).mapTo[List[Item]]

  def get(id: Long): Future[Option[Item]] = (itemsActor ? GetItem(id)).mapTo[Option[Item]]

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: Double, estvalue: Double): Future[Item] =
    (itemsActor ? Item(None, itemNumber, category, donor, description, minbid, estvalue)).mapTo[Item]

  def delete(id: Long): Future[Item] = (itemsActor ? DeleteItem(id)).mapTo[Item]

  def edit(id: Long, itemNumber: String, category: String, donor: String, description: String, minbid: Double, estvalue: Double): Future[Item] =
    (itemsActor ? EditItem(Item(Some(id), itemNumber, category, donor, description, minbid, estvalue))).mapTo[Item]

  def getWinningBid(id: Long): Future[Option[WinningBid]] = (itemsActor ? GetWinningBid(id)).mapTo[Option[WinningBid]]
  def winningBids(item: Item): Future[List[WinningBid]] = (itemsActor ? WinningBidsByItem(item)).mapTo[List[WinningBid]]
  def winningBids(bidder: Bidder): Future[List[WinningBid]] = (itemsActor ? WinningBidsByBidder(bidder)).mapTo[List[WinningBid]]

  def addWinningBid(bidder: Bidder, item: Item, amount: Double): Future[WinningBid] =
    (itemsActor ? WinningBid(None, bidder, item, amount)).mapTo[WinningBid]

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: Double): Future[WinningBid] =
    (itemsActor ? EditWinningBid(winningBidId, bidder, item, amount)).mapTo[WinningBid]

  def deleteWinningBid(winningBidId: Long): Future[WinningBid] =
    (itemsActor ? DeleteWinningBid(winningBidId)).mapTo[WinningBid]

  private def allWinningBidsByBidder(bidder: Bidder) = winningBids(bidder)

  def totalWinningBidsByBidder(bidder: Bidder): Future[Double] =
    allWinningBidsByBidder(bidder) map { bidsList =>
      (0.0 /: bidsList) { (sum, bid) => sum + bid.amount}
    }

  def totalEstValueByBidder(bidder: Bidder): Future[Double] =
    allWinningBidsByBidder(bidder) map { bidsList =>
      (0.0 /: bidsList) { (sum, bid) => sum + bid.item.estvalue}
    }

  def currentItems(): Future[List[ItemData]] = {
    all() flatMap { items =>
      val listFutureItemData = items map { item =>
        winningBids(item).fallbackTo(Future.successful(List())) map { wbs =>
          ItemData(item, wbs)
        }
      }
      Future.sequence(listFutureItemData)
    }
  }

  def loadFromDataSource(): Future[Boolean] = {
    (itemsActor ? LoadFromDataSource).mapTo[Boolean]
  }
}

case class WinningBidException(message: String, cause: Exception = null) extends Exception

case class WinningBid(id: Option[Long], bidder: Bidder, item: Item, amount: Double)
object WinningBid extends ((Option[Long], Bidder, Item, Double) => WinningBid)
