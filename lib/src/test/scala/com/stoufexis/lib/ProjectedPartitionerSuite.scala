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
        val src = p.getPartitionForSourceTopic(uuidToBytes(uuid))
        val prj = p.getPartitionForProjectedTopic(uuidToBytes(uuid))

        exists(p.project(src)) { ps =>
          expect(ps contains prj)
          expect(ps.length == p.projectedPartitionsPerSourcePartitionsCnt)
        }
      }
    }
  }
}
