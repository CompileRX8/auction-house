package models

import com.google.inject.ImplementedBy

import scala.concurrent.Future

/**
  * Created by ryan on 4/7/16.
  */
@ImplementedBy(classOf[ItemServiceImpl])
trait ItemService {
  def allCategories(): Future[List[String]]

  def allDonors(): Future[List[String]]

  def all(): Future[List[Item]]

  def allByCategory(category: String): Future[List[Item]]

  def get(id: Long): Future[Option[Item]]

  def create(itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item]

  def delete(id: Long): Future[Item]

  def edit(id: Long, itemNumber: String, category: String, donor: String, description: String, minbid: BigDecimal, estvalue: BigDecimal): Future[Item]

  def getWinningBid(id: Long): Future[Option[WinningBid]]

  def winningBids(item: Item): Future[List[WinningBid]]

  def winningBids(bidder: Bidder): Future[List[WinningBid]]

  def addWinningBid(bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid]

  def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid]

  def deleteWinningBid(winningBidId: Long): Future[WinningBid]

  def currentItems(): Future[List[ItemData]]

  def loadFromDataSource(): Future[Boolean]
}
