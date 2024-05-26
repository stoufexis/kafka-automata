package com.stoufexis.fsm.examples.voting

import cats.effect.kernel.Sync
import fs2.kafka._
import io.circe._
import io.circe.syntax._
import io.circe.parser.decode

package object codec {
  def jsonSerializer[F[_]: Sync, A: Encoder]: Serializer[F, A] =
    Serializer[F, String].contramap[A](_.asJson.printWith(Printer.noSpaces))

  def jsonDeserializer[F[_]: Sync, A: Decoder]: Deserializer[F, A] =
    Deserializer[F, String].flatMap { str =>
      decode(str) match {
        case Left(err)    => Deserializer.fail(err)
        case Right(value) => Deserializer.const(value)
      }
    }

  implicit class JsonWithType(json: Json) {
    def withType(typ: String): Json =
      json.deepMerge(Json.obj("type" -> typ.asJson))
  }
}
