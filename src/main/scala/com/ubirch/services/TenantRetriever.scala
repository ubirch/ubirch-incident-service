package com.ubirch.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.models.SimpleDeviceInfo
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.TenantRetrieverConf
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import scredis.Redis
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Uri

import javax.inject.Inject
import scala.concurrent.Future

class TenantRetriever @Inject()(config: Config, redis: Redis, lifecycle: Lifecycle) extends StrictLogging {

  private implicit val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val thingApiURL = config.getString(TenantRetrieverConf.THING_API_URL)
  private val uri: Uri = Uri.parse(thingApiURL).getOrElse(throw new IllegalArgumentException(s"the configuration for the thingApiURL $thingApiURL is not parsable to uri."))
  private implicit val json4sJacksonFormats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  private val ttlSecondsRedisDeviceMap: Int = config.getInt(TenantRetrieverConf.TTL_REDIS_DEVICE_MAP) * 60

  import redis.dispatcher // Import internal ActorSystem's dispatcher (execution context) to register callbacks

  def getDevice(hwId: String, token: String): Future[Option[SimpleDeviceInfo]] =
    getDeviceCached(hwId).flatMap {
      case None =>
        getDeviceFromThingApi(token).map {
          case Left(msg) =>
            logger.error(s"failed to retrieve simpleDeviceInfo from Thing Api $msg")
            None
          case Right(deviceJson) =>
            cacheDevice(hwId, deviceJson)
            Some(read[SimpleDeviceInfo](deviceJson))
        }
      case deviceOpt =>
        Future.successful(deviceOpt)
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

  private[services] def getDeviceFromThingApi(token: String): Future[Either[String, String]] =
    sttp.client.basicRequest.get(uri).header("Authorization", s"bearer $token").send().map(_.body)

  lifecycle.addStopHook { () =>
    sttpBackend.close()
  }


}
