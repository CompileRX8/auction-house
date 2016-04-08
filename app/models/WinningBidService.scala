package models

import com.google.inject.ImplementedBy

import scala.concurrent.Future

/**
  * Created by ryan on 4/7/16.
  */
@ImplementedBy(classOf[WinningBidServiceImpl])
trait WinningBidService {
  def get(id: Long): Future[Option[WinningBid]]

  def allByBidder(bidder: Bidder): Future[List[WinningBid]]

  def totalByBidder(bidder: Bidder): Future[Option[BigDecimal]]

  def totalEstValueByBidder(bidder: Bidder): Future[Option[BigDecimal]]
}
