package com.stoufexis.fsm.lib.kafka

import cats.effect.kernel._
import cats.implicits._
import com.stoufexis.fsm.lib.config.Topic
import com.stoufexis.fsm.lib.kafka.CleanupPolicy
import fs2.kafka._
import org.apache.kafka.clients.admin.AlterConfigOp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.DurationInt

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
    client: KafkaAdminClient[F],
    log:    Logger[F]
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
      _ <- log.info(s"Created topic $topic")
    } yield ()

  def deleteExistingTopics[F[_]: Async](implicit
    client: KafkaAdminClient[F],
    log:    Logger[F]
  ): F[Unit] =
    for {
      existing <- client.listTopics.names
      _        <- client.deleteTopics(existing.toList)
      _        <- log.info(s"Deleted topics $existing")
    } yield ()

  def reset[F[_]: Async](
    bootstrapServers: String,
    stateTopic:       Topic,
    inputTopic:       Topic,
    outputTopics:     List[Topic]
  ): F[Unit] =
    kafkaAdminClientResource[F](bootstrapServers).use { implicit client =>
      Slf4jLogger.fromClass(Setup.getClass).flatMap { implicit log =>
        for {
          _ <- deleteExistingTopics
          _ <- Async[F].sleep(5.seconds)
          _ <- createTopic(stateTopic, CleanupPolicy.Compact)
          _ <- createTopic(inputTopic, CleanupPolicy.Delete)
          _ <- outputTopics.traverse(createTopic(_, CleanupPolicy.Delete))
        } yield ()
      }
    }
}
