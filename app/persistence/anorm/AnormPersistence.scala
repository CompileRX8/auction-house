package persistence.anorm

import java.sql.Connection

import anorm._
import play.api.db.DB
import play.api.Play.current

import scala.util.Try

trait AnormPersistence {
  val db = DB

  def withDBConnection[T](f: (Connection) => T): Try[T] = Try {
    db.withConnection {
      implicit c =>
        f(c)
    }
  }
}
