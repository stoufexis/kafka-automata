package com.stoufexis.fsm.examples.voting

import com.stoufexis.fsm.lib.config.Topic
import scala.concurrent.duration._
import cats.effect.kernel.Sync

case class VotesConfig(
  bootstrapServers:   String         = "localhost:9092",
  commandsTopic:      Topic          = Topic("vote-commands", 10, 1),
  eventsTopic:        Topic          = Topic("vote-events", 10, 1),
  updatesTopic:       Topic          = Topic("vote-updates", 10, 1),
  stateTopic:         Topic          = Topic("state-snapshots", 10, 1),
  consumerGroupId:    String         = "group1",
  consumerBatchEvery: FiniteDuration = 250.millis,
  producerLinger:     FiniteDuration = 5.millis,
  producerBatchSize:  Int            = 16000
)

object VotesConfig {
  // This is currently mocking an effectful loading of config
  // TODO: load from env
  def load[F[_]: Sync]: F[VotesConfig] =
    Sync[F].pure(VotesConfig())
}
