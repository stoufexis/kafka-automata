package com.stoufexis.lib.kafka

import cats._
import cats.effect.implicits._
import cats.implicits._
import com.stoufexis.lib.util
import fs2._
import fs2.kafka._
import scala.annotation.unused
import scala.collection.mutable

case class Batch[F[_], K, V](
  chunks:  List[(K, Chunk[V])],
  offsets: CommittableOffsetBatch[F]
) {

  /** The chunks corresponding to different keys can be processed in parallel. This does not
    * preserve the order of the output. Each Key is supposed to represent an independent
    * "session" of the state machine, so no cross-key relationship is assumed or guaranteed.
    */
  def parProcess[Out](
    f:   (K, Chunk[V]) => F[Out],
  )(implicit
    @unused ev:  Parallel.Aux[F, F],
    @unused ev2: CommutativeApplicative[F]
  ): F[List[(K, Out)]] =
    chunks.parUnorderedTraverse { case (k, chunk) =>
      f(k, chunk) map ((k, _))
    }
}

object Batch {

  /** Assumes that chunk is ordered by (topicpartition, offset).
    */
  def apply[F[_]: Applicative, Key, Value](
    chunk: Chunk[CommittableConsumerRecord[F, Key, Value]]
  ): Batch[F, Key, Value] =
    util.chunkToBatch(chunk)
}
