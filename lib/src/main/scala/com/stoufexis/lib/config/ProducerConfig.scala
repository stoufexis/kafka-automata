package com.stoufexis.lib.config

import cats.effect.kernel._
import fs2.kafka._
import scala.concurrent.duration.FiniteDuration

case class ProducerConfig[F[_]: Async, K, V](
  bootstrapServers: String,
  linger:           FiniteDuration,
  batchSize:        Int,
  keySerializer:    Serializer[F, K],
  valueSerializer:  Serializer[F, V]
) {
  def makeProducer(txId: String): Resource[F, TransactionalKafkaProducer[F, K, V]] =
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

object ProducerConfig {
  def apply[F[_]: Async, K: Serializer[F, *], V: Serializer[F, *]](
    bootstrapServers: String,
    linger:           FiniteDuration,
    batchSize:        Int
  ): ProducerConfig[F, K, V] =
    ProducerConfig(
      bootstrapServers = bootstrapServers,
      linger           = linger,
      batchSize        = batchSize,
      keySerializer    = implicitly,
      valueSerializer  = implicitly
    )
}
