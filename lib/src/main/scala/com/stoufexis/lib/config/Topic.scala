package com.stoufexis.lib.config

/**
  * Description of a kafka topic.
  * Best-fsm needs to know the partitions configuration for its input and state topics
  */
case class Topic(name: String, partitions: Int)
