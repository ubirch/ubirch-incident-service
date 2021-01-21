package com.ubirch.services

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.TestBase
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{Event, EventlogError, Incident, NiomonError, SimpleDeviceInfo}
import com.ubirch.provider.ConfigProvider
import com.ubirch.util.{Binder, InjectorHelper, Lifecycle, TestErrorMessages}
import com.ubirch.values.ConfPaths.{IncidentConsumerConf, IncidentProducerConf}
import com.ubirch.values.HeaderKeys
import net.manub.embeddedkafka.Codecs.nullSerializer
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer}
import org.joda.time.DateTime
import org.json4s.JsonAST.{JObject, JString, JValue}
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, Formats, JField}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatest.mockito.MockitoSugar.mock
import scredis.Redis

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class IncidentListenerSpec extends TestBase with EmbeddedKafka with StrictLogging {


  private def FakeInjector(bootstrapServers: String, niomonErrorTopic: String, eventlogErrorTopic: String): InjectorHelper =
    new InjectorHelper(List(new Binder {
      override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
        override def conf: Config = super.conf
          .withValue(IncidentConsumerConf.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(IncidentProducerConf.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(IncidentConsumerConf.EVENTLOG_ERROR_TOPIC, ConfigValueFactory.fromAnyRef(eventlogErrorTopic))
          .withValue(IncidentConsumerConf.NIOMON_ERROR_TOPIC, ConfigValueFactory.fromAnyRef(niomonErrorTopic))
      })
    })) {}

  implicit val valueDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer
  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  "read and process incidents" in {

    implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    val niomonErrorTopic = "ubirch-niomon-error-json"
    val eventlogErrorTopic = "com-ubirch-eventlog-error"
    val customerId = "1234567890"
    val injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, niomonErrorTopic, eventlogErrorTopic)

    val config = injector.get[Config]
    val mockRedis = mock[Redis]
    val lifecycle = injector.get[Lifecycle]

    class FakeTR(config: Config, mockRedis: Redis) extends TenantRetriever(config, mockRedis, lifecycle) {
      override def getDevice(hwDeviceId: String, token: String): Future[Option[SimpleDeviceInfo]] = {
        Future.successful(Some(SimpleDeviceInfo("", "", customerId)))
      }
    }
    val fakeTenantRetriever = new FakeTR(config, mockRedis)
    val incidentList: ListBuffer[Array[Byte]] = ListBuffer.empty[Array[Byte]]

    class FakeDistributor extends DistributorBase {

      override def sendIncident(incident: Array[Byte], customerId: String): Boolean = {
        synchronized(incidentList += incident)
        true
      }
    }
    val distributor = new FakeDistributor
    val mockIncidentHandler = new IncidentListener(config, lifecycle, fakeTenantRetriever, distributor)

    withRunningKafka {
      val hwIdHeader = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, "HardwareId".getBytes)
      val authTokenHeader = new RecordHeader(HeaderKeys.X_UBIRCH_DEVICE_INFO_TOKEN, "AuthToken".getBytes)
      val errorCodeHeader = new RecordHeader(HeaderKeys.X_CODE, "ErrorCode".getBytes)
      val headers = new RecordHeaders().add(hwIdHeader).add(authTokenHeader).add(errorCodeHeader)

      val niomonValue1 = TestErrorMessages.niomonErrorJson1.getBytes(StandardCharsets.UTF_8)
      val niomonValue2 = TestErrorMessages.niomonErrorJson2.getBytes(StandardCharsets.UTF_8)
      val niomonRecord1 = new ProducerRecord[String, Array[Byte]](niomonErrorTopic, null, "", niomonValue1, headers)
      val niomonRecord2 = new ProducerRecord[String, Array[Byte]](niomonErrorTopic, null, "", niomonValue2, headers)

      val eventlogValue1 = TestErrorMessages.eventlogErrorJson1.getBytes(StandardCharsets.UTF_8)
      val eventlogValue2 = TestErrorMessages.eventlogErrorJson2.getBytes(StandardCharsets.UTF_8)
      val eventlogRecord1 = new ProducerRecord[String, Array[Byte]](eventlogErrorTopic, null, "", eventlogValue1, headers)
      val eventlogRecord2 = new ProducerRecord[String, Array[Byte]](eventlogErrorTopic, null, "", eventlogValue2, headers)

      publishToKafka(niomonRecord1)
      publishToKafka(niomonRecord2)
      publishToKafka(eventlogRecord1)
      publishToKafka(eventlogRecord2)

      mockIncidentHandler.consumption.startPolling()
      Thread.sleep(6000)
      var niomonCounter = 0
      var eventlogCounter = 0

      incidentList.length mustBe 4
      incidentList.foreach { byteArray: Array[Byte] =>
        val incident: Incident = read[Incident](new ByteArrayInputStream(byteArray))
        incident.microservice match {
          case "niomon-decoder" =>
            assert(Seq("NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.", "SignatureException: Invalid signature").contains(incident.error))
            assert(Seq("7820bb50-a2f4-4c42-9b80-5d918bff7ff2", "7c9743e7-fa43-4790-996d-59d9ee06f2a6").contains(incident.requestId))
            assert(incident.timestamp.before(new Date()))
            niomonCounter += 1
          case service if service == "event-log-service" || service == "event-log" =>
            assert(Seq("Error in the Encoding Process: No CustomerId found", "Error storing data (other)").contains(incident.error))
            incident.requestId mustBe "request_Id_unknown"
            assert(Seq(new DateTime("2020-12-01T08:52:09.484Z"), new DateTime("2020-12-01T12:37:59.892Z")).contains(new DateTime(incident.timestamp)))
            eventlogCounter += 1
        }
      }
      eventlogCounter mustBe 2
      niomonCounter mustBe 2
    }
  }
}

