package com.stoufexis.lib.kafka

import cats.implicits._
import cats.Show
import com.stoufexis.lib.kafka.ProjectedPartitioner.IllegalSourcePartition
import org.apache.kafka.common.utils.Bytes

case class ProjectedPartitioner(
  val sourceTopicPartitions:    Int,
  val projectedTopicPartitions: Int
) {
  val projectedPartitionsPerSourcePartitionsCnt: Int =
    (projectedTopicPartitions * 1.0 / sourceTopicPartitions).ceil.toInt

  def getPartitionForSourceTopic(key: Bytes): Int =
    hashKey(key, sourceTopicPartitions)

  def getPartitionForProjectedTopic(key: Bytes): Int =
    hashKey(key, projectedTopicPartitions)

  /** Does not check if input is a valid partition
   */
  def projectUnsafe(sourcePartition: Int): List[Int] = {
    def f(x: Int): Int =
      (sourcePartition + x * sourceTopicPartitions) % projectedTopicPartitions

    List.iterate(0, projectedPartitionsPerSourcePartitionsCnt)(f)
  }

  def project(
    sourcePartition: Int
  ): Either[IllegalSourcePartition, List[Int]] =
    Either.cond(
      test  = sourcePartition <= sourceTopicPartitions,
      right = projectUnsafe(sourcePartition),
      left  = IllegalSourcePartition(sourcePartition, sourceTopicPartitions)
    )

  def project(sourcePartitions: List[Int]): Either[IllegalSourcePartition, List[Int]] =
    sourcePartitions.flatTraverse(project)
}

object ProjectedPartitioner {
  case class IllegalSourcePartition(
    sourcePartition:          Int,
    sourceTopicMaxPartitions: Int
  ) extends RuntimeException {
    override def getMessage(): String =
      s"Input source partition ${sourcePartition} greater than expected max ${sourceTopicMaxPartitions}."
  }

  implicit val showPP: Show[ProjectedPartitioner] = cats.derived.semiauto.show
}
