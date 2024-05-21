package com.stoufexis.fsm.examples.voting.fsm

import com.stoufexis.fsm.examples.voting.domain.message._
import fs2.Chunk

sealed trait Output

object Output {
  // Would benefit from scala 3 union types
  case class Event(event: VoteEvent)        extends Output
  case class Update(event: VoteStateUpdate) extends Output

  def both(event: VoteEvent, update: Option[VoteStateUpdate]): Chunk[Output] =
    update match {
      case None      => Chunk.singleton(Event(event))
      case Some(upd) => Chunk(Update(upd), Event(event))
    }
}
