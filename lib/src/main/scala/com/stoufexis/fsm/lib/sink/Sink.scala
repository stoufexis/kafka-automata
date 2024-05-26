package com.stoufexis.fsm.lib.sink

import cats.effect.kernel._
import cats.implicits._
import com.stoufexis.fsm.lib.config._
import com.stoufexis.fsm.lib.consumer.ProcessedBatch
import com.stoufexis.fsm.lib.sink.hashKey
import com.stoufexis.fsm.lib.typeclass._
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import org.typelevel.log4cats.Logger

trait Sink[F[_], InstanceId, State, Out] {
  trait ForPartition {
    val latestState: Map[InstanceId, State]

    def emit(batch: ProcessedBatch[F, InstanceId, State, Out]): F[Unit]
  }

  /** @param inputPartition
    * @return
    *   A sink for the given input partition. Along with the usual failure reasons, the resulting
    *   stream will fail in case of a failure to deserialize a state snapshot
    */
  def forPartition(inputPartition: TopicPartition): Stream[F, ForPartition]
}

object Sink {

  def fromKafka[
    F[_]:       Async,
    InstanceId: Serializer[F, *]: Deserializer[F, *],
    S:          Serializer[F, *]: Deserializer[F, *],
    V
  ](
    producerConfig:       ProducerConfig,
    consumeStateConfig:   ConsumerConfig,
    stateTopic:           String,
    stateTopicPartitions: Int,
    toRecords:            ToRecords[F, V]
  )(implicit
    log: Logger[F]
  ): Sink[F, InstanceId, S, V] = new Sink[F, InstanceId, S, V] {

    // The TopicPartition to which we send state snapshots for this input TopicPartition.
    // The mapping will break for older states if a Sink is re-initialized with a different stateTopicPartitions value
    // i.e. you can't simply change the partition count of the stateTopic, you need to do something more elaborate.
    def mappedTopicPartition(topicPartition: TopicPartition): TopicPartition =
      new TopicPartition(
        stateTopic,
        hashKey(topicPartition.toString, stateTopicPartitions)
      )

    def makeProducer(
      topicPartition: TopicPartition
    ): Stream[F, TransactionalKafkaProducer[F, Array[Byte], Array[Byte]]] =
      producerConfig.makeProducer(topicPartition.toString)

    // fails if theres a deserialization error
    def compileLatestState(topicPartition: TopicPartition): Stream[F, Map[InstanceId, S]] = {
      val mappedTp: TopicPartition =
        mappedTopicPartition(topicPartition)

      for {
        // generate a random groupId, since we don't want
        // this to be part of a consumer group
        consumer: KafkaConsumer[F, InstanceId, S] <-
          consumeStateConfig.makeConsumer[F, InstanceId, S](
            topicPartition = mappedTp,
            groupId        = None,
            seek           = ConsumerConfig.Seek.ToBeginning
          )

        offsets: Map[TopicPartition, Long] <-
          Stream.eval(consumer.endOffsets(Set(mappedTp)))

        // unsafe but it should ALWAYS succeed
        endOffset: Long =
          offsets(mappedTp)

        state: Map[InstanceId, S] <-
          consumer
            .stream
            .takeWhile { record =>
              // At this point, no other instance will be producing state snapshots
              // for the keys this instance cares about, so we can assume that there
              // wont be any state snapshots that we currently care about with offset > endOffset.
              record.record.offset <= endOffset
            }
            .fold(Map.empty[InstanceId, S]) { (acc, record) =>
              acc.updated(record.record.key, record.record.value)
            }

        _ <-
          Stream.eval(log.info(s"Compiled state for $topicPartition"))

      } yield state
    }

    override def forPartition(topicPartition: TopicPartition): Stream[F, ForPartition] =
      for {
        producer <- makeProducer(topicPartition)
        state    <- compileLatestState(topicPartition)
      } yield new ForPartition {
        val mappedTp: TopicPartition =
          mappedTopicPartition(topicPartition)

        override val latestState: Map[InstanceId, S] = state

        override def emit(batch: ProcessedBatch[F, InstanceId, S, V]): F[Unit] = {
          // TODO: Is empty headers ok?
          def serializeKey(k: InstanceId): F[Array[Byte]] =
            Serializer[F, InstanceId].serialize(stateTopic, Headers.empty, k)

          def serializeState(s: Option[S]): F[Array[Byte]] =
            Serializer[F, Option[S]].serialize(stateTopic, Headers.empty, s)

          val stateRecords: F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]] =
            Chunk.from(batch.statesMap).traverse { case (k, state) =>
              (serializeKey(k), serializeState(state)).mapN {
                case (keyBytes, stateBytes) =>
                  // We have to know to which partition a state for a key is mapped
                  // so we simply determine it ourselves
                  ProducerRecord(stateTopic, keyBytes, stateBytes)
                    .withPartition(mappedTp.partition)
              }
            }

          val valueRecords: F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]] =
            batch.values.flatTraverse(toRecords(_))

          (stateRecords, valueRecords).flatMapN { (s, v) =>
            val records: Chunk[CommittableProducerRecords[F, Array[Byte], Array[Byte]]] =
              Chunk.singleton(CommittableProducerRecords.chunk(s ++ v, batch.offset))

            producer.produce(records).void
          }
        }
      }
  }
}
