package com.stoufexis.fsm.lib.sink

import cats.effect.kernel._
import cats.implicits._
import com.stoufexis.fsm.lib.config._
import com.stoufexis.fsm.lib.consumer.ProcessedBatch
import com.stoufexis.fsm.lib.sink.hashKey
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition

trait Sink[F[_], Key, State, Out] {
  trait ForPartition {
    def latestState: F[Map[Key, State]]
    def emit(batch: ProcessedBatch[F, Key, State, Out]): F[Unit]
  }

  def forPartition(inputPartition: TopicPartition): Resource[F, ForPartition]
}

object Sink {

  /** @param producerConfig
    * @param consumeStateConfig
    *   Assumed to be compacted and each record containing complete snapshots of states for a
    *   key
    * @param stateTopic
    * @param stateTopicPartitions
    * @param outTopic
    * @return
    */
  def fromKafka[
    F[_]: Concurrent,
    K,
    S: Serializer[F, *],
    V: Serializer[F, *]
  ](
    producerConfig:       ProducerConfig[F, K, Array[Byte]],
    consumeStateConfig:   ConsumerConfig[F, K, S],
    stateTopic:           String,
    stateTopicPartitions: Int,
    topicOut:             V => String
  ): Sink[F, K, S, V] =
    new Sink[F, K, S, V] {
      override def forPartition(topicPartition: TopicPartition): Resource[F, ForPartition] =
        producerConfig.makeProducer(topicPartition.toString).map {
          producer: TransactionalKafkaProducer[F, K, Array[Byte]] =>
            new ForPartition {
              // The TopicPartition to which we send state snapshots for this input TopicPartition.
              // The mapping will break for older states if a Sink is re-initialized with a different stateTopicPartitions value
              // i.e. you can't simply change the partition count of the stateTopic, you need to do something more elaborate.
              val mappedTopicPartition: TopicPartition =
                new TopicPartition(
                  stateTopic,
                  hashKey(topicPartition.toString, stateTopicPartitions)
                )

              override def latestState: F[Map[K, S]] = {
                val stateConsumer: Stream[F, CommittableConsumerRecord[F, K, S]] =
                  for {
                    // generate a random groupId, since we don't want
                    // this to be part of a consumer group
                    consumer: KafkaConsumer[F, K, S] <-
                      consumeStateConfig.makeConsumer(
                        topicPartition = mappedTopicPartition,
                        groupId        = None,
                        seek           = ConsumerConfig.Seek.ToBeginning
                      )

                    offsets: Map[TopicPartition, Long] <-
                      Stream.eval(consumer.endOffsets(Set(mappedTopicPartition)))

                    // unsafe but it should ALWAYS succeed
                    endOffset: Long =
                      offsets(mappedTopicPartition)

                    // At this point, no other instance will be producing state snapshots
                    // for the keys this instance cares about, so we can assume that there
                    // wont be any state snapshots that we currently care about with offset > endOffset.
                    records: CommittableConsumerRecord[F, K, S] <-
                      consumer.stream.takeWhile { record =>
                        record.record.offset <= endOffset
                      }

                  } yield records

                stateConsumer.compile.fold(Map.empty[K, S]) { (acc, record) =>
                  acc.updated(record.record.key, record.record.value)
                }
              }

              override def emit(batch: ProcessedBatch[F, K, S, V]): F[Unit] = {
                val stateRecords: F[Chunk[ProducerRecord[K, Array[Byte]]]] =
                  Chunk.from(batch.statesMap).traverse { case (k, state) =>
                    Serializer[F, S]
                      // TODO: Is empty headers ok?
                      .serialize(stateTopic, Headers.empty, state)
                      .map { stateBytes =>
                        // We have to know to which partition a state for a key is mapped
                        // so we simply determine it ourselves
                        ProducerRecord(
                          topic = stateTopic,
                          key   = k,
                          value = stateBytes
                        )
                          .withPartition(mappedTopicPartition.partition)
                      }
                  }

                val valueRecords: F[Chunk[ProducerRecord[K, Array[Byte]]]] =
                  batch.values.flatTraverse { case (k, values) =>
                    values.traverse { v =>
                      val topic: String =
                        topicOut(v)

                      Serializer[F, V]
                        // TODO: Is empty headers ok?
                        .serialize(topic, Headers.empty, v)
                        .map { valueBytes =>
                          ProducerRecord(
                            topic = topic,
                            key   = k,
                            value = valueBytes
                          )
                        }
                    }
                  }

                (stateRecords, valueRecords).flatMapN { (s, v) =>
                  val records: Chunk[CommittableProducerRecords[F, K, Array[Byte]]] =
                    Chunk.singleton(CommittableProducerRecords(s ++ v, batch.offset))

                  producer.produce(records).void
                }
              }
            }
        }
    }
}
