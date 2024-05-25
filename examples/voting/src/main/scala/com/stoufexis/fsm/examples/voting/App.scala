package com.stoufexis.fsm.examples.voting

import cats.effect._
import com.stoufexis.fsm.examples.voting.domain.message.VoteCommand
import com.stoufexis.fsm.examples.voting.domain.typ._
import com.stoufexis.fsm.examples.voting.fsm._
import com.stoufexis.fsm.lib.Pipeline
import com.stoufexis.fsm.lib.kafka.Setup
import com.stoufexis.fsm.lib.typeclass.ToRecords
import fs2.Stream
import io.chrisdavenport.fuuid.FUUIDGen

object Main extends IOApp.Simple {
  override def run: IO[Unit] =
    runStream[IO].compile.drain

  def runStream[F[_]: Async: FUUIDGen]: Stream[F, Unit] = for {
    config <-
      Stream.eval(VotesConfig.load[F])

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

    pipeline: Pipeline[F, ItemId, Votes, VoteCommand, Output] <-
      Stream.eval {
        Pipeline[F, ItemId, Votes, VoteCommand, Output](
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

    _ <-
      pipeline.process(VoteFSM[F])

  } yield ()

}
