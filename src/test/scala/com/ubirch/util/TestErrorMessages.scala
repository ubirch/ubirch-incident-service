package com.ubirch.util

object TestErrorMessages {


  val niomonErrorJson1: String =
    """{
      |  "error": "NoSuchElementException: Header with key x-ubirch-hardware-id is missing. Cannot verify msgpack.",
      |  "causes": [],
      |  "microservice": "niomon-decoder",
      |  "requestId": "7c9743e7-fa43-4790-996d-59d9ee06f2a6"
      |}""".stripMargin

  val niomonErrorJson2: String =
    """{
      |  "error": "SignatureException: Invalid signature",
      |  "causes": [],
      |  "microservice": "niomon-decoder",
      |  "requestId": "7820bb50-a2f4-4c42-9b80-5d918bff7ff2"
      |}
      |""".stripMargin

  val eventlogErrorJson1: String =
    """{
      |  "headers": {},
      |  "id": "7bb902c2-8998-4c51-a1b6-ea9af80291d7",
      |  "customer_id": "7bb902c2-8998-4c51-a1b6-ea9af80291d7",
      |  "service_class": "com.ubirch.models.EnrichedError",
      |  "category": "ubirch-svalbard-evt-error-json",
      |  "event": {
      |    "id": "7bb902c2-8998-4c51-a1b6-ea9af80291d7",
      |    "message": "Error in the Encoding Process: No CustomerId found",
      |    "exception_name": "com.ubirch.encoder.util.Exceptions.EncodingException",
      |    "value": "{\"ubirchPacket\":{\"chain\":\"w1eI2A3W/fCIWy6huAqSfN1trugcPYQrB2OqFU9Jk1eh2qkhhY8lOPfQiuTKZ/xWnZwkeN4y5S1Fa2xKRDDhDA==\",\"hint\":0,\"payload\":\"KMVHQvhvrEEJAAAABgAAACYExl8=\",\"signature\":\"BA9itTmXKZnd5qaeyLZ00ErqyqaFduA59XYr8mXLNgWPSys2tKhz41p3yLeFn2knJj4RaLfaU+0/lYp8DtTsAA==\",\"signed\":\"lhOwVUJJUjxxv4AwtDxxv4AwtNoAQMNXiNgN1v3wiFsuobgKknzdba7oHD2EKwdjqhVPSZNXodqpIYWPJTj30Irkymf8Vp2cJHjeMuUtRWtsSkQw4QwAtCjFR0L4b6xBCQAAAAYAAAAmBMZf\",\"uuid\":\"55424952-3c71-bf80-30b4-3c71bf8030b4\",\"version\":35},\"context\":{}}",
      |    "error_time": "2020-12-01T08:52:09.484Z",
      |    "service_name": "event-log-service"
      |  },
      |  "event_time": "2020-12-01T08:52:09.484Z",
      |  "signature": "89399f6a6549159e9a5947a43a8f8d2b3a26fb67f5dbf065e89b660bb4cbf11d6c2160507ddc332fa35a291b8ac701d9abd8434f386b1e9724a461217c1f140e",
      |  "nonce": "",
      |  "lookup_keys": []
      |}
      |""".stripMargin

  val eventlogErrorJson2: String =
    """{
      |  "headers": {},
      |  "id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
      |  "customer_id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
      |  "service_class": "com.ubirch.models.EnrichedError",
      |  "category": "ubirch-svalbard-evt-error-json",
      |  "event": {
      |    "id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
      |    "message": "Error storing data (other)",
      |    "exception_name": "com.ubirch.util.Exceptions.StoringIntoEventLogException",
      |    "value": "Some(EventLog(List((trace,ENCODER), (blue_mark,c6e9e241-8019-4fc5-bc8f-4048bed1cccd), (trace,EVENT_LOG)),4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,fd123dd8bffea35cb53709a7674252a3d4ef4bcdaf66cb856ff6f229bb8cd4b8,upp-event-log-entry,UPP,JObject(List((hint,JInt(0)), (payload,JString(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==)), (signature,JString(mJUZHByYCSQjzXVLTemImQUmu56+QhU2F+6IIm/kTLMRSY/fqVE1oiFoGyZD3spMEHNYa6ZwmM4GDyEs/xufBA==)), (signed,JString(lSLEEN4yO4NaNk+8hRFya76lzxQAxEDgtIC68L1mU4NQ5P96SYSKwqDZ0ZVV4s5H/Dzc08hPD9/jr16hZhundAp2hQThK8H7IByBqhDpYSxHaf82kqap)), (uuid,JString(de323b83-5a36-4fbc-8511-726bbea5cf14)), (version,JInt(34)))),Tue Dec 01 12:37:58 UTC 2020,,64663832313933342d613735392d343639392d616339372d626431646131663439333736,List(LookupKey(signature,UPP,Key(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,Some(UPP)),List(Value(mJUZHByYCSQjzXVLTemImQUmu56+QhU2F+6IIm/kTLMRSY/fqVE1oiFoGyZD3spMEHNYa6ZwmM4GDyEs/xufBA==,Some(signature),Map()))), LookupKey(device-id,DEVICE,Key(de323b83-5a36-4fbc-8511-726bbea5cf14,Some(DEVICE)),List(Value(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,Some(UPP),Map()))))))",
      |    "error_time": "2020-12-01T12:37:59.892Z",
      |    "service_name": "event-log"
      |  },
      |  "event_time": "2020-12-01T12:37:59.914Z",
      |  "signature": "e8b751ddcd219a952d4f0bdeabfd7ffebddfd8957deb23c0f1842634e5bb736b301262ae2cded46448283ade05e5d80981f9bd475b56f1c3842642e47cd1700d",
      |  "nonce": "",
      |  "lookup_keys": []
      |}""".stripMargin
}
