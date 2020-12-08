package com.ubirch

import com.google.inject.Module
import com.ubirch.util.{InjectorHelper, JVMHook, PrometheusMetrics}


/**
  * Represents an assembly for the boot process
  *
  * @param modules It is the modules of the system
  */
abstract class Boot(modules: List[Module]) extends InjectorHelper(modules) with WithJVMHooks with WithPrometheusMetrics {
  def *[T](block: => T): Unit =
    try {
      block
    } catch {
      case e: Exception =>
        logger.error("Exiting after exception found = {}", e.getMessage)
        Thread.sleep(5000)
        sys.exit(1)
    }
}


/**
  * Util that integrates an elegant way to add shut down hooks to the JVM.
  */
trait WithJVMHooks {

  _: InjectorHelper =>

  private def bootJVMHook(): JVMHook = get[JVMHook]

  bootJVMHook()

}

/**
  * Util that integrates an elegant way to add shut down hooks to the JVM.
  */
trait WithPrometheusMetrics {

  _: InjectorHelper =>

  get[PrometheusMetrics]

}