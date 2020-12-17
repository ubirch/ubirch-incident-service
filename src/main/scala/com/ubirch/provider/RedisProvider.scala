package com.ubirch.provider

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import scredis.Redis

import javax.inject.{Inject, Provider, Singleton}

@Singleton
class RedisProvider @Inject()(config: Config) extends Provider[Redis] with StrictLogging {

  private val redis: Redis = Redis(config)

  override def get(): Redis = redis
}