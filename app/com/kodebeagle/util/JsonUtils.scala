/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kodebeagle.util

import com.kodebeagle.model.{ClientQuery, QUERY_TYPE, Query}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsArray, _}

object JsonUtils {

  private implicit val queryReads: Reads[Query] = (
    (JsPath \ "term").read[String] and
      (JsPath \ "type").read[String].map(toQueryType)
    ) (Query.apply _)

  private implicit val clientQueryReads: Reads[ClientQuery] = (
    (JsPath \ "queries").read[List[Query]] and
      (JsPath \ "from").read[Int] and
      (JsPath \ "size").read[Int]
    ) (ClientQuery.apply _)

  private def toQueryType(queryType: String) = {
    if (queryType == "type") {
      QUERY_TYPE.TYPE
    } else if (queryType == "method") {
      QUERY_TYPE.METHOD
    } else  {
      QUERY_TYPE.WORD
    }
  }

  def extractFileNames(payload: JsObject): List[String] =
    ((payload \ "hits") \\ "fileName").map(_.as[String]).toList

  def transformSuggestResponse(response: String): String = {
    val jsObj = Json.parse(response).as[JsObject]

    val transformedFields = jsObj.value.map { case (key, value) =>
      val pickOptions = (__ \ 'options).json.pick
      (key, value.as[JsArray].value.head.as[JsObject].transform(pickOptions).get)
    }.toSeq
    JsObject(transformedFields).toString()
  }

  def makeClientQuery(query: String): ClientQuery = Json.parse(query).as[ClientQuery]

  def transformTypeRefs(hits: List[String], typeNames: List[String], hitCount: Long): JsObject = {
    val transformedHits = hits.flatMap { hit =>
      val payload = Json.parse(hit).as[JsObject] \ "payload"
      val file = (payload \ "file").as[JsString]
      val score = (payload \ "score").as[JsNumber]
      val types = (payload \ "types").as[JsArray]
      val filteredTypes = types.value.filter(`type` => typeNames.contains(
        (`type`.as[JsObject] \ "name").as[String]))

      if (filteredTypes.nonEmpty) {
        Some(JsObject(Seq(("types", JsArray(filteredTypes)),
          ("score", score), ("fileName", file))))
      } else {
        None
      }
    }
    JsObject(Seq("hits" -> JsArray(transformedHits), "total_hits" -> JsNumber(hitCount)))
  }

  def transformMetadata(hits: List[String]): String =
    JsArray(hits.map(hit => Json.parse(hit))).toString()

  def transformIntention(hits: List[String]): List[String] =
    hits.map { hit => (Json.parse(hit) \ "name").as[String] }

  def toListOfTypeNames(typeNames: String): List[String] =
    Json.parse(typeNames).as[List[String]].map(_.toLowerCase)

  def transformProps(hits: List[String]): String = {
    JsArray(hits.map { hit =>
      val obj = Json.parse(hit).as[JsObject]
      val name = obj \ "name"
      val methods = obj \ "methods"
      JsObject(Seq("typeName" -> name, "props" -> methods))
    }).toString
  }

  def transformSrcFile(srcHits: List[String]): Map[String, String] = {
    srcHits.map { hit =>
      val srcObj = Json.parse(hit).as[JsObject]
      val fName = (srcObj \ "fileName").as[String]
      val fContent = (srcObj \ "fileContent").as[String]
      (fName, fContent)
    }.toMap
  }

  def toJson(hits: List[String]): String = JsArray(hits.map(Json.parse)).toString()

  def combineTypeRefsAndSources(payload: JsObject, files: Map[String, String]): String = {
    val hits = (payload \ "hits").as[JsArray].value
    val total_hits = payload \ "total_hits"

    val hitsWithFileContent = hits.map {
      case obj: JsObject =>
        val fileName = (obj \ "fileName").as[String]
        obj + ("fileContent", JsString(files(fileName)))
    }

    JsObject(Seq("hits" -> JsArray(hitsWithFileContent),
      "total_hits" -> total_hits)).toString
  }
}
