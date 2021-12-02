package com.ubirch.provider

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.MqttDistributorConf
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttAsyncClient, MqttConnectOptions, MqttException}

import java.util.UUID
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.Future

@Singleton
class MQTTClientProvider @Inject()(lifeCycle: Lifecycle, config: Config) extends Provider[MqttAsyncClient] with StrictLogging {

  private val broker: String = config.getString(MqttDistributorConf.BROKER_URL)
  //It can only connect one client with a specific name to a mqtt broker
  private val clientId: String = config.getString(MqttDistributorConf.CLIENT_ID) + UUID.randomUUID().toString
  private val userName: String = config.getString(MqttDistributorConf.USER_NAME)
  private val password: String = config.getString(MqttDistributorConf.PASSWORD)
  private val inflightMessages: Int = config.getInt(MqttDistributorConf.MAX_INFLIGHT_MESSAGES)
  private val mqttClient: MqttAsyncClient = getClient

  def getClient: MqttAsyncClient = {
    try {
      val persistence = new MemoryPersistence()
      logger.info(s"trying to connect MQTT Client to broker: $broker with $clientId")
      val client = new MqttAsyncClient(broker, clientId, persistence)
      val connOpts = new MqttConnectOptions()
      connOpts.setUserName(userName)
      connOpts.setPassword(password.toCharArray)
      connOpts.setCleanSession(true)
      connOpts.setMaxInflight(inflightMessages)
      client.connect(connOpts)
      logger.info(s"MQTT Client successfully connected.")
      client
    } catch {
      case me: MqttException =>
        logger.error("retrieving MQTT client failed: ", me)
        throw me
    }
  }

  override def get(): MqttAsyncClient = mqttClient

  lifeCycle.addStopHook { () =>
    mqttClient.disconnect()
    Future.successful(mqttClient.close())
  }
}
