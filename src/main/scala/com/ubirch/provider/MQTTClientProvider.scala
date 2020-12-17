package com.ubirch.provider

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.util.Lifecycle
import com.ubirch.values.ConfPaths.MqttDistributorConf
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttAsyncClient, MqttConnectOptions, MqttException}

import javax.inject.{Inject, Provider, Singleton}

@Singleton
class MQTTClientProvider @Inject()(lifeCycle: Lifecycle, config: Config) extends Provider[MqttAsyncClient] with StrictLogging {

  private val mqttClient: MqttAsyncClient = getClient
  private val broker = config.getString(MqttDistributorConf.BROKER_URL)
  private val clientId = config.getString(MqttDistributorConf.CLIENT_ID)
  private val userName = config.getString(MqttDistributorConf.USER_NAME)
  private val password = config.getString(MqttDistributorConf.PASSWORD)

  def getClient: MqttAsyncClient = {
    try {
      val persistence = new MemoryPersistence()
      val client = new MqttAsyncClient(broker, clientId, persistence)
      val connOpts = new MqttConnectOptions()
      connOpts.setUserName(userName)
      connOpts.setPassword(password.toCharArray)
      connOpts.setCleanSession(true)
      client.connect(connOpts)
      logger.info("MQTT Client connected to broker: " + broker)
      client
    } catch {
      case me: MqttException =>
        logger.error("retrieving MQTT client failed: ", me)
        throw me
    }
  }

  override def get(): MqttAsyncClient = mqttClient

  //  lifeCycle.addStopHook {
  //    Future.successful(mqttClient.close())
  //
  //  }
}