/**
 * Test code for the functions of the IncidentListener class
 */
class IncidentListenerFuncSpec extends FreeSpec with MustMatchers {
  implicit val ec = scala.concurrent.ExecutionContext.global

  val niomonErrorTopic = "ubirch-niomon-error-json"
  val eventLogErrorTopic = "ubirch-svalbard-evt-error-json"
  private def FakeInjector(bootstrapServers: String, niomonErrorTopic: String, eventlogErrorTopic: String): InjectorHelper =
    new InjectorHelper(List(new Binder {
      override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
        override def conf: Config = super.conf
          .withValue(IncidentConsumerConf.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(IncidentProducerConf.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(IncidentConsumerConf.EVENTLOG_ERROR_TOPIC, ConfigValueFactory.fromAnyRef(eventlogErrorTopic))
          .withValue(IncidentConsumerConf.NIOMON_ERROR_TOPIC, ConfigValueFactory.fromAnyRef(niomonErrorTopic))
      })
    })) {}

  private def getMockInjectListener(): IncidentListener = {
    val injector = FakeInjector("localhost:" + 8000, niomonErrorTopic, eventLogErrorTopic)

    val config = injector.get[Config]
    val lifecycle = injector.get[Lifecycle]
    val mockRedis = mock[Redis]
    class FakeTR(config: Config, mockRedis: Redis) extends TenantRetriever(config, mockRedis, lifecycle) {
      override def getDevice(hwDeviceId: String, token: String): Future[Option[SimpleDeviceInfo]] = Future.successful(None)
    }
    val fakeTenantRetriever = new FakeTR(config, mockRedis)
    class FakeDistributor extends DistributorBase {
      override def sendIncident(incident: Array[Byte], customerId: String): Boolean = true
    }
    val distributor = new FakeDistributor
    new IncidentListener(config, lifecycle, fakeTenantRetriever, distributor)
  }

