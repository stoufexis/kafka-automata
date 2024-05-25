package com.stoufexis.fsm.lib.kafka

import org.apache.kafka.clients.admin.ConfigEntry

sealed abstract class CleanupPolicy(val name: String) {
  def asConfigEntry: ConfigEntry =
    new ConfigEntry("cleanup.policy", name)
}

object CleanupPolicy {
  case object Delete  extends CleanupPolicy("delete")
  case object Compact extends CleanupPolicy("compact")
}
