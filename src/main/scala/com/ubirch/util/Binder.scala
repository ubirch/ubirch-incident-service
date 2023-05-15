package com.ubirch.util

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.Config
import com.ubirch.provider.{ConfigProvider, ExecutionProvider, MQTTClientProvider, RedisProvider, SchedulerProvider}
import com.ubirch.services.{DistributorBase, PahoDistributor}
import monix.execution.Scheduler
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import scredis.Redis

import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def scheduler: ScopedBindingBuilder = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])

  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])

  def redis: ScopedBindingBuilder = bind(classOf[Redis]).toProvider(classOf[RedisProvider])

  def mqttClient: ScopedBindingBuilder = bind(classOf[MqttAsyncClient]).toProvider(classOf[MQTTClientProvider])

  def distributor: ScopedBindingBuilder = bind(classOf[DistributorBase]).to(classOf[PahoDistributor])

  def configure(): Unit = {
    config
    executionContext
    scheduler
    jvmHook
    lifecycle
    redis
    mqttClient
    distributor
  }

}

object Binder {
  def modules: List[Module] = List(new Binder)
}