  "readFromCR" - {
    val niomonError = NiomonError("NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.", Seq("authentication error"), "niomon-decoder", "7c9743e7-fa43-4790-996d-59d9ee06f2a6")
    val niomonErrorJson1: String =
      s"""{
         |  "error": "${niomonError.error}",
         |  "causes": ["${niomonError.causes(0)}"],
         |  "microservice": "${niomonError.microservice}",
         |  "requestId": "${niomonError.requestId}"
         |}""".stripMargin

    "(success) read NiomonError" in {
      val mockIncidentListener = getMockInjectListener()
      val niomonValue = niomonErrorJson1.getBytes(StandardCharsets.UTF_8)
      val customerRecord = new ConsumerRecord("", 0, 0, "", niomonValue)
      val result: Either[Throwable, NiomonError] = mockIncidentListener.readFromCR[NiomonError](customerRecord)
      result mustBe Right(niomonError)
    }
  }

  "retrieveHeaderOpt" - {
    "(success) header exists" in {
      val mockIncidentListener = getMockInjectListener()
      val errorCode = "errorCode"
      val xCodeHeader = new RecordHeader(HeaderKeys.X_CODE, errorCode.getBytes)
      val headers = new RecordHeaders().add(xCodeHeader)
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeaderOpt(customerRecord, HeaderKeys.X_CODE)
      result mustBe Right(Some(errorCode))
    }

    "(success) header doesn't exist" in {
      val mockIncidentListener = getMockInjectListener()
      val headers = new RecordHeaders()
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeaderOpt(customerRecord, HeaderKeys.X_CODE)
      result mustBe Right(None)
    }

    "(failure) multi headers exist " in {
      val mockIncidentListener = getMockInjectListener()
      val errorCode1 = "errorCode1"
      val errorCode2 = "errorCode2"
      val xCodeHeader1 = new RecordHeader(HeaderKeys.X_CODE, errorCode1.getBytes)
      val xCodeHeader2 = new RecordHeader(HeaderKeys.X_CODE, errorCode2.getBytes)
      val headers = new RecordHeaders().add(xCodeHeader1).add(xCodeHeader2)
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeaderOpt(customerRecord, HeaderKeys.X_CODE)
      result mustBe Left(mockIncidentListener.HeaderError(2, HeaderKeys.X_CODE))
    }
  }

  "retrieveHeader" - {
    "(success) header exists" in {
      val mockIncidentListener = getMockInjectListener()
      val hardWareId = "HardwareId"
      val hwIdHeader = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, hardWareId.getBytes)
      val headers = new RecordHeaders().add(hwIdHeader)
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeader(customerRecord, HeaderKeys.X_UBIRCH_HARDWARE_ID)
      result mustBe Right(hardWareId)
    }

    "(failure) header doesn't exist" in {
      val mockIncidentListener = getMockInjectListener()
      val headers = new RecordHeaders()
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeader(customerRecord, HeaderKeys.X_UBIRCH_HARDWARE_ID)
      result mustBe Left(mockIncidentListener.HeaderError(0, HeaderKeys.X_UBIRCH_HARDWARE_ID))
    }

