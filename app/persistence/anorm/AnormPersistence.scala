package persistence.anorm

import anorm._
import play.api.db.DB
import play.api.Play.current

trait AnormPersistence {
  val db = DB

//  val biddersQueryAll = withDBConnection[SQL] { SQL("select * from bidders") }
//  val paymentsQueryAll = withDBConnection[SQL] { SQL("select * from payments") }

  def withDBConnection[T](f: () => T): T = db.withConnection {
    implicit c =>
      f()
  }
}
