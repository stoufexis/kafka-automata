package com.stoufexis.lib

case class FSM[F[_], S, I, O](f: (S, I) => F[(S, O)])
