package com.stoufexis.fsm.lib.typeclass

import fs2.kafka._
import fs2._

trait ToRecords[F[_], V] {
  def apply(a: V): F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]]
}

object ToRecords {
  def apply[F[_], A: ToRecords[F, *]]: ToRecords[F, A] = implicitly
}