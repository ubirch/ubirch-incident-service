package com.ubirch.services

import com.github.sebruck.EmbeddedRedis
import com.ubirch.TestBase
import com.ubirch.models.Device
import com.ubirch.util.{Binder, InjectorHelper}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Mockito
import redis.embedded.RedisServer

class TenantRetrieverSpec extends TestBase with EmbeddedRedis {

  private val injector = new InjectorHelper(Binder.modules) {}
  var redis: Option[RedisServer] = None
  private val deviceJson: String = getDeviceJson
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
    val response: Either[Throwable, String] = Right(deviceJson)
    Mockito.doAnswer(_ => response).when(spiedTenantRetriever).getDeviceFromThingApi(hwId, token)
    val shouldDevice = Some(read[Device](deviceJson))

    //device should be retrieved via Thing API call
    val isDevice = spiedTenantRetriever.getDevice(hwId, token)
    isDevice mustBe shouldDevice

    //the device should be stored in cache
    val deviceOpt = tenantRetriever.getDeviceCached(hwId)
    deviceOpt mustBe shouldDevice

    //now the Thing API call should not be made
    Mockito.doAnswer(_ => throw new IllegalArgumentException("should not be thrown")).when(spiedTenantRetriever).getDeviceFromThingApi(hwId, token)
    val cachedDevice = spiedTenantRetriever.getDevice(hwId, token)
    cachedDevice mustBe shouldDevice
  }

  private def getDeviceJson: String =
    """{
      |  "description": "string",
      |  "canBeDeleted": true,
      |  "deviceType": "string",
      |  "groups": [
      |    {
      |      "id": "string",
      |      "name": "string"
      |    }
      |  ],
      |  "attributes": {
      |    "additionalProp1": [
      |      "string"
      |    ],
      |    "additionalProp2": [
      |      "string"
      |    ],
      |    "additionalProp3": [
      |      "string"
      |    ]
      |  },
      |  "id": "string",
      |  "owner": [
      |    {
      |      "id": "string",
      |      "username": "string",
      |      "lastname": "string",
      |      "firstname": "string"
      |    }
      |  ],
      |  "created": "string",
      |  "hwDeviceId": "string"
      |}""".stripMargin

}
