package models
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/**
  * Created by ryan on 4/7/16.
  */
@ImplementedBy(classOf[BidderServiceImpl])
trait BidderService {
  def payments(bidder: Bidder): Future[List[Payment]]

  def paymentsTotal(bidder: Bidder): Future[BigDecimal]

  def addPayment(bidderId: Long, description: String, amount: BigDecimal): Future[Payment]

  def all(): Future[List[Bidder]]

  def get(id: Long): Future[Option[Bidder]]

  def create(name: String): Future[Bidder]

  def delete(id: Long): Future[Bidder]

  def edit(id: Long, name: String): Future[Bidder]

  def totalOwed(bidderId: Long): Future[BigDecimal]

  def currentBidders(): Future[List[BidderData]]

  def loadFromDataSource(): Future[Boolean]

}
