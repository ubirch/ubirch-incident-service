package com.ubirch.services

import com.github.sebruck.EmbeddedRedis
import com.ubirch.TestBase
import com.ubirch.models.SimpleDeviceInfo
import com.ubirch.util.{Binder, InjectorHelper}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Mockito
import redis.embedded.RedisServer

class TenantRetrieverSpec extends TestBase with EmbeddedRedis {

  private val injector = new InjectorHelper(Binder.modules) {}
  var redis: Option[RedisServer] = None
  private val deviceJson: String = getSimpleDeviceInfoJson
  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless

  override def beforeAll(): Unit = {
    redis = Some(startRedis(6379))
  }

  override def afterAll(): Unit = {
    redis.get.stop()
  }

  "retrieve and cache devices" in {

    val hwId: String = "deviceId"
    val token: String = "token"
    val tenantRetriever = injector.get[TenantRetriever]
    val spiedTenantRetriever = Mockito.spy(tenantRetriever)

    Mockito
      .doAnswer(_ => Right(deviceJson).asInstanceOf[Either[String, String]])
      .when(spiedTenantRetriever).getDeviceFromThingApi(hwId, token)

    val shouldDevice = Some(read[SimpleDeviceInfo](deviceJson))

    //device should be retrieved via Thing API call
    spiedTenantRetriever
      .getDevice(hwId, token)
      .map { device =>
        tenantRetriever.cacheDevice(hwId, deviceJson)
        device mustBe shouldDevice

        //the device should be stored in cache
        tenantRetriever
          .getDeviceCached(hwId)
          .flatMap { d =>
            d mustBe shouldDevice

            //now the Thing API call should not be made
            Mockito.doAnswer(_ => throw new IllegalArgumentException("should not be thrown")).when(spiedTenantRetriever).getDeviceFromThingApi(hwId, token)
            val cachedDevice = spiedTenantRetriever.getDevice(hwId, token)
            cachedDevice.flatMap(_ mustBe shouldDevice)
          }
      }.flatten
  }

  private def getSimpleDeviceInfoJson: String =
    """{
      |  "hwDeviceId": "string",
      |  "description": "string",
      |  "customerId": "string"
      |}""".stripMargin

}
