package persistence.anorm

import java.sql.Connection

import akka.actor.ActorRef
import anorm.SqlParser._
import anorm._
import models.{Bidder, BidderException, Payment}
import persistence.BiddersPersistence
import play.api.Play.current
import scala.language.postfixOps

import scala.util.Try

object BiddersPersistenceAnorm extends AnormPersistence with BiddersPersistence {
  val bidderSQL =
    """
       select "id", "name" from "bidder"
    """.stripMargin

  val bidderMapper = {
    get[Long]("id") ~
      get[String]("name") map {
      case id ~ name => Bidder(Some(id), name)
    }
  }

  val paymentSQL =
    """
      |select p."id", p."bidder_id", p."description", p."amount", b."name"
      |from "payment" p
      |inner join "bidder" b on p."bidder_id" = b."id"
    """.stripMargin

  val paymentMapper = {
    get[Long]("id") ~
      get[Long]("bidder_id") ~
      get[String]("description") ~
      get[BigDecimal]("amount") ~
      get[String]("name") map {
      case id ~ bidderId ~ description ~ amount ~ name =>
        Payment(Some(id), Bidder(Some(bidderId), name), description, amount)
    }
  }

  override def load(biddersActor: ActorRef): Try[Boolean] = withDBConnection {
    implicit c =>
    SQL(bidderSQL).as(bidderMapper *).foreach { bidder =>
      biddersActor ! bidder
    }

    SQL(paymentSQL).as(paymentMapper *).foreach { payment =>
      biddersActor ! payment
    }

    true
  }

  val paymentsByBidderSQL = SQL(paymentSQL + """ where p."bidder_id" = {bidder_id} """.stripMargin)

  override def paymentsByBidder(bidder: Bidder): Try[List[Payment]] = withDBConnection {
    implicit c =>
    paymentsByBidderSQL.on(
      'bidder_id -> bidder.id.get
    ).as(paymentMapper *)
  }

  val sortedBiddersSQL = SQL(bidderSQL + """ order by "name" """.stripMargin)

  override def sortedBidders: Try[List[Bidder]] = withDBConnection {
    implicit c =>
      sortedBiddersSQL.as(bidderMapper *)
  }

  val bidderByIdSQL = SQL(bidderSQL + """ where "id" = {id} """.stripMargin)

  override def bidderById(id: Long): Try[Option[Bidder]] = withDBConnection {
    implicit c =>
      bidderByIdSQL.on(
        'id -> id
      ).as(bidderMapper.singleOpt)
  }

  val bidderUpdateSQL = SQL(
    """
      |update "bidder"("name")
      |values ({name})
      |where "id" = {id}
    """.stripMargin
  )

  override def edit(bidder: Bidder): Try[Bidder] = withDBConnection {
    implicit c =>
      bidderUpdateSQL.on(
        'name -> bidder.name,
        'id -> bidder.id.get
      ).executeUpdate() match {
        case 1 => bidder
        case _ => throw new BidderException(s"Unable to edit bidder with unique ID ${bidder.id.get}")
      }
  }

  val bidderDeleteSQL = SQL(
    """
      |delete "bidder" where "id" = {id}
    """.stripMargin
  )

  override def delete(bidder: Bidder): Try[Bidder] = withDBConnection {
    implicit c =>
      bidderDeleteSQL.on(
        'id -> bidder.id.get
      ).executeUpdate() match {
        case 1 => bidder
        case _ => throw new BidderException(s"Unable to delete bidder with unique ID ${bidder.id.get}")
      }
  }

  val bidderByNameSQL = SQL(bidderSQL + """ where "name" = {name} """.stripMargin)

  override def bidderByName(name: String): Try[Option[Bidder]] = withDBConnection {
    implicit c =>
      bidderByNameSQL.on(
        'name -> name
      ).as(bidderMapper.singleOpt)
  }

  val bidderInsertSQL = SQL(
    """
      |insert "bidder"("name")
      |values ({name})
    """.stripMargin
  )

  override def create(bidder: Bidder): Try[Bidder] = withDBConnection {
    implicit c =>
      bidderInsertSQL.on(
        'name -> bidder.name
      ).executeInsert(bidderMapper.single)
  }

  val paymentInsertSQL = SQL(
    """
      |insert "payment"("bidder_id", "description", "amount")
      |values ({bidder_id}, {description}, {amount})
    """.stripMargin
  )

  override def create(payment: Payment): Try[Payment] = withDBConnection {
    implicit c =>
      bidderInsertSQL.on(
        'bidder_id -> payment.bidder.id.get,
        'description -> payment.description,
        'amount -> payment.amount).executeInsert(paymentMapper.single)
  }
}
