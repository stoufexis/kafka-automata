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

  private def projectUnchecked(sourcePartition: Int): List[Int] = {
    def f(x: Int): Int =
      (sourcePartition + x * sourceTopicPartitions) % projectedTopicPartitions

    List.iterate(0, projectedPartitionsPerSourcePartitionsCnt)(f)
  }

  def project(
    sourcePartition: Int
  ): Either[IllegalSourcePartition, List[Int]] =
    Either.cond(
      test  = sourcePartition <= sourceTopicPartitions,
      right = projectUnchecked(sourcePartition),
      left  = IllegalSourcePartition(sourcePartition, sourceTopicPartitions)
    )

  def project(sourcePartitions: List[Int]): Either[IllegalSourcePartition, List[Int]] =
    sourcePartitions.flatTraverse(project)

  def projectUnsafe(sourcePartition: Int): List[Int] =
    project(sourcePartition) match {
      case Right(p) => p
      case Left(t)  => throw t
    }

  def projectUnsafe(sourcePartitions: List[Int]): List[Int] =
    sourcePartitions.flatMap(projectUnsafe)
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
