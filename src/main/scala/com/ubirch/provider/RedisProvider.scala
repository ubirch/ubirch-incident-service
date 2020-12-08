package com.ubirch.provider

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.values.ConfPaths.ServiceConf

import javax.inject._

@Singleton
class RedisProvider @Inject()(config: Config) extends Provider[RedisCache] with StrictLogging {

  private val redisName = config.getString(ServiceConf.SERVICE_NAME)
  private val redis: RedisCache = new RedisCache(redisName, config)

  override def get(): RedisCache = redis

}

