package com.ubirch.models

import org.json4s.JObject
import org.json4s.JsonAST.JValue

import java.util.Date

case class EventlogError(headers: JObject, id: String, customer_id: String,
                         service_class: String, category: String, event: Event,
                         event_time: Date, signature: String, nonce: String, lookup_keys: Seq[JValue])

case class Event(id: String, message: String, exception_name: String, value: JValue, error_time: Date, service_name: String)

//{
//  "headers": {},
//  "id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
//  "customer_id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
//  "service_class": "com.ubirch.models.EnrichedError",
//  "category": "ubirch-svalbard-evt-error-json",
//  "event": {
//    "id": "4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==",
//    "message": "Error storing data (other)",
//    "exception_name": "com.ubirch.util.Exceptions.StoringIntoEventLogException",
//    "value": "Some(EventLog(List((trace,ENCODER), (blue_mark,c6e9e241-8019-4fc5-bc8f-4048bed1cccd), (trace,EVENT_LOG)),4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,fd123dd8bffea35cb53709a7674252a3d4ef4bcdaf66cb856ff6f229bb8cd4b8,upp-event-log-entry,UPP,JObject(List((hint,JInt(0)), (payload,JString(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==)), (signature,JString(mJUZHByYCSQjzXVLTemImQUmu56+QhU2F+6IIm/kTLMRSY/fqVE1oiFoGyZD3spMEHNYa6ZwmM4GDyEs/xufBA==)), (signed,JString(lSLEEN4yO4NaNk+8hRFya76lzxQAxEDgtIC68L1mU4NQ5P96SYSKwqDZ0ZVV4s5H/Dzc08hPD9/jr16hZhundAp2hQThK8H7IByBqhDpYSxHaf82kqap)), (uuid,JString(de323b83-5a36-4fbc-8511-726bbea5cf14)), (version,JInt(34)))),Tue Dec 01 12:37:58 UTC 2020,,64663832313933342d613735392d343639392d616339372d626431646131663439333736,List(LookupKey(signature,UPP,Key(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,Some(UPP)),List(Value(mJUZHByYCSQjzXVLTemImQUmu56+QhU2F+6IIm/kTLMRSY/fqVE1oiFoGyZD3spMEHNYa6ZwmM4GDyEs/xufBA==,Some(signature),Map()))), LookupKey(device-id,DEVICE,Key(de323b83-5a36-4fbc-8511-726bbea5cf14,Some(DEVICE)),List(Value(4LSAuvC9ZlODUOT/ekmEisKg2dGVVeLOR/w83NPITw/f469eoWYbp3QKdoUE4SvB+yAcgaoQ6WEsR2n/NpKmqQ==,Some(UPP),Map()))))))",
//    "error_time": "2020-12-01T12:37:59.892Z",
//    "service_name": "event-log"
//  },
//  "event_time": "2020-12-01T12:37:59.914Z",
//  "signature": "e8b751ddcd219a952d4f0bdeabfd7ffebddfd8957deb23c0f1842634e5bb736b301262ae2cded46448283ade05e5d80981f9bd475b56f1c3842642e47cd1700d",
//  "nonce": "",
//  "lookup_keys": []
//}