package com.stoufexis.fsm.examples.voting

import cats.effect._
import com.stoufexis.fsm.examples.voting.domain.message.VoteCommand
import com.stoufexis.fsm.examples.voting.domain.typ._
import com.stoufexis.fsm.examples.voting.fsm._
import com.stoufexis.fsm.lib.Pipeline
import com.stoufexis.fsm.lib.kafka.Setup
import com.stoufexis.fsm.lib.typeclass.ToRecords
import fs2.Stream

object Main extends IOApp.Simple {
  override def run: IO[Unit] =
    runStream.compile.drain

  def runStream: Stream[IO, Unit] = for {
    config <-
      Stream.eval(VotesConfig.load[IO])

    _ <-
      Stream.eval {
        Setup.reset[IO](
          bootstrapServers = config.bootstrapServers,
          stateTopic       = config.stateTopic,
          inputTopic       = config.commandsTopic,
          outputTopics     = List(config.eventsTopic, config.updatesTopic)
        )
      }

    toRecords: ToRecords[IO, Output] =
      Output.toRecords[IO](
        eventsTopic  = config.eventsTopic.name,
        updatesTopic = config.updatesTopic.name
      )

    pipeline: Pipeline[IO, ItemId, Votes, VoteCommand, Output] =
      Pipeline(
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

    _ <-
      pipeline.process(VoteFSM[IO])

  } yield ()

}
