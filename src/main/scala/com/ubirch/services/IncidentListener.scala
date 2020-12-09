package com.ubirch.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.WithConsumerShutdownHook
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.producer.WithProducerShutdownHook
import com.ubirch.models.{Device, EventlogError, Incident, NiomonError}
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.{IncidentConsumerConf, IncidentProducerConf}
import com.ubirch.values.HeaderKeys
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization._
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.{DefaultFormats, Formats}

import java.io.{ByteArrayInputStream, IOException}
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.Inject
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}

class IncidentListener @Inject()(config: Config, lifecycle: Lifecycle, tenantRetriever: TenantRetriever,
                                 distributor: DistributorBase)
                                (implicit val ec: ExecutionContext)
  extends ExpressKafka[String, Array[Byte], Unit]
    with WithConsumerShutdownHook
    with WithProducerShutdownHook
    with LazyLogging {

  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  lifecycle.addStopHooks(hookFunc(consumerGracefulTimeout, consumption), hookFunc(production))

  override def consumerBootstrapServers: String = config.getString(IncidentConsumerConf.BOOTSTRAP_SERVERS)

  override def consumerGroupId: String = config.getString(IncidentConsumerConf.GROUP_ID)

  override def consumerMaxPollRecords: Int = config.getInt(IncidentConsumerConf.MAX_POLL_RECORDS)

  override def consumerGracefulTimeout: Int = config.getInt(IncidentConsumerConf.GRACEFUL_TIMEOUT)

  override def consumerReconnectBackoffMsConfig: Long = config.getLong(IncidentConsumerConf.RECONNECT_BACKOFF_MS)

  override def consumerReconnectBackoffMaxMsConfig: Long = config.getLong(IncidentConsumerConf.RECONNECT_BACKOFF_MAX_MS)

  private val niomonErrorTopic: String = config.getString(IncidentConsumerConf.NIOMON_ERROR_TOPIC)
  private val eventlogErrorTopic: String = config.getString(IncidentConsumerConf.EVENTLOG_ERROR_TOPIC)

  override def consumerTopics: Set[String] = Set(niomonErrorTopic, eventlogErrorTopic)

  override def maxTimeAggregationSeconds: Long = config.getLong(IncidentConsumerConf.MAX_TIME_AGGREGATION_SECONDS)

  override def metricsSubNamespace: String = config.getString(IncidentConsumerConf.METRICS_SUB_NAMESPACE)

  override def prefix: String = config.getString(IncidentConsumerConf.PREFIX)

  override def producerBootstrapServers: String = config.getString(IncidentProducerConf.BOOTSTRAP_SERVERS)

  override def lingerMs: Int = config.getInt(IncidentProducerConf.LINGER_MS)

  override def keyDeserializer: Deserializer[String] = new StringDeserializer

  override def valueDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer

  override def keySerializer: Serializer[String] = new StringSerializer

  override def valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer

  //  private val publishTopicPrefix: String = config.getString(IncidentProducerConf.PUBLISH_TOPIC_PREFIX)

  lifecycle.addStopHooks(hookFunc(consumerGracefulTimeout, consumption), hookFunc(production))

  override val process: Process = Process.apply(processing)

  def processing(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]]): Vector[Future[Boolean]] = {

    consumerRecords.map { cr =>

      try {
        val hwId = retrieveHeader(cr, HeaderKeys.X_UBIRCH_HARDWARE_ID)
        val authToken = retrieveHeader(cr, HeaderKeys.X_UBIRCH_DEVICE_INFO_TOKEN)

        val incident: Incident = createIncidentFromCR(cr, hwId)
        tenantRetriever.getDevice(hwId, authToken) match {

          case Some(device: Device) =>

            device.owners.headOption match {
              case Some(owner) =>
                logger.info(s"processing incident $incident for owner $owner and forwarding it to mqtt")
                distributor.sendIncident(write(incident).getBytes(StandardCharsets.UTF_8), owner.id)

              case None =>
                throw new IllegalArgumentException(s"device is missing an owner $device")
            }
          //          send(publishTopicPrefix + ownerId, write(incident).getBytes(StandardCharsets.UTF_8))

          case None =>
            throw new IOException(s"thing api cannot find a device for deviceId $hwId with $authToken")
        }
      } catch {
        case ex: Throwable =>
          logger.error(s"processing incident from consumerRecord with key ${cr.key()} from topic ${cr.topic()} failed ", ex)
          Future.successful(false)
      }
    }
  }

  private def createIncidentFromCR(cr: ConsumerRecord[String, Array[Byte]], hwId: String) = {
    cr.topic match {
      case `niomonErrorTopic` =>
        val nError = read[NiomonError](new ByteArrayInputStream(cr.value()))
        Incident(nError.requestId, hwId, nError.error, nError.microservice, new Date())

      case `eventlogErrorTopic` =>
        val eError = read[EventlogError](new ByteArrayInputStream(cr.value()))
        Incident("request_Id_unknown", hwId, eError.event.message, eError.event.service_name, eError.event.error_time)
    }
  }

  private def retrieveHeader(cr: ConsumerRecord[String, Array[Byte]], headerKey: String): String = {
    val values = cr.headers().headers(headerKey).toSet
    if (values.size != 1) throw new IllegalStateException(s"wrong number of $headerKey headers: ${values.size}.")
    new String(values.head.value(), StandardCharsets.UTF_8)
  }
}
