package com.ubirch.services

import com.softwaremill.sttp._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.models.SimpleDeviceInfo
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.values.ConfPaths.TenantRetrieverConf
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.redisson.api.RMapCache

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TenantRetriever @Inject()(config: Config, redis: RedisCache) extends StrictLogging {

  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
  private lazy val redisMap: RMapCache[String, String] = redis.redisson.getMapCache[String, String](TenantRetrieverConf.REDIS_DEVICE_MAP)
  private val thingApiURL = config.getString(TenantRetrieverConf.THING_API_URL)
  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  private val ttlMinutesRedisDeviceMap: Long = config.getLong(TenantRetrieverConf.TTL_REDIS_DEVICE_MAP)
  private val uri: Uri = Uri.parse(thingApiURL).getOrElse(throw new IllegalArgumentException(s"the configuration for the thingApiURL $thingApiURL is not parsable to uri."))

  def getDevice(hwId: String, token: String): Future[Option[SimpleDeviceInfo]] =
    getDeviceCached(hwId).map {
      case None =>
        getDeviceFromThingApi(hwId, token) match {
          case Left(msg) =>
            logger.error(s"failed to retrieve simpleDeviceInfo from Thing Api $msg")
            None
          case Right(deviceJson) =>
            cacheDevice(hwId, deviceJson)
            Some(read[SimpleDeviceInfo](deviceJson))
        }
      case deviceOpt =>
        deviceOpt
    }.recover {
      case ex: Throwable =>
        logger.error("something went wrong retrieving device ", ex)
        None
    }


  private[services] def updateTTL(hwId: String, deviceJson: String) = {
    redisMap.fastPutAsync(hwId, deviceJson, ttlMinutesRedisDeviceMap, TimeUnit.MINUTES)
  }


  private[services] def getDeviceCached(hwId: String): Future[Option[SimpleDeviceInfo]] =

    Future(redisMap.get(hwId)).map {
      case deviceJson: String =>
        //Todo: Does this make sense?
        updateTTL(hwId, deviceJson)
        Some(read[SimpleDeviceInfo](deviceJson))
      case _ => None
    }.recover {
      case ex: Throwable =>
        logger.error(s"something went wrong retrieving device with hwId $hwId from cache. ", ex)
        None
    }


  private[services] def cacheDevice(hwId: String, deviceJson: String): Unit =
    redisMap.fastPutAsync(hwId, deviceJson, ttlMinutesRedisDeviceMap, TimeUnit.MINUTES)


  private[services] def getDeviceFromThingApi(hwId: String, token: String): Either[String, String] =
    sttp.get(uri).header("Authorization", s"bearer $token").send().body

}
