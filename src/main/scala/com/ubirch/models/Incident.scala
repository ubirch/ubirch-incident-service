package com.ubirch.models

import java.util.Date


case class Incident(requestId: String, hwDeviceId: String, errorCode: Option[String], error: String, microservice: String,
                    timestamp: Date)

object Incident {
  val UNKNOWN_REQUEST_ID = "request_Id_unknown"
}

sealed trait ErrorType

object Unauthorized extends ErrorType