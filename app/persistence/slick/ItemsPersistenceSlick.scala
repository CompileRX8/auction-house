package persistence.slick

import java.sql.SQLException

import akka.actor.ActorRef
import models.{Bidder, Item, ItemException, WinningBid}
import persistence.ItemsPersistence
//import play.api.db.slick.Config.driver.simple._

import scala.util.Try

object ItemsPersistenceSlick extends SlickPersistence with ItemsPersistence {

  override def load(itemsActor: ActorRef): Try[Boolean] = ??? /*{
    db withSession {
      implicit session =>
        try {
          itemsQuery.list map {
            _.copy()
          } foreach {
            itemsActor ! _
          }
          winningBidsQuery.list map {
            _.copy().toWinningBid
          } foreach {
            itemsActor ! _
          }
          true
        } catch {
          case _: SQLException =>
            (itemsQuery.ddl ++ winningBidsQuery.ddl).create
            true
        }
    }
  } */

  override def create(item: Item): Try[Item] = ??? /*Try {
    db withSession {
      implicit session =>
        val newItemId = (itemsQuery returning itemsQuery.map(_.id)) += item
        Item(Some(newItemId), item.itemNumber, item.category, item.donor, item.description, item.minbid, item.estvalue)
    }
  } */

  override def create(winningBid: WinningBid): Try[WinningBid] = ??? /*Try {
    db withSession {
      implicit session =>
        val newWinningBidId = (winningBidsQuery returning winningBidsQuery.map(_.id)) += WinningBidRow.fromWinningBid(winningBid)
        WinningBid(Some(newWinningBidId), winningBid.bidder, winningBid.item, winningBid.amount)
    }
  }*/

  override def delete(item: Item): Try[Item] = ??? /*Try {
    db withSession {
      implicit session =>
        val q = itemsQuery.filter(_.id === item.id.get)
        if ((q.length === 1).run && q.delete == 1) {
          item
        } else {
          throw new ItemException(s"Unable to delete item with unique ID ${item.id.get}")
        }
    }
  }*/

  override def delete(winningBid: WinningBid): Try[WinningBid] = ??? /*Try {
    db withSession {
      implicit session =>
        val q = winningBidsQuery.filter(_.id === winningBid.id.get)
        if ((q.length === 1).run && q.delete == 1) {
          winningBid
        } else {
          throw new ItemException(s"Unable to delete winning bid with unique ID ${winningBid.id.get}")
        }
    }
  }*/

  override def edit(item: Item): Try[Item] = ??? /*Try {
    db withSession {
      implicit session =>
        val q = itemsQuery.filter(_.id === item.id.get)
        if ((q.length === 1).run && q.update(item) == 1) {
          item
        } else {
          throw new ItemException(s"Unable to update item with unique ID ${item.id.get}")
        }
    }
  }*/

  override def editWinningBid(winningBidId: Long, bidder: Bidder, item: Item, amount: BigDecimal): Try[WinningBid] = ??? /*Try {
    db withSession {
      implicit session =>
        val q = winningBidsQuery.filter(_.id === winningBidId)
        val row = WinningBidRow(Some(winningBidId), bidder.id.get, item.id.get, amount)
        if ((q.length === 1).run && q.update(row) == 1)
          row.toWinningBid
        else
          throw new ItemException(s"Unable to edit winning bid with unique ID $winningBidId")
    }
  } */

  override def winningBidById(id: Long): Try[Option[WinningBid]] = ??? /*Try {
    db withSession {
      implicit session =>
        winningBidsQuery.filter(_.id === id).list.headOption.map { row => row.toWinningBid }
    }
  } */

  override def winningBidsByItem(item: Item): Try[List[WinningBid]] = ??? /*Try {
    db withSession {
      implicit session =>
        winningBidsQuery.filter(_.itemId === item.id.get).list.map { row => row.toWinningBid }
    }
  }*/

  override def winningBidsByBidder(bidder: Bidder): Try[List[WinningBid]] = ??? /*Try {
    db withSession {
      implicit session =>
        winningBidsQuery.filter(_.bidderId === bidder.id.get).list.map { row => row.toWinningBid }
    }
  }*/

  override def sortedItems: Try[List[Item]] = ??? /*Try {
    db withSession {
      implicit session =>
        itemsQuery.sortBy(_.itemNumber).list.map { row => row.copy() }
    }
  }*/

  override def itemById(id: Long): Try[Option[Item]] = ??? /*Try {
    db withSession {
      implicit session =>
        itemsQuery.filter(_.id === id).list.headOption.map { row => row.copy() }
    }
  }*/

  override def itemByItemNumber(itemNumber: String): Try[Option[Item]] = ??? /*Try {
    db withSession {
      implicit session =>
        itemsQuery.filter(_.itemNumber === itemNumber).list.headOption.map { row => row.copy() }
    }
  }*/

}
