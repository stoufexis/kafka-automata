package com.stoufexis.lib

import cats.Show
import com.stoufexis.lib.kafka._
import org.scalacheck.Gen
import weaver._
import weaver.scalacheck._

import java.util.UUID

object ProjectedPartitionerSuite extends SimpleIOSuite with Checkers {
  implicit val showBytes: Show[UUID] =
    new Show[UUID] {
      override def show(t: UUID): String = t.toString()
    }

  val genUUID: Gen[UUID] = Gen.delay(UUID.randomUUID)

  test("key partitioning is consistent with projection") {
    val partitioners: Gen[ProjectedPartitioner] =
      for {
        source    <- Gen.choose(1, 10)
        projected <- Gen.choose(1, 10)
      } yield ProjectedPartitioner(source, projected)

    forall(partitioners) { p =>
      forall(genUUID) { uuid =>
        forall(Gen.choose(0, 100)) { inPartition =>
          val prj: Int =
            p.getPartitionForProjectedTopic(inPartition, uuidToBytes(uuid))

          // Given projected partition is a valid partition for the topic
          expect(0 <= prj && prj < p.projectedTopicPartitions)

          val candidates: Vector[Int] = p.project(inPartition)

          expect(candidates contains prj)

          expect(
            candidates.length == p.projectedPartitionsPerSourcePartitionsCnt
          )
        }
      }
    }
  }
}
