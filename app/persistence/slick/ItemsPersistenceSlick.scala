package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import models.{Bidder, Item, ItemException, WinningBid}
import persistence.ItemsPersistence
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.JdbcProfile

class ItemsPersistenceSlick @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends SlickPersistence with ItemsPersistence {
  import driver.api._

  override def load(itemsActor: ActorRef): Future[Boolean] = {
    db.run(itemsQuery.result) map { items =>
      items foreach {
        itemsActor ! _
      }
      db.run(winningBidsQuery.result) map { _ map { a =>
        a.toWinningBid
      } } foreach {
        itemsActor ! _
      }
      true
    }
  }

  override def create(item: Item): Future[Item] = {
    db.run((itemsQuery returning itemsQuery.map(_.id)) += item) map { newItemId =>
      Item(Some(newItemId), item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue)
    }
  }

  override def create(winningBid: WinningBid): Future[WinningBid] = {
    db.run((winningBidsQuery returning winningBidsQuery.map(_.id)) += WinningBidRow.fromWinningBid(winningBid)) map { newWinningBidId =>
      WinningBid(Some(newWinningBidId), winningBid.bidder, winningBid.item, winningBid.amount)
    }
  }

  override def delete(item: Item): Future[Item] = {
    val q = itemsQuery.filter(_.id === item.id.get)
    db.run(q.result) flatMap { rows =>
      if(rows.length == 1) {
        db.run(q.delete) map { _ => rows.head }
      } else {
        Future.failed(new ItemException(s"Unable to delete item with unique ID ${item.id.get}"))
      }
    }
  }

  override def delete(winningBid: WinningBid): Future[WinningBid] = {
    val q = winningBidsQuery.filter(_.id === winningBid.id.get)
    db.run(q.result) flatMap { rows =>
      if(rows.length == 1) {
        db.run(q.delete) flatMap { _ => rows.head.toWinningBid }
      } else {
        Future.failed(new ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id.get}"))
      }
    }
  }

  override def edit(item: Item): Future[Item] = {
    val q = itemsQuery.filter(_.id === item.id.get)
    db.run(q.result) flatMap { rows =>
      if(rows.length == 1) {
        db.run(q.update(item)) map { _ => item }
      } else {
        Future.failed(new ItemException(s"Unable to update item with unique ID ${item.id.get}"))
      }
    }
  }

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Future[WinningBid] = {
    val q = winningBidsQuery.filter(_.id === winningBidId)
    val row = WinningBidRow(Some(winningBidId), bidder.id.get, item.id.get, amount)
    db.run(q.result) flatMap { rows =>
      if(rows.length == 1) {
        db.run(q.update(row)) flatMap { _ => row.toWinningBid }
      } else {
        Future.failed(new ItemException(s"Unable to edit winning bid with unique ID $winningBidId"))
      }
    }
  }

  override def winningBidById(id: Long): Future[Option[WinningBid]] = {
    db.run(winningBidsQuery.filter(_.id === id).result.headOption) flatMap { rowOpt =>
      val futList = rowOpt.toList map { _.toWinningBid }
      val futWB = Future.sequence(futList)
      futWB map { _.headOption }
    }
  }

  override def winningBidsByItem(item: Item): Future[List[WinningBid]] = {
    db.run(winningBidsQuery.filter(_.itemId === item.id.get).to[List].result) flatMap { rows =>
      Future.sequence(rows map { _.toWinningBid })
    }
  }

  override def winningBidsByBidder(bidder: Bidder): Future[List[WinningBid]] = {
    db.run(winningBidsQuery.filter(_.bidderId === bidder.id.get).to[List].result) flatMap { rows =>
      Future.sequence(rows map { _.toWinningBid })
    }
  }

  override def sortedItems: Future[List[Item]] = {
    db.run(itemsQuery.sortBy(_.itemNumber).to[List].result)
  }

  override def itemById(id: Long): Future[Option[Item]] = {
    db.run(itemsQuery.filter(_.id === id).result.headOption)
  }

  override def itemByItemNumber(itemNumber: String): Future[Option[Item]] = {
    db.run(itemsQuery.filter(_.itemNumber === itemNumber).result.headOption)
  }
}
