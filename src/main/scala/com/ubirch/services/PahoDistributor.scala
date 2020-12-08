package com.ubirch.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.values.ConfPaths.MqttDistributorConf
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttConnectOptions, MqttException, MqttMessage}

import javax.inject.Inject
import scala.concurrent.Future

class PahoDistributor @Inject()(config: Config) extends DistributorBase with StrictLogging {

  private val qos = config.getInt(MqttDistributorConf.QOS)
  private val broker = config.getString(MqttDistributorConf.BROKER_URL)
  private val clientId = config.getString(MqttDistributorConf.CLIENT_ID)
  private val userName = config.getString(MqttDistributorConf.USER_NAME)
  private val password = config.getString(MqttDistributorConf.PASSWORD)
  private val queue_prefix = config.getString(MqttDistributorConf.QUEUE_PREFIX)

  private val sampleClient: MqttClient = getClient

  def getClient: MqttClient = {
    try {
      val persistence = new MemoryPersistence()
      val client = new MqttClient(broker, clientId, persistence)
      val connOpts = new MqttConnectOptions()
      connOpts.setUserName(userName)
      connOpts.setPassword(password.toCharArray)
      connOpts.setCleanSession(true)
      logger.info("MQTT Client connecting to broker: " + broker)
      client.connect(connOpts)
      logger.info("MQTT Client connected.")
      client
    } catch {
      case me: MqttException =>
        logger.error("retrieving MQTT client failed: ", me)
        throw me
    }
  }

  override def sendIncident(incident: Array[Byte], customerId: String): Future[Boolean] = {
    try {
      val topic = queue_prefix + customerId
      val message = new MqttMessage(incident)
      message.setQos(qos)
      sampleClient.publish(topic, message)
      Future.successful(true)
    } catch {
      case ex: Throwable =>
        logger.error(s"failing to send mqtt message for customerId $customerId: ", ex)
        Future.successful(false)
    }
  }
}
