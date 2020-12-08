package com.ubirch.models

case class Device(
                   description: String,
                   canBeDeleted: Boolean,
                   deviceType: String,
                   groups: Seq[Group],
                   attributes: Attributes,
                   id: String,
                   owners: Seq[Owner],
                   created: String,
                   hwDeviceId: String)

case class Group(id: String, name: String)

case class Attributes(additionalProp1: Seq[String],
                      additionalProp2: Seq[String],
                      additionalProp3: Seq[String])

case class Owner(id: String, username: String, lastname: String, firstname: String)


