package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject._
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * Basic definition for a Life CyCle Component.
  * A component that supports StopHooks
  */
trait Lifecycle {

  def addStopHook(hook: () => Future[_]): Unit

  def addStopHooks(hooks: (() => Future[_])*): Unit = hooks.foreach(addStopHook)

  def stop(): Future[_]

}

/**
  * It represents the default implementation for the LifeCycle Component.
  * It actually executes or clears StopHooks.
  */

@Singleton
class DefaultLifecycle @Inject()(implicit ec: ExecutionContext)
  extends Lifecycle
    with LazyLogging {

  private val hooks = new ConcurrentLinkedDeque[() => Future[_]]()

  override def addStopHook(hook: () => Future[_]): Unit = hooks.push(hook)

  override def stop(): Future[_] = {

    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] = {
      val hook = hooks.poll()
      if (hook != null) clearHooks(previous.flatMap { _ =>
        hook().recover {
          case e => logger.error("Error executing stop hook", e)
        }
      })
      else previous
    }

    logger.info("Running life cycle hooks...")
    clearHooks()
  }

}
