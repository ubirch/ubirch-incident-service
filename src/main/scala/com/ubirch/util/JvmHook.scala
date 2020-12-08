package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.{CountDownLatch, TimeUnit}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


/**
  * Definition for JVM ShutDown Hooks.
  */

trait JVMHook {
  protected def registerShutdownHooks(): Unit
}

/**
  * Default Implementation of the JVMHook.
  * It takes LifeCycle stop hooks and adds a corresponding shut down hook.
  *
  * @param lifecycle LifeCycle Component that allows for StopDownHooks
  */

@Singleton
class DefaultJVMHook @Inject()(lifecycle: Lifecycle)(implicit ec: ExecutionContext) extends JVMHook with LazyLogging {

  protected def registerShutdownHooks() {

    logger.info("Registering Shutdown Hooks")

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        val countDownLatch = new CountDownLatch(1)
        lifecycle.stop().onComplete {
          case Success(_) =>
            countDownLatch.countDown()
          case Failure(e) =>
            logger.error("Error running jvm hook={}", e.getMessage)
            countDownLatch.countDown()
        }

        val res = countDownLatch.await(5, TimeUnit.SECONDS) //Waiting 5 secs
        if (!res) logger.warn("Taking too much time shutting down :(  ..")
        else logger.info("Bye bye, see you later...")
      }
    })

  }

  registerShutdownHooks()

}

