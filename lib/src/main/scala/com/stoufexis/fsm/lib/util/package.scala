package com.stoufexis.fsm.lib

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
}
