package com.ubirch.services

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.TestBase
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{Attributes, Device, Incident, Owner}
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.provider.ConfigProvider
import com.ubirch.util.{Binder, InjectorHelper, Lifecycle, TestErrorMessages}
import com.ubirch.values.ConfPaths.{IncidentConsumerConf, IncidentProducerConf}
import com.ubirch.values.HeaderKeys
import net.manub.embeddedkafka.Codecs.nullSerializer
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer}
import org.joda.time.DateTime
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.mockito.MockitoSugar.mock

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Date
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
    val ownerId = "1234567890"
    val injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, niomonErrorTopic, eventlogErrorTopic)

    val config = injector.get[Config]
    val mockRedis = mock[RedisCache]
    val lifecycle = injector.get[Lifecycle]

    class FakeTR(config: Config, mockRedis: RedisCache) extends TenantRetriever(config, mockRedis) {
      override def getDevice(hwDeviceId: String, token: String): Option[Device] = {
        Some(Device("", canBeDeleted = false, "", Seq(), Attributes(Seq(), Seq(), Seq()), "", Seq(Owner(ownerId, "", "", "")), "", ""))
      }
    }
    val fakeTenantRetriever = new FakeTR(config, mockRedis)

    class FakeDistributor extends DistributorBase {
      var incidentList: Seq[Array[Byte]] = Seq()

      override def sendIncident(incident: Array[Byte], customerId: String): Future[Boolean] = {
        incidentList = incidentList :+ incident
        Future.successful(true)
      }
    }
    val distributor = new FakeDistributor
    val mockIncidentHandler = new IncidentListener(config, lifecycle, fakeTenantRetriever, distributor)

    withRunningKafka {
      val hwIdHeader = new RecordHeader(HeaderKeys.X_UBIRCH_HARDWARE_ID, "HardwareId".getBytes)
      val authTokenHeader = new RecordHeader(HeaderKeys.X_UBIRCH_DEVICE_INFO_TOKEN, "AuthToken".getBytes)
      val headers = new RecordHeaders().add(hwIdHeader).add(authTokenHeader)

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

      //      createCustomTopic("incident_" + ownerId)
      mockIncidentHandler.consumption.startPolling()
      Thread.sleep(6000)
      var niomonCounter = 0
      var eventlogCounter = 0

      distributor.incidentList.length mustBe 4
      distributor.incidentList.foreach { byteArray: Array[Byte] =>
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


      //      consumeNumberMessagesFrom[Array[Byte]]("incident_" + ownerId, 4).foreach { cr: Array[Byte] =>
      //        val incident: Incident = read[Incident](new ByteArrayInputStream(cr))
      //        incident.microservice match {
      //          case "niomon-decoder" =>
      //            assert(Seq("NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.", "SignatureException: Invalid signature").contains(incident.error))
      //            assert(Seq("7820bb50-a2f4-4c42-9b80-5d918bff7ff2", "7c9743e7-fa43-4790-996d-59d9ee06f2a6").contains(incident.requestId))
      //            assert(incident.timestamp.before(new Date()))
      //            niomonCounter += 1
      //          case service if service == "event-log-service" || service == "event-log" =>
      //            assert(Seq("Error in the Encoding Process: No CustomerId found", "Error storing data (other)").contains(incident.error))
      //            incident.requestId mustBe "request_Id_unknown"
      //            assert(Seq(new DateTime("2020-12-01T08:52:09.484Z"), new DateTime("2020-12-01T12:37:59.892Z")).contains(new DateTime(incident.timestamp)))
      //            eventlogCounter += 1
      //        }
      //      }
    }
  }


}
