package com.stoufexis.fsm.lib.typeclass

import fs2.kafka._

trait FromRecord[F[_], K, A] {
  def apply(cr: ConsumerRecord[K, Array[Byte]]): F[Option[A]]
}

object FromRecord {
  def apply[F[_], K, A: FromRecord[F, K, *]]: FromRecord[F, K, A] = implicitly
}