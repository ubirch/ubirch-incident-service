package com.ubirch

import com.ubirch.services.IncidentListener
import com.ubirch.util.Binder


/**
  * Represents a bootable service object that starts the identity system
  */
object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit = * {
    get[IncidentListener].start
  }
}
