package utils

import java.net.InetAddress

import com.typesafe.config.ConfigFactory
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress

/**
  * Created by keerathj on 2/8/16.
  */
object EsClientHandler {

  private val esClient = buildEsClient()

  private def buildEsClient() = {
    val esConfig = ConfigFactory.load("querySearch.conf")
    val esHost = /*esConfig.getString("es.ip")*/ "127.0.0.1"
    val esport = /*esConfig.getString("es.port").toInt*/ "9300".toInt
    /*val eshttp = /*esConfig.getString("es.http").toInt*/*/
    val clusterName = /*esConfig.getString("es.cluster.nameWithBool")*/ "elasticsearch"
    val settings = Settings.settingsBuilder()
      .put("cluster.name", clusterName)
      .put("client.transport.sniff", true).build()

    TransportClient.builder().settings(settings).build
      .addTransportAddress(new InetSocketTransportAddress(
        InetAddress.getByName(esHost), esport))
  }

}
