package com.ubirch.util

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.Config
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.provider.{ConfigProvider, ExecutionProvider, RedisProvider}
import com.ubirch.services.{DistributorBase, PahoDistributor}

import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])

  def redis: ScopedBindingBuilder = bind(classOf[RedisCache]).toProvider(classOf[RedisProvider])

  def distributor: ScopedBindingBuilder = bind(classOf[DistributorBase]).to(classOf[PahoDistributor])

  def configure(): Unit = {
    config
    executionContext
    jvmHook
    lifecycle
    redis
    distributor
  }

}

object Binder {
  def modules: List[Module] = List(new Binder)
}

