package com.ubirch.provider

import com.typesafe.config.Config
import com.ubirch.values.ConfPaths.ServiceConf

import java.util.concurrent.Executors
import javax.inject._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


/**
  * Represents the Execution Context provider.
  * Whenever someone injects an ExecutionContext, this provider defines what will
  * be returned.
  *
  * @param config Represents the configuration object
  */
@Singleton
class ExecutionProvider @Inject()(config: Config) extends Provider[ExecutionContext] {

  val threadPoolSize: Int = config.getInt(ServiceConf.THREAD_POOL_SIZE)

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadPoolSize))

  override def get(): ExecutionContext = ec

}

