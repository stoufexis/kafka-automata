package com.stoufexis.fsm.lib.config

import cats.effect.kernel._
import fs2.kafka._
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
  ): Resource[F, TransactionalKafkaProducer[F, K, V]] =
    TransactionalKafkaProducer.resource(
      TransactionalProducerSettings(
        txId,
        ProducerSettings(keySerializer, valueSerializer)
          .withBootstrapServers(bootstrapServers)
          .withEnableIdempotence(true)
          .withLinger(linger)
          .withBatchSize(batchSize)
      )
    )
}