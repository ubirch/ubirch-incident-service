package com.ubirch.models

import java.util.Date


case class Incident(requestId: String, hwDeviceId: String, error: String, microservice: String,
                    timestamp: Date)


sealed trait ErrorType

object Unauthorized extends ErrorType