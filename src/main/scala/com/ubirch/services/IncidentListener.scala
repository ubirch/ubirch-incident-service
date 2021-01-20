package com.ubirch.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.WithConsumerShutdownHook
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.producer.WithProducerShutdownHook
import com.ubirch.models.{EventlogError, Incident, NiomonError, SimpleDeviceInfo}
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.{IncidentConsumerConf, IncidentProducerConf}
import com.ubirch.values.HeaderKeys
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization._
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.{DefaultFormats, Formats}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.Inject
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class IncidentListener @Inject()(config: Config, lifecycle: Lifecycle, tenantRetriever: TenantRetriever,
                                 distributor: DistributorBase)
                                (implicit val ec: ExecutionContext)
  extends ExpressKafka[String, Array[Byte], Vector[Boolean]]
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

  lifecycle.addStopHooks(hookFunc(consumerGracefulTimeout, consumption), hookFunc(production))

  override val process: Process = Process.async(processing)

  def processing(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]]): Future[Vector[Boolean]] = {

    Future.sequence(consumerRecords.map { cr =>

      // it's short circuit evaluation.
      // if an error occurs in the for comprehension before yield is called, incident is not sent, Left is returned.
      (for {
        hwId <- retrieveHeader(cr, HeaderKeys.X_UBIRCH_HARDWARE_ID)
        authToken <- retrieveHeader(cr, HeaderKeys.X_UBIRCH_DEVICE_INFO_TOKEN)
        errorCodeOpt <- retrieveHeaderOpt(cr, HeaderKeys.X_CODE)
        incident <- createIncidentFromCR(cr, hwId, errorCodeOpt)
      } yield (hwId, authToken, incident)) match {
        case Right((hwId, authToken, incident)) => {
          tenantRetriever.getDevice(hwId, authToken).map {
            case Some(device: SimpleDeviceInfo) =>
              logger.info(s"processing incident $incident for owner ${device.customerId} and forwarding it to mqtt")
              distributor.sendIncident(write(incident).getBytes(StandardCharsets.UTF_8), device.customerId)
            case None =>
              logger.info(s"device not found. hwId: $hwId, authToken: $authToken")
              false
          }
        }
        case Left(ex) => {
          logger.error(s"processing incident from consumerRecord with key ${cr.key()} from topic ${cr.topic()} failed ", ex)
          Future.successful(false)
        }
      }
    })
  }


  private[services] def createIncidentFromCR(cr: ConsumerRecord[String, Array[Byte]], hwId: String, errorCodeOpt: Option[String]): Either[Throwable, Incident] = {
    cr.topic match {
      case `niomonErrorTopic` =>
        readFromCR[NiomonError](cr).map { nError =>
          Incident(nError.requestId, hwId, errorCodeOpt, nError.error, nError.microservice, new Date())
        }

      case `eventlogErrorTopic` =>
        readFromCR[EventlogError](cr).map { eError =>
          Incident(Incident.UNKNOWN_REQUEST_ID, hwId, errorCodeOpt, eError.event.message, eError.event.service_name, eError.event.error_time)
        }

      case _ =>
        Left(new IllegalArgumentException(s"found unknown topic: ${cr.topic()} in incident handler"))
    }
  }

  private[services] def readFromCR[T](cr: ConsumerRecord[String, Array[Byte]])(implicit mf : scala.reflect.Manifest[T]): Either[Throwable, T] = {
    Try {
      read[T](new ByteArrayInputStream(cr.value()))
    } match {
      case Success(value) => Right(value)
      case Failure(err) => Left(ParseError(err.getMessage))
    }
  }


  private[services] def retrieveHeaderOpt(cr: ConsumerRecord[String, Array[Byte]], headerKey: String): Either[HeaderError, Option[String]] = {
    retrieveHeader(cr, headerKey) match {
      case Right(value) => Right(Some(value))
      case Left(err) =>
        if(err.headerNum == 0) Right(None)
        else Left(err)
    }
  }

  private[services] def retrieveHeader(cr: ConsumerRecord[String, Array[Byte]], headerKey: String): Either[HeaderError, String] = {
    val values = cr.headers().headers(headerKey).toList
    values.size match {
      case 1 => Right(new String(values.head.value(), StandardCharsets.UTF_8))
      case _ => Left(HeaderError(values.size, headerKey))
    }
  }

  case class HeaderError(headerNum: Int, headerKey: String) extends IllegalStateException {
    override def getMessage: String =  s"wrong number of $headerKey headers: ${headerNum}."
  }

  case class ParseError(errorMsg: String) extends RuntimeException {
    override def getMessage: String = s"couldn't parse error from consumerRecord. ${errorMsg}"
  }
}
