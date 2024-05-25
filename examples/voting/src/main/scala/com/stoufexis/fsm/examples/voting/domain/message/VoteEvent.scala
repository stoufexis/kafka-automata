package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.effect.kernel.Sync
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ.EventId
import fs2.kafka._
import io.chrisdavenport.fuuid.FUUIDGen
import io.circe.Encoder
import io.circe.generic.semiauto._

sealed trait VoteEvent {
  val eventId: EventId
  val cmd:     VoteCommand
}

object VoteEvent {
  case class CommandExecuted(eventId: EventId, cmd: VoteCommand)                 extends VoteEvent
  case class CommandRejected(eventId: EventId, cmd: VoteCommand, reason: String) extends VoteEvent

  def commandExecuted[F[_]: FUUIDGen: Functor](cmd: VoteCommand): F[VoteEvent] =
    FUUIDGen[F].random map (id => CommandExecuted(EventId(id), cmd))

  def commandRejected[F[_]: FUUIDGen: Functor](cmd: VoteCommand, reason: String): F[VoteEvent] =
    FUUIDGen[F].random map (id => CommandRejected(EventId(id), cmd, reason))

  implicit val encodeEvent: Encoder[VoteEvent] = {
    val encodeCommandExecuted: Encoder[CommandExecuted] =
      deriveEncoder[CommandExecuted]

    val encodeCommandRejected: Encoder[CommandRejected] =
      deriveEncoder[CommandRejected]

    Encoder.instance {
      case e: CommandExecuted => encodeCommandExecuted(e)
      case r: CommandRejected => encodeCommandRejected(r)
    }
  }

  implicit def serializeVoteEvent[F[_]: Sync]: Serializer[F, VoteEvent] =
    com.stoufexis.fsm.examples.voting.codec.jsonSerializer
}
