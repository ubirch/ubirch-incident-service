package com.ubirch.services

import scala.concurrent.Future

trait DistributorBase {

  def sendIncident(incident: Array[Byte], customerId: String): Future[Boolean]
}