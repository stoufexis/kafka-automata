package com.stoufexis.lib

import org.apache.kafka.common.utils.{Bytes, Utils}

import java.nio.ByteBuffer
import java.util.UUID

package object kafka {
  def hashKey(key: Bytes, max: Int): Int =
    Utils.toPositive(Utils.murmur2(key.get())) % max

  def uuidToBytes(uuid: UUID): Bytes = {
    val bb: ByteBuffer = ByteBuffer.allocate(16);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    Bytes.wrap(bb.array())
  }
}
