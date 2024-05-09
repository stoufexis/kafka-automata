package com.stoufexis.lib.state

trait EmitSnapshot[F[_], S] {
  def apply(snapshot: S): F[Unit]
}