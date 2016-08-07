package utils

import java.util.{List => JList, Map => JMap}

import org.elasticsearch.search.SearchHit
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsArray, _}

import scala.collection.JavaConversions._
import scala.collection.mutable

case class ClientQuery(text: String, queryType: QUERY_TYPE.QUERY_TYPE)

case class Payload(fName: String, score: Int, types: List[PayloadType], var fContent: String)

case class PayloadType(name: String, props: List[PayloadProp])

case class PayloadProp(name: String, lines: List[List[Int]])

object JsonUtils {

  implicit val queryReads: Reads[ClientQuery] = (
    (JsPath \ "term").read[String] and
      (JsPath \ "type").read[String].map(toQueryType)
    ) (ClientQuery.apply _)

  implicit val propWrites = new Writes[PayloadProp] {
    def writes(payloadProp: PayloadProp) = Json.obj(
      "name" -> payloadProp.name,
      "lines" -> payloadProp.lines
    )
  }

  implicit val typeWrites = new Writes[PayloadType] {
    def writes(payloadType: PayloadType) = Json.obj(
      "name" -> payloadType.name,
      "props" -> payloadType.props
    )
  }

  implicit val payloadWrites = new Writes[Payload] {
    def writes(payload: Payload) = Json.obj(
      "fName" -> payload.fName,
      "fContent" -> payload.fContent,
      "score" -> payload.score,
      "types" -> payload.types
    )
  }

  private def toQueryType(queryType: String) = {
    if (queryType == "type") {
      QUERY_TYPE.TYPE
    } else if (queryType == "method") {
      QUERY_TYPE.METHOD
    } else {
      QUERY_TYPE.WORD
    }
  }

  def transformSuggestResponse(response: String): String = {
    val jsObj = Json.parse(response).as[JsObject]

    val transformedFields = jsObj.value.map { case (key, value) =>
      val pickOptions = (__ \ 'options).json.pick
      (key, value.as[JsArray].value.head.as[JsObject].transform(pickOptions).get)
    }.toSeq
    JsObject(transformedFields).toString()
  }

  def toClientQueries(searchText: String) = Json.parse(searchText).as[List[ClientQuery]]

  def toPayloads(hits: Array[SearchHit], typeNames: List[String]): List[Payload] = {
    hits.flatMap { hit =>
      val payload = hit.sourceAsMap().get("payload").asInstanceOf[JMap[String, AnyRef]]
      val file = payload.get("file").asInstanceOf[String]
      val score = payload.get("score").asInstanceOf[Integer]
      val types = payload.get("types").asInstanceOf[JList[AnyRef]]

      println(Json.parse(hit.getSourceAsString) \\ "name")
      val filteredTypes = types.filter(`type` => typeNames.contains(
        `type`.asInstanceOf[JMap[String, AnyRef]].get("name").toString))
      if (filteredTypes.nonEmpty) {
        /* found it convenient to set file content to empty
         and set it back once source if fetched */
        Some(Payload(file, score, toPayloadType(filteredTypes), ""))
      } else {
        None
      }
    }.toList
  }

  def combineResponseAsJson(payloads: List[Payload],
                            files: Map[String, String]) = Json.toJson(
    payloads.map { payload =>
      payload.fContent = files(payload.fName)
      payload
    })

  private def toPayloadType(types: mutable.Buffer[AnyRef]) = {
    types.map {
      case t: JMap[String, AnyRef] =>
        val props = t.get("props") match {
          case p: JList[AnyRef] =>
            p.map {
              case p: JMap[String, AnyRef] =>
                PayloadProp(p.get("name").toString,
                  p.get("lines").asInstanceOf[JList[JList[Int]]]
                    .map(_.toList).toList)
            }.toList
        }
        PayloadType(t.get("name").toString, props)
    }.toList
  }
}
