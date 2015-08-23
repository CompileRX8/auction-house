package persistence.anorm

import anorm._
import play.api.db.DB
import play.api.Play.current

trait AnormPersistence {
  val db = DB

  def withDBConnection[T](f: () => T): T = db.withConnection {
    implicit c =>
      f()
  }
}
