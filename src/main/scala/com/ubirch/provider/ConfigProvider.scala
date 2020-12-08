package com.ubirch.provider

import com.typesafe.config.{Config, ConfigFactory}

import javax.inject.{Provider, Singleton}

/**
  * Configuration Provider for the Configuration Component.
  */
@Singleton
class ConfigProvider extends Provider[Config] {

  private val default = ConfigFactory.load()

  def conf: Config = default

  override def get(): Config = conf

}