    "(failure) multi headers exist " in {
      val mockIncidentListener = getMockInjectListener()
      val hardWareId1 = "HardwareId1"
      val hardWareId2 = "HardwareId2"
      val hwIdHeader1 = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, hardWareId1.getBytes)
      val hwIdHeader2 = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, hardWareId2.getBytes)
      val headers = new RecordHeaders().add(hwIdHeader1).add(hwIdHeader2)
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)
      val result = mockIncidentListener.retrieveHeader(customerRecord, HeaderKeys.X_UBIRCH_HARDWARE_ID)
      result mustBe Left(mockIncidentListener.HeaderError(2, HeaderKeys.X_UBIRCH_HARDWARE_ID))
    }
  }

  "createIncidentFromCR" - {
    "(success) create NiomonError incident" in {
      val niomonError = NiomonError("NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.", Seq("authentication error"), "niomon-decoder", "7c9743e7-fa43-4790-996d-59d9ee06f2a6")
      val niomonErrorJson: String =
        s"""{
           |  "error": "${niomonError.error}",
           |  "causes": ["${niomonError.causes(0)}"],
           |  "microservice": "${niomonError.microservice}",
           |  "requestId": "${niomonError.requestId}"
           |}""".stripMargin

      val errorCode = "errorCode"
      val hardWareId = "HardwareId"
      val hwIdHeader = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, hardWareId.getBytes)
      val xCodeHeader = new RecordHeader(HeaderKeys.X_CODE, errorCode.getBytes)
      val niomonValue = niomonErrorJson.getBytes(StandardCharsets.UTF_8)
      val headers = new RecordHeaders().add(xCodeHeader).add(hwIdHeader)
      val customerRecord = new ConsumerRecord(niomonErrorTopic, 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", niomonValue, headers)

      val mockIncidentListener = getMockInjectListener()
      val result: Either[Throwable, Incident] = mockIncidentListener.createIncidentFromCR(customerRecord, hardWareId, Some(errorCode))
      result.isRight mustBe true
      result.right.foreach { incident =>
        incident.requestId mustBe niomonError.requestId
        incident.hwDeviceId mustBe hardWareId
        incident.errorCode mustBe Some(errorCode)
        incident.error mustBe niomonError.error
        incident.microservice mustBe niomonError.microservice
      }
    }

    "(success) create EventLogError incident" in {
      val errorDateStr = "2020-12-01T00:00:00.000Z"
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
      val errorDate = formatter.parse(errorDateStr)
      val event = Event("", "Error in the Encoding Process: No CustomerId found", "", JString(""), errorDate, "event-log-service")
      val eventLogError = EventlogError(JObject(List.empty[JField]), "", "", "", "", event, errorDate, "", "", Seq.empty[JValue])

      val eventLogErrorJson: String =
        s"""{
          |  "headers": {},
          |  "id": "${eventLogError.id}",
          |  "customer_id": "${eventLogError.customer_id}",
          |  "service_class": "${eventLogError.service_class}",
          |  "category": "${eventLogError.category}",
          |  "event": {
          |    "id": "${event.id}",
          |    "message": "${event.message}",
          |    "exception_name": "${event.exception_name}",
          |    "value": "",
          |    "error_time": "${errorDateStr}",
          |    "service_name": "${event.service_name}"
          |  },
          |  "event_time": "${errorDateStr}",
          |  "signature": "${eventLogError.signature}",
          |  "nonce": "${eventLogError.nonce}",
          |  "lookup_keys": []
          |}
          |""".stripMargin
      val hardWareId = "HardwareId"
      val hwIdHeader = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, hardWareId.getBytes)
      val eventLogValue = eventLogErrorJson.getBytes(StandardCharsets.UTF_8)
      val headers = new RecordHeaders().add(hwIdHeader)
      val customerRecord = new ConsumerRecord(eventLogErrorTopic, 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", eventLogValue, headers)

      val mockIncidentListener = getMockInjectListener()
      val result: Either[Throwable, Incident] = mockIncidentListener.createIncidentFromCR(customerRecord, hardWareId, None)
      result.isRight mustBe true
      result.right.foreach { incident =>
        incident.requestId mustBe Incident.UNKNOWN_REQUEST_ID
        incident.hwDeviceId mustBe hardWareId
        incident.errorCode mustBe None
        incident.error mustBe event.message
        incident.microservice mustBe event.service_name
        incident.timestamp mustBe event.error_time
      }
    }

    "(failure) unknown topic" in {
      val mockIncidentListener = getMockInjectListener()
      val headers = new RecordHeaders()
      val customerRecord = new ConsumerRecord("unknown topic", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray, headers)

      val result: Either[Throwable, Incident] = mockIncidentListener.createIncidentFromCR(customerRecord, "", None)
      result.isLeft mustBe true
      result.swap.foreach { ex =>
        assert(ex.isInstanceOf[IllegalArgumentException])
      }
    }
  }
}