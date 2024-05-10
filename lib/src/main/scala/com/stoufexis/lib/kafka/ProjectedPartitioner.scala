package com.stoufexis.lib.kafka

import cats.Show
import cats.implicits._
import com.stoufexis.lib.kafka.hashKey
import org.apache.kafka.common.utils.Bytes

case class ProjectedPartitioner(
  val sourceTopicPartitions:    Int,
  val projectedTopicPartitions: Int
) {
  val projectedPartitionsPerSourcePartitionsCnt: Int =
    (projectedTopicPartitions * 1.0 / sourceTopicPartitions).ceil.toInt

  def getPartitionForProjectedTopic(
    sourcePartition: Int,
    key:             Bytes
  ): Int = hashKey(key, project(sourcePartition).length)

  def project(sourcePartition: Int): Vector[Int] = {
    def f(x: Int): Int =
      (sourcePartition + x * sourceTopicPartitions) % projectedTopicPartitions

    Vector.iterate(0, projectedPartitionsPerSourcePartitionsCnt)(f)
  }
}

object ProjectedPartitioner {
  implicit val showPP: Show[ProjectedPartitioner] =
    cats.derived.semiauto.show
}
