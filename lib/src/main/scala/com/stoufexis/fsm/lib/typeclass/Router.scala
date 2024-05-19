package com.stoufexis.fsm.lib.typeclass

import fs2.kafka._
import fs2._

trait Router[F[_], V] {
  def toProducerRecords(a: V): F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]]
}

object Router {
  def apply[F[_], A: Router[F, *]]: Router[F, A] = implicitly
}