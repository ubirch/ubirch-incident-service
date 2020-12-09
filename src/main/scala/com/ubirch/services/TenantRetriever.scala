package com.ubirch.services

import com.softwaremill.sttp._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.models.Device
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.values.ConfPaths.TenantRetrieverConf
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.redisson.api.RMapCache

import java.util.concurrent.TimeUnit
import javax.inject.Inject


class TenantRetriever @Inject()(config: Config, redis: RedisCache) extends StrictLogging {

  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
  private lazy val redisMap: RMapCache[String, String] = redis.redisson.getMapCache[String, String](TenantRetrieverConf.REDIS_DEVICE_MAP)
  private val thingApiURL = config.getString(TenantRetrieverConf.THING_API_URL)
  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  private val ttlMinutesRedisDeviceMap: Long = config.getLong(TenantRetrieverConf.TTL_REDIS_DEVICE_MAP)

  def getDevice(hwId: String, token: String): Option[Device] = {
    getDeviceCached(hwId) match {

      case None =>
        getDeviceFromThingApi(hwId, token) match {
          case Left(ex) => throw ex
          case Right(deviceJson) =>
            cacheDevice(hwId, deviceJson)
            Some(read[Device](deviceJson))
        }
      case deviceOpt =>
        deviceOpt
    }
  }

  private[services] def updateTTL(hwId: String, deviceJson: String) = {
    redisMap.fastPutAsync(hwId, deviceJson, ttlMinutesRedisDeviceMap, TimeUnit.MINUTES)
  }


  private[services] def getDeviceCached(hwId: String): Option[Device] = {

    redisMap.get(hwId) match {
      case deviceJson: String =>
        try {
          //Todo: Does this make sense?
          updateTTL(hwId, deviceJson)
          Some(read[Device](deviceJson))
        } catch {
          case ex: Throwable => logger.error("something went wrong parsing a device from redis cache", ex)
            None
        }
      case _ => None
    }
  }

  private def cacheDevice(hwId: String, deviceJson: String): Unit = {
    redisMap.fastPutAsync(hwId, deviceJson, ttlMinutesRedisDeviceMap, TimeUnit.MINUTES)
  }

  private[services] def getDeviceFromThingApi(hwId: String, token: String): Either[Throwable, String] = {
    for {
      uri <- Uri.parse(thingApiURL).toEither
      response = sttp.get(uri).header("Authorization", s"bearer $token").send()
      body <- response.body.left.map(_ => new NoSuchElementException("No device info"))
    } yield {
      body
    }
  }
}
