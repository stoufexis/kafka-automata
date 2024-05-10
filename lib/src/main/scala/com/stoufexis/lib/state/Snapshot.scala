package com.stoufexis.lib.state

import scala.annotation.unused
import cats.Functor
import cats.implicits._
import com.stoufexis.lib.typeclass.Empty

trait Snapshot[F[_], K, S] {
  def latest(partitions: Vector[Int]): F[Map[K, S]]
}


