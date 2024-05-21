package com.stoufexis.fsm.examples.voting.fsm

import com.stoufexis.fsm.examples.voting.domain.message._
import fs2.Chunk

case class Output(either: Either[VoteEvent, VoteStateUpdate])

object Output {
  def event(event: VoteEvent): Output = ???
  def update(update: VoteStateUpdate): Output = ???

  def both(event: VoteEvent, update: Option[VoteStateUpdate]): Chunk[Output] =
    update match {
      case None      => Chunk.singleton(Output.event(event))
      case Some(upd) => Chunk(Output.update(upd), Output.event(event))
    }

}
