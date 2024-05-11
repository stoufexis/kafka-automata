package com.stoufexis.lib.state

trait Snapshot[F[_], K, S] {
  def latest(partitions: Int): F[Map[K, S]]
}