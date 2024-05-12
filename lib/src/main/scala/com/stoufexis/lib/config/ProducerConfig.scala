package com.stoufexis.lib.config

import cats.effect.kernel._
import fs2.kafka._
import scala.concurrent.duration.FiniteDuration

case class ProducerConfig[F[_]: Async, K, V](
  txId:             String,
  bootstrapServers: String,
  idempotent:       Boolean,
  linger:           FiniteDuration,
  batchSize:        Int,
  keySerializer:    Serializer[F, K],
  valueSerializer:  Serializer[F, V]
) {
  private val producerSettings: TransactionalProducerSettings[F, K, V] =
    TransactionalProducerSettings(
      txId,
      ProducerSettings(keySerializer, valueSerializer)
        .withBootstrapServers(bootstrapServers)
        .withEnableIdempotence(idempotent)
        .withLinger(linger)
        .withBatchSize(batchSize)
    )

  def makeProducer: Resource[F, TransactionalKafkaProducer[F, K, V]] =
    TransactionalKafkaProducer.resource(producerSettings)
}
