package com.stoufexis.fsm.examples.voting

import cats.effect._
import cats.effect.kernel.Async
import com.stoufexis.fsm.examples.voting.domain.message.VoteCommand
import com.stoufexis.fsm.examples.voting.domain.typ._
import com.stoufexis.fsm.examples.voting.fsm._
import com.stoufexis.fsm.lib.Pipeline
import com.stoufexis.fsm.lib.config._
import com.stoufexis.fsm.lib.kafka.Setup
import com.stoufexis.fsm.lib.typeclass.ToRecords
import fs2._
import fs2.kafka._
import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.fuuid.FUUIDGen
import io.circe.Json
import io.circe.Printer
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

object Main extends IOApp.Simple {
  val config = VotesConfig()

  def votes: List[VoteCommand] = {
    val item = ItemId(FUUID.fromUUID(UUID.fromString("dc251f7c-a510-4913-b79f-2a8248401991")))
    val users = UserId(FUUID.fromUUID(UUID.randomUUID))

    List(
      VoteCommand.VoteStart(
        CommandId(FUUID.fromUUID(UUID.randomUUID)),
        CorrelationId(FUUID.fromUUID(UUID.randomUUID)),
        item,
        users
      ),
      VoteCommand.Upvote(
        CommandId(FUUID.fromUUID(UUID.randomUUID)),
        CorrelationId(FUUID.fromUUID(UUID.randomUUID)),
        item,
        users
      ),
      VoteCommand.Upvote(
        CommandId(FUUID.fromUUID(UUID.randomUUID)),
        CorrelationId(FUUID.fromUUID(UUID.randomUUID)),
        item,
        users
      )
    )

  }

  override def run: IO[Unit] =
    Slf4jLogger.fromClass[IO](VoteFSM.getClass()).flatMap { implicit log =>
      runStream[IO].compile.drain
    }

  def echoConsumer[F[_]: Async](topic: String): Stream[F, Unit] = {
    implicit def jsonDeserializer[F[_]: Sync]: Deserializer[F, Json] =
      codec.jsonDeserializer

    for {
      log <-
        Stream.eval(Slf4jLogger.fromName(s"echo-$topic"))

      consumer <-
        ConsumerConfig(config.bootstrapServers)
          .makeConsumer[F, String, Option[Json]](topic, None, ConsumerConfig.Seek.ToBeginning)

      record: CommittableConsumerRecord[F, String, Option[Json]] <-
        consumer.stream

      message =
        record.record.value match {
          case None        => s"Received deletion of ${record.record.key}"
          case Some(value) => s"Received ${value.printWith(Printer.noSpaces)}"
        }

      _ <-
        Stream.eval(log.info(message))
    } yield ()
  }

  def produceCommands[F[_]: Async](topic: String)(cmds: Chunk[VoteCommand]): Stream[F, Unit] =
    for {
      log: SelfAwareStructuredLogger[F] <-
        Stream.eval(Slf4jLogger.fromName(s"produce-$topic"))

      producer: TransactionalKafkaProducer.WithoutOffsets[F, ItemId, VoteCommand] <-
        ProducerConfig(
          bootstrapServers = config.bootstrapServers,
          linger           = config.producerLinger,
          batchSize        = config.producerBatchSize
        ).makeProducer[F, ItemId, VoteCommand]("tx10")

      _ <-
        Stream.eval(producer.produceWithoutOffsets(cmds.map(cmd =>
          ProducerRecord(topic, cmd.itemId, cmd)
        )))

      _ <-
        Stream.eval(log.info(s"Produced ${cmds.size} commands"))
    } yield ()

  def runStream[F[_]: Async: FUUIDGen: Logger]: Stream[F, Unit] =
    for {
      _ <-
        Stream.eval {
          Setup.reset[F](
            bootstrapServers = config.bootstrapServers,
            stateTopic       = config.stateTopic,
            inputTopic       = config.commandsTopic,
            outputTopics     = List(config.eventsTopic, config.updatesTopic)
          )
        }

      toRecords: ToRecords[F, Output] =
        Output.toRecords[F](
          eventsTopic  = config.eventsTopic.name,
          updatesTopic = config.updatesTopic.name
        )

      pipeline: (Int => Stream[F, Pipeline[F, ItemId, Votes, VoteCommand, Output]]) =
        instanceId =>
          Stream.eval {
            Pipeline[F, ItemId, Votes, VoteCommand, Output](
              instanceId           = instanceId,
              bootstrapServers     = config.bootstrapServers,
              producerLinger       = config.producerLinger,
              producerBatchSize    = config.producerBatchSize,
              consumerGroupId      = config.consumerGroupId,
              consumerBatchEvery   = config.consumerBatchEvery,
              topicIn              = config.commandsTopic.name,
              stateTopic           = config.stateTopic.name,
              stateTopicPartitions = config.stateTopic.partitions,
              toRecords            = toRecords
            )
          }

      pipe1 <- pipeline(1)
      // pipe2 <- pipeline(2)
      // pipe3 <- pipeline(3)

      _ <-
        Stream(
          pipe1.process(VoteFSM[F](1)),
          // pipe2.process(VoteFSM[F](2)),
          // pipe3.process(VoteFSM[F](3)),
          echoConsumer[F](config.eventsTopic.name),
          echoConsumer[F](config.updatesTopic.name),
          echoConsumer[F](config.stateTopic.name),
          produceCommands[F](config.commandsTopic.name)(Chunk.from(votes))
        ).parJoinUnbounded

    } yield ()

}
