package com.stoufexis.fsm.lib

import fs2.Chunk
import org.apache.kafka.common.utils.Bytes

import java.nio.ByteBuffer
import java.util.UUID

package object util {
  def uuidToBytes(uuid: UUID): Bytes = {
    val bb: ByteBuffer = ByteBuffer.allocate(16);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    Bytes.wrap(bb.array())
  }

  def chunkToMap[A, K, V](chunk: Chunk[A], split: A => (K, V)): Map[K, V] =
    ???

  def chunkToMap[K, V](chunk: Chunk[(K, V)]): Map[K, V] =
    ???
}
