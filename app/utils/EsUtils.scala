package utils

import java.net.InetAddress

import com.typesafe.config.ConfigFactory
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder
import play.api.libs.json.{JsObject, Json, _}
import utils.JsonUtils._
import utils.QueryBuilderUtils._

import scala.collection.JavaConversions._
import scala.util.parsing.json.JSONObject

case class TypeNameWithBool(name: String, isMust: Boolean)

case class Type(nameWithBool: TypeNameWithBool, props: List[String])

object QUERY_TYPE extends Enumeration {
  type QUERY_TYPE = Value
  val TYPE, METHOD, WORD = Value
}

object EsUtils {

  val JAVA_INDEX = "java"
  val SEARCH_TYPE = "typereference"
  val METADATA_TYPE = "filemetadata"
  val SOURCE_TYPE = "sourcefile"
  val AGG_TYPE = "aggregation"
  val AGG_SIZE = 3

  val client: TransportClient = buildClient()

  private def buildClient() = {
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

  def performSuggest(suggestText: String): String = {

    def getSuggestionBuilder(name: String, field: String) = {
      val builder = new CompletionSuggestionBuilder(name)
      builder.field(field)
    }

    val suggestRequestBuilder = client.prepareSuggest(JAVA_INDEX).setSuggestText(suggestText)
    suggestRequestBuilder.addSuggestion(getSuggestionBuilder("types", "typeSuggest"))
    suggestRequestBuilder.addSuggestion(getSuggestionBuilder("props", "methodSuggest"))
    val response = suggestRequestBuilder.execute().actionGet().getSuggest.toString
    transformSuggestResponse(response)
  }



  def performSearch(searchText: String) = {
    val queries = toClientQueries(searchText)
    val types = toTypes(queries)
    val payloads = queryTypeRefs(types)
    val srcHits = querySources(payloads)
    combineResponseAsJson(payloads, srcHits)
  }

  def querySources(payloads: List[Payload]) = {
    def getFiles(srcHits: List[SearchHit]): Map[String, String] = {
      srcHits.map { hit =>
        val source = hit.getSource
        val fName = source.get("fileName").toString
        val fContent = source.get("fileContent").toString
        (fName, fContent)
      }.toMap
    }

    val files = payloads.map(_.fName)
    val sourcesQuery = makeSrcFileQuery(files)

    val hits = client.prepareSearch(JAVA_INDEX).setTypes(SOURCE_TYPE)
      .setFetchSource(Array("fileName", "fileContent"), Array[String]())
      .setQuery(sourcesQuery).execute().get()
      .getHits.getHits.toList
    getFiles(hits)
  }

  def queryTypeRefs(types: List[Type]) = {
    val searchQuery = makeSrchQuery(types)
    val response = client.prepareSearch(JAVA_INDEX).setTypes(SEARCH_TYPE)
      .setFetchSource(Array("payload"), Array[String]())
      .addSort("payload.score", SortOrder.DESC)
      .setQuery(searchQuery).execute().get()

    toPayloads(response.getHits.getHits, types.map(_.nameWithBool.name))
  }

  def toTypes(queries: List[ClientQuery]): List[Type] = {

    def handleWords(words: Option[String]) = queryIntention(words).map(typeName =>
      Type(TypeNameWithBool(typeName, isMust = false), words.get.split(" ").toList))

    val typeQueries = queries.filter(_.queryType == QUERY_TYPE.TYPE)
      .map(query => Type(TypeNameWithBool(query.text, isMust = true), List()))

    val methodQueries = queries filter (_.queryType == QUERY_TYPE.METHOD) map { query =>
      val (typeName, method) = query.text.splitAt(query.text.lastIndexOf('.'))
      Type(TypeNameWithBool(typeName, isMust = true), List(method.stripPrefix(".")))
    }

    val wordsQuery = handleWords(queries.find(_.queryType == QUERY_TYPE.WORD).map(_.text))

    val typeGroup = (typeQueries ++ methodQueries ++ wordsQuery)
      .groupBy(_.nameWithBool).mapValues(_.flatMap(_.props))

    typeGroup.map {
      case (typNameWithBool, props) => Type(typNameWithBool, props)
    }.filter(_.nameWithBool.name.nonEmpty).toList
  }

  def queryIntention(mayBeWords: Option[String]) = {
    mayBeWords.map { words =>
      val response = client.prepareSearch(JAVA_INDEX).setTypes(AGG_TYPE)
        .setFetchSource(Array("name"), Array[String]()).setQuery(makeIntentQuery(words))
        .setSize(AGG_SIZE).execute().actionGet()

      response.getHits.getHits.flatMap(_.getSource.values.map(_.toString)).toList
    }.getOrElse(List[String]())
  }

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis()
    println(performSuggest("file"))
    /*println(performSearch(
      """[
        |{
        |	"term" : "socket read",
        |	"type" : "word"
        |}
        |]""".stripMargin))*/
    println(System.currentTimeMillis() - start)
  }
}
