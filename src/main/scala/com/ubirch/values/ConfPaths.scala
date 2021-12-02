package com.ubirch.values

/**
  * Object that contains configuration keys
  */
object ConfPaths {

  trait IncidentConsumerConf {
    final val BOOTSTRAP_SERVERS = "incidentService.kafkaApi.kafkaConsumer.bootstrapServers"
    final val FETCH_MAX_BYTES = "incidentService.kafkaApi.kafkaConsumer.fetchMaxBytes"
    final val GRACEFUL_TIMEOUT = "incidentService.kafkaApi.kafkaConsumer.gracefulTimeout"
    final val GROUP_ID = "incidentService.kafkaApi.kafkaConsumer.groupId"
    final val NIOMON_ERROR_TOPIC = "incidentService.kafkaApi.kafkaConsumer.niomonErrorTopic"
    final val EVENTLOG_ERROR_TOPIC = "incidentService.kafkaApi.kafkaConsumer.eventlogErrorTopic"
    final val MAX_PARTITION_FETCH_BYTES = "incidentService.kafkaApi.kafkaConsumer.maxPartitionFetchBytesConfig"
    final val MAX_POLL_RECORDS = "incidentService.kafkaApi.kafkaConsumer.maxPollRecords"
    final val MAX_TIME_AGGREGATION_SECONDS = "incidentService.kafkaApi.kafkaConsumer.maxTimeAggregationSeconds"
    final val METRICS_SUB_NAMESPACE = "incidentService.kafkaApi.kafkaConsumer.metricsSubNamespace"
    final val PREFIX = "incidentService.kafkaApi.kafkaConsumer.prefix"
    final val RECONNECT_BACKOFF_MAX_MS = "incidentService.kafkaApi.kafkaConsumer.reconnectBackoffMaxMsConfig"
    final val RECONNECT_BACKOFF_MS = "incidentService.kafkaApi.kafkaConsumer.reconnectBackoffMsConfig"
  }

  trait IncidentProducerConf {
    final val BOOTSTRAP_SERVERS = "incidentService.kafkaApi.kafkaProducer.bootstrapServers"
    final val LINGER_MS = "incidentService.kafkaApi.kafkaProducer.lingerMS"
    final val ERROR_TOPIC_PATH = "incidentService.kafkaApi.kafkaProducer.errorTopic"
    final val PUBLISH_TOPIC_PREFIX = "incidentService.kafkaApi.kafkaProducer.publishTopicPrefix"
  }

  trait TenantRetrieverConf {
    final val THING_API_URL = "incidentService.tenantRetriever.thingApiUrl"
    final val REDIS_DEVICE_MAP = "incidentService.tenantRetriever.deviceCacheName"
    final val TTL_REDIS_DEVICE_MAP = "incidentService.tenantRetriever.ttl"
  }

  trait ServiceConf {
    final val SERVICE_NAME = "incidentService.name"
    final val THREAD_POOL_SIZE = "incidentService.threadPoolSize"
  }

  trait RedisConf {
    final val HOST = "redis.host"
    final val PORT = "redis.port"
    final val PASSWORD = "redis.password"
    final val DATABASE = "redis.database"
  }

  trait PrometheusConf {
    final val PORT = "incidentService.metrics.prometheus.port"
  }

  trait MqttDistributorConf {
    final val BROKER_URL = "incidentService.mqttDistributor.brokerUrl"
    final val CLIENT_ID = "incidentService.mqttDistributor.clientId"
    final val QOS = "incidentService.mqttDistributor.qualityOfService"
    final val USER_NAME = "incidentService.mqttDistributor.userName"
    final val PASSWORD = "incidentService.mqttDistributor.password"
    final val QUEUE_PREFIX = "incidentService.mqttDistributor.queuePrefix"
    final val MAX_INFLIGHT_MESSAGES = "incidentService.mqttDistributor.maxInflightMessages"
  }

  object IncidentConsumerConf extends IncidentConsumerConf

  object IncidentProducerConf extends IncidentProducerConf

  object TenantRetrieverConf extends TenantRetrieverConf

  object ServiceConf extends ServiceConf

  object RedisConf extends RedisConf

  object PrometheusConf extends PrometheusConf

  object MqttDistributorConf extends MqttDistributorConf

}
