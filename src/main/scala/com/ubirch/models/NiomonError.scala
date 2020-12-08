package com.ubirch.models

case class NiomonError(error: String, causes: Seq[String], microservice: String, requestId: String)

//{
//  "error": "NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.",
//  "causes": [],
//  "microservice": "niomon-decoder",
//  "requestId": "7c9743e7-fa43-4790-996d-59d9ee06f2a6"
//}