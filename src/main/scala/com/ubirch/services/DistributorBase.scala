package com.ubirch.services

trait DistributorBase {

  def sendIncident(incident: Array[Byte], customerId: String): Boolean
}