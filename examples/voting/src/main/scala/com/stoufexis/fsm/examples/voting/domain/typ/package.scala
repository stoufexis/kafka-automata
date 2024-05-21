package com.stoufexis.fsm.examples.voting.domain

package object typ {
  case class ItemId(str: String)          extends AnyVal
  case class UserId(str: String)          extends AnyVal
  case class CommandId(str: String)       extends AnyVal
}
