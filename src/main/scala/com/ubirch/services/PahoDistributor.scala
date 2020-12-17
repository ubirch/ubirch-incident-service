package com.ubirch.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.values.ConfPaths.MqttDistributorConf
import org.eclipse.paho.client.mqttv3.{MqttAsyncClient, MqttMessage}

import javax.inject.Inject

class PahoDistributor @Inject()(config: Config, mqttClient: MqttAsyncClient) extends DistributorBase with StrictLogging {

  private val qos = config.getInt(MqttDistributorConf.QOS)
  private val queue_prefix = config.getString(MqttDistributorConf.QUEUE_PREFIX)

  override def sendIncident(incident: Array[Byte], customerId: String): Boolean = {
    val message = new MqttMessage(incident)
    message.setQos(qos)
    mqttClient.publish(queue_prefix + customerId, message)
    true
  }
}
