package com.stoufexis.fsm.lib.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.ConfigResource

/** Description of a kafka topic
  */
case class Topic(name: String, partitions: Int, replicationFactor: Short) {
  def asNewTopic: NewTopic =
    new NewTopic(name, partitions, replicationFactor)

  def asConfigResource: ConfigResource =
    new ConfigResource(ConfigResource.Type.TOPIC, name)
}
