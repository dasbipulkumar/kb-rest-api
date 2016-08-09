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

package com.kodebeagle.service

import com.kodebeagle.model.{EsSearchRequest, EsSuggestRequest, FileName, MetadataReq, Sort, TypeName}
import com.kodebeagle.util.{EsClient, Logger}
import com.kodebeagle.util.JsonUtils._
import com.kodebeagle.util.QueryBuilderUtils._
import com.kodebeagle.util.QueryIntentionUtils._
import org.elasticsearch.search.sort.SortOrder
import play.api.libs.json.JsObject

object EsService extends App with Logger {

  /* Queries types i.e. (name and props) against java/typerefs */

  def queryForTypeRefs(queryString: String): JsObject = {
    val query = makeClientQuery(queryString)
    val types = resolveTypes(query.queries)
    val fromSize = (query.from, query.size)

    val searchRequest = EsSearchRequest(indices = List(JAVA_INDEX),
      types = List(SEARCH_TYPE), query = makeSearchQuery(types),
      Array("payload"), from = fromSize._1, size = fromSize._2)

    val response = EsClient.search(searchRequest)
    val typeNames = types.map(_.nameWithBool.name)
    transformTypeRefs(response.hits, typeNames, response.count)
  }

  /* Queries for props against java/aggregation with types names */

  def queryForProps(typeNamesJson: String): String = {
    val request = EsSearchRequest(indices = List(JAVA_INDEX), types = List(AGG_TYPE),
      query = makeAggQuery(toListOfTypeNames(typeNamesJson)),
      includeFields = Array("name", "methods.name", "methods.count"), from = 0,
      size = 1, Sort("score", SortOrder.DESC))
    transformProps(EsClient.search(request).hits)
  }

  /* Queries for metadata against java/filemetadata with either fileName or fileType */

  def queryForMetadata(metaReq: MetadataReq): String = {
    val metaQuery = metaReq match {
      case FileName(name) => makeFileMetaQuery("fileName", name)
      case TypeName(name) => makeFileMetaQuery("fileTypes.fileType", name.toLowerCase)
    }

    val request = EsSearchRequest(indices = List(JAVA_INDEX),
      types = List(METADATA_TYPE), query = metaQuery)
    val response = EsClient.search(request)
    transformMetadata(response.hits)
  }

  /* Queries for type and method names against java/_suggest with text */

  def queryForSuggestion(suggestText: String): String = {
    val typeSuggester = makeSuggester("types", "typeSuggest")
    val methodSuggester = makeSuggester("props", "methodSuggest")
    val request = EsSuggestRequest(JAVA_INDEX, suggestText, List(typeSuggester, methodSuggester))

    val response = EsClient.suggest(request)
    transformSuggestResponse(response)
  }

  /* Queries for file content against java/sourcefile with file names */

  def queryForSources(fileNames: List[String]): Map[String, String] = {
    val searchRequest = EsSearchRequest(indices = List(JAVA_INDEX),
      types = List(SOURCE_TYPE), query = makeIdsQuery(List("sourcefile"), fileNames),
      includeFields = Array("fileName", "fileContent"))

    val hits = EsClient.search(searchRequest).hits
    transformSrcFile(hits)
  }

  /* Queries for types against java/typereference for file names and
     then queries these file names against java/sourcefile for file
     content and stitches their response */

  def queryTypeRefsAndSources(queryString: String): String = {
    val payload = queryForTypeRefs(queryString)
    val fileNames = extractFileNames(payload)
    val sources = queryForSources(fileNames)
    combineTypeRefsAndSources(payload, sources)
  }

  /* Queries for file details against java/filedetails */

  def queryForFileDetails(fileName: String): String = {
    val searchRequest = EsSearchRequest(
      indices = List(JAVA_INDEX), types = List(FILEDETAILS_TYPE),
      query = makeIdsQuery(List(FILEDETAILS_TYPE), List(fileName)),
      from = 0, size = 1)

    val response = EsClient.search(searchRequest)
    toJson(response.hits)
  }

  /* Queries for repo details against java/repodetails */

  def queryForRepoDetails(repoName: String): String = {
    val searchRequest = EsSearchRequest(
      indices = List(JAVA_INDEX), types = List(REPODETAILS_TYPE),
      query = makeIdsQuery(List(REPODETAILS_TYPE), List(repoName)),
      includeFields = Array("gitHubInfo", "gitHistory.mostChanged"),
      from = 0, size = 1)

    val response = EsClient.search(searchRequest)
    toJson(response.hits)
  }
}