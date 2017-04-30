package persistence.slick

import scala.language.postfixOps

trait SlickPersistence {

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
