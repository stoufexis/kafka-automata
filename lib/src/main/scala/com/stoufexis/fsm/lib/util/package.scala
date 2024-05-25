package com.stoufexis.fsm.lib

package object util {
  type Result[X] = Either[Throwable, X]
}
