package com.stoufexis.fsm.lib.kafka

import cats.effect.kernel._
import cats.implicits._
import com.stoufexis.fsm.lib.config.Topic
import com.stoufexis.fsm.lib.kafka.CleanupPolicy
import fs2.kafka._
import org.apache.kafka.clients.admin.AlterConfigOp

object Setup {
  def kafkaAdminClientResource[F[_]: Async](
    bootstrapServers: String
  ): Resource[F, KafkaAdminClient[F]] =
    KafkaAdminClient
      .resource[F](AdminClientSettings(bootstrapServers))

  def createTopic[F[_]: Async](
    topic:         Topic,
    cleanupPolicy: CleanupPolicy
  )(implicit
    client: KafkaAdminClient[F]
  ): F[Unit] =
    for {
      _ <- client.createTopic(topic.asNewTopic)
      _ <- client.alterConfigs {
        Map(topic.asConfigResource ->
          List(
            new AlterConfigOp(
              cleanupPolicy.asConfigEntry,
              AlterConfigOp.OpType.SET
            )
          ))
      }
    } yield ()

  def deleteExistingTopics[F[_]: Async](implicit client: KafkaAdminClient[F]): F[Unit] =
    for {
      existing <- client.listTopics.names
      _        <- client.deleteTopics(existing.toList)
    } yield ()

  def reset[F[_]: Async](
    stateTopic:       Topic,
    inputTopic:       Topic,
    outputTopic:      Topic,
    bootstrapServers: String
  ): F[Unit] =
    kafkaAdminClientResource[F](bootstrapServers)
      .use { implicit client =>
        for {
          _ <- deleteExistingTopics
          _ <- createTopic(stateTopic, CleanupPolicy.Compact)
          _ <- createTopic(inputTopic, CleanupPolicy.Delete)
          _ <- createTopic(outputTopic, CleanupPolicy.Delete)
        } yield ()

      }
}
