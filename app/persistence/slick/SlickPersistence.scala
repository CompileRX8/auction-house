package persistence.slick

import models.Organization
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.PostgresDriver.api._
import slick.lifted.Shape

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}

trait SlickPersistence {

  val db = Database.forConfig("auctiondb")

  def deleteHandler[DomainType](id: Long, forId: (Long) => Future[Option[DomainType]])
                               (deleteAction: => DBIOAction[Int, NoStream, Effect.Write]): Future[Option[DomainType]] = {
    forId(id) map {
      case opt@Some(_) =>
        db.run(deleteAction)
        opt
      case None => None
    }
  }

  def editHandler[DomainType](optId: Option[Long], domainObj: DomainType, forId: (Long) => Future[Option[DomainType]])
                             (editAction: (Long) => DBIOAction[Int, NoStream, Effect.Write]): Future[Option[DomainType]] = {
    optId match {
      case Some(id) =>
        forId(id) map {
          case opt@Some(_) =>
            db.run(editAction(id))
            opt
          case None => None
        }
      case None => Future(None)
    }
  }

  def createHandler[DomainType, RowType](domainObj: DomainType, tableQuery: TableQuery[RowType], idMap: (RowType) => Rep[Long], idMapperShape: Shape[FlatShapeLevel, Rep[Long], Long, _])
                                        (rowMapper: (RowType, Long) => Option[DomainType])
                                        (domainMapper: (DomainType) => RowType)
  : Future[Option[DomainType]] = ??? /*{
    val baseTableRow = tableQuery.baseTableRow
    val a = tableQuery.returning(tableQuery.map(idMap)(idMapperShape)) into rowMapper += domainMapper(domainObj)
    val rc = db.run(a)
    rc
  } */

  def mapOption[R, D](f: (R) => D)(opt: Option[R]) = {
    opt map {
      f(_)
    }
  }

  def mapSeq[R, D](f: (R) => D)(seq: Seq[R]) = {
    seq map {
      f(_)
    } toList
  }

}
