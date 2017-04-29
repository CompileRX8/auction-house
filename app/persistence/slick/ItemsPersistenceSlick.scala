package persistence.slick

import javax.inject.Inject

import akka.actor.ActorRef
import models.{Bidder, Item, ItemException, WinningBid}
import persistence.ItemsPersistence

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ItemsPersistenceSlick @Inject() extends SlickPersistence with ItemsPersistence {

  import dbConfig.profile.api._

  override def load(itemsActor: ActorRef): Future[Boolean] = {
    val items = for(i <- itemsQuery) yield i
    val winningBids = for(wb <- winningBidsQuery) yield wb
    val itemsUpdate = db.run(items.to[List].result).collect {
      case i =>
        itemsActor ! i
        true
    }
    val winningBidsUpdate = db.run(winningBids.to[List].result).collect {
      case wb =>
        itemsActor ! wb
        true
    }
    itemsUpdate.zip(winningBidsUpdate).map {
      case (i, wb) => i && wb
      case _ => false
    }
  }

  override def create(item: Item): Future[Item] = {
    db.run {
      val newItemIdOp = (itemsQuery returning itemsQuery.map(_.id)) += item
      newItemIdOp.map(iId => Item(Some(iId), item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue))
    }
  }

  override def create(winningBid: WinningBid): Future[WinningBid] = {
    val createTuple = WinningBidRow(None, winningBid.bidder.id.get, winningBid.item.id.get, winningBid.amount)
    for {
      bidder <- db.run(
        biddersQuery.filter(_.id === winningBid.bidder.id.get).result.head
      )
      item <- db.run(
        itemsQuery.filter(_.id === winningBid.item.id.get).result.head
      )
      newWinningBid <- db.run(
        winningBidsQuery returning winningBidsQuery.map {
          _.id
        } += createTuple
      ).map(id => WinningBid(Some(id), bidder, item, winningBid.amount))
    } yield newWinningBid
  }

  override def delete(item: Item): Future[Item] = {
    db.run {
      itemsQuery.filter(_.id === item.id.get).delete map {
        case 1 => item
        case _ => throw ItemException(s"Unable to delete item with unique ID ${item.id.get}")
      }
    }
  }

  override def delete(winningBid: WinningBid): Future[WinningBid] = {
    db.run {
      winningBidsQuery.filter(_.id === winningBid.id.get).delete map {
        case 1 => winningBid
        case _ => throw ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id.get}")
      }
    }
  }

  override def edit(item: Item): Future[Item] = {
    val itemUpdate = (item.itemNumber, item.category, item.donor, item.description, item.minbid.toDouble, item.estvalue.toDouble)
    db.run {
      itemsQuery.filter(_.id === item.id.get).map( i =>
        (i.itemNumber, i.category, i.donor, i.description, i.minbid, i.estvalue)
      ).update(itemUpdate).map {
        case 1 => item
        case _ => throw ItemException(s"Unable to update item with unique ID ${item.id.get}")
      }
    }
  }

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: PGMoney): Future[WinningBid] = {
    val winningBidUpdate = (bidder.id.get, item.id.get, amount.toDouble)
    db.run {
      winningBidsQuery.filter(_.id === winningBidId).map( wb =>
        (wb.bidderId, wb.itemId, wb.amount)
      ).update(winningBidUpdate).map {
        case 1 => WinningBid(Some(winningBidId), bidder, item, amount)
        case _ => throw ItemException(s"Unable to edit winning bid with unique ID $winningBidId")
      }
    }
  }

  override def winningBidById(id: Long): Future[Option[WinningBid]] = {
    val winningBidOpt = for {
      wb <- winningBidsQuery if wb.id === id
      b <- biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(winningBidOpt.result.headOption)
  }

  override def winningBidsByItem(item: Item): Future[List[WinningBid]] = {
    val q = for {
      wb <- winningBidsQuery if wb.itemId === item.id.get
      b <- biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(q.to[List].result)
  }

  override def winningBidsByBidder(bidder: Bidder): Future[List[WinningBid]] = {
    val q = for {
      wb <- winningBidsQuery if wb.bidderId === bidder.id.get
      b <- biddersQuery if b.id === wb.bidderId
      i <- itemsQuery if i.id === wb.itemId
    } yield {
      (wb.id.?, b, i, wb.amount).mapTo[WinningBid]
    }
    db.run(q.to[List].result)
  }

  override def sortedItems: Future[List[Item]] = {
    val items = for(i <- itemsQuery) yield i
    db.run(items.sortBy(_.itemNumber).to[List].result)
  }

  override def itemById(id: Long): Future[Option[Item]] = {
    val itemOpt = for(
      i <- itemsQuery if i.id === id
    ) yield i
    db.run(itemOpt.result.headOption)
  }

  override def itemByItemNumber(itemNumber: String): Future[Option[Item]] = {
    val itemOpt = for(
      i <- itemsQuery if i.itemNumber === itemNumber
    ) yield i
    db.run(itemOpt.result.headOption)
  }

}
