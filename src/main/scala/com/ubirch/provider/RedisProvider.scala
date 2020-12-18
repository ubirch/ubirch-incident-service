package com.ubirch.provider

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.RedisConf
import scredis.Redis
import scredis.protocol.AuthConfig

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.Future

@Singleton
class RedisProvider @Inject()(config: Config, lifecycle: Lifecycle) extends Provider[Redis] with StrictLogging {

  private val host = config.getString(RedisConf.HOST)
  private val port = config.getInt(RedisConf.PORT)
  private val authOpt =
    if (config.hasPath(RedisConf.PASSWORD))
      Some(AuthConfig(None, config.getString(RedisConf.PASSWORD)))
    else None
  private val database = config.getInt(RedisConf.DATABASE)
  private val redis: Redis = Redis(host, port, authOpt, database)

  override def get(): Redis = redis

  lifecycle.addStopHook { () =>
    Future.successful(redis.quit())
  }

}