package com.ubirch.services

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, SttpBackend, Uri, sttp}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.models.SimpleDeviceInfo
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.TenantRetrieverConf
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import scredis.Redis

import javax.inject.Inject
import scala.concurrent.Future

class TenantRetriever @Inject()(config: Config, redis: Redis, lifecycle: Lifecycle) extends StrictLogging {

  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
  private val thingApiURL = config.getString(TenantRetrieverConf.THING_API_URL)
  private val uri: Uri = Uri.parse(thingApiURL).getOrElse(throw new IllegalArgumentException(s"the configuration for the thingApiURL $thingApiURL is not parsable to uri."))
  implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  private val ttlSecondsRedisDeviceMap: Int = config.getInt(TenantRetrieverConf.TTL_REDIS_DEVICE_MAP) * 60

  // Import internal ActorSystem's dispatcher (execution context) to register callbacks

  import redis.dispatcher

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

  private[services] def updateTTL(hwId: String) =
    redis.expire(hwId, ttlSecondsRedisDeviceMap)

  private[services] def getDeviceCached(hwId: String): Future[Option[SimpleDeviceInfo]] =
    redis
      .get[String](hwId)
      .map(_.map(value => read[SimpleDeviceInfo](value)))
      .recover {
        case ex: Throwable =>
          logger.error(s"something went wrong retrieving device with hwId $hwId from cache. ", ex)
          None
      }

  private[services] def cacheDevice(hwId: String, deviceJson: String): Unit =
    redis.setEX(hwId, deviceJson, ttlSecondsRedisDeviceMap)

  private[services] def getDeviceFromThingApi(hwId: String, token: String): Either[String, String] =
    sttp.get(uri).header("Authorization", s"bearer $token").send().body

  //  lifecycle.addStopHook {
  //    // Shutdown all initialized internal clients along with the ActorSystem
  //     redis.quit()
  //
  //  }


}
