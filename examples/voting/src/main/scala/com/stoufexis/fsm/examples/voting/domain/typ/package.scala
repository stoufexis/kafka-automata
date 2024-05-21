package com.stoufexis.fsm.examples.voting.domain

import io.chrisdavenport.fuuid.FUUID

package object typ {
  case class ItemId(str: FUUID)        extends AnyVal
  case class UserId(str: FUUID)        extends AnyVal
  case class CommandId(str: FUUID)     extends AnyVal
  case class EventId(str: FUUID)       extends AnyVal
  case class UpdateId(str: FUUID)      extends AnyVal
  case class CorrelationId(str: FUUID) extends AnyVal
}
