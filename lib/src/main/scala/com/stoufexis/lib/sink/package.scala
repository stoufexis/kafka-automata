package com.stoufexis.lib

import org.apache.kafka.common.utils.{Bytes, Utils}

package object sink {
  def hashKey(key: Bytes, max: Int): Int =
    Utils.toPositive(Utils.murmur2(key.get())) % max
}
