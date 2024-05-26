package com.stoufexis.fsm.lib.config

import cats.effect.kernel._
import fs2.kafka._
import fs2.Stream
import scala.concurrent.duration.FiniteDuration

case class ProducerConfig(
  bootstrapServers: String,
  linger:           FiniteDuration,
  batchSize:        Int
) {
  def makeProducer[F[_]: Async, K, V](
    txId: String
  )(implicit
    keySerializer:   Serializer[F, K],
    valueSerializer: Serializer[F, V]
  ): Stream[F, TransactionalKafkaProducer[F, K, V]] =
    TransactionalKafkaProducer.stream(
      TransactionalProducerSettings(
        txId,
        ProducerSettings(keySerializer, valueSerializer)
          .withBootstrapServers(bootstrapServers)
          .withEnableIdempotence(true)
          .withLinger(linger)
          .withBatchSize(batchSize)
          .withRetries(3) // Im not sure about this number.
      )
    )
}