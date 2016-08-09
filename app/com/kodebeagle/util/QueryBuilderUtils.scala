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

import com.kodebeagle.model.Type
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder
import org.elasticsearch.index.query.functionscore.script.ScriptScoreFunctionBuilder
import org.elasticsearch.index.query.{BoolQueryBuilder, IdsQueryBuilder, QueryBuilder, QueryBuilders}
import org.elasticsearch.script.Script
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder

object QueryBuilderUtils {

  def makeIntentQuery(words: String): FunctionScoreQueryBuilder = {
    val matchQuery = QueryBuilders.matchQuery("searchText", words)
    val functionScore = new ScriptScoreFunctionBuilder(
      new Script("""_score * doc["score"].value"""))
    QueryBuilders.functionScoreQuery(matchQuery, functionScore)
  }

  def makeSearchQuery(types: List[Type]): BoolQueryBuilder = {
    val onlyTypes = types.filter(_.props.isEmpty)
    val typeProps = types.filter(_.props.nonEmpty)
    val typeQueries = onlyTypes.map(getTypeQuery)
    val typePropsQueries = typeProps.map(getTypePropsQuery)

    getBoolQueryWithShould(typeQueries, typePropsQueries)
  }

  def makeIdsQuery(types: List[String], ids: List[String]): IdsQueryBuilder =
    QueryBuilders.idsQuery(types: _ *).ids(ids: _*)


  def makeAggQuery(typeNames: List[String]): BoolQueryBuilder = {
    val termQueries = typeNames.map(getTermQuery("name", _))
    getBoolQueryWithShould(termQueries)
  }

  def makeSuggester(name: String, field: String): CompletionSuggestionBuilder = {
    val builder = new CompletionSuggestionBuilder(name)
    builder.field(field)
  }

  def makeFileMetaQuery(field: String, text: String): BoolQueryBuilder = {
    val termQuery = getTermQuery(field, text)
    getBoolQueryWithShould(List(termQuery))
  }

  private def getTypeQuery(`type`: Type) = {
    val termQuery = getTermQuery("contexts.types.name", `type`.nameWithBool.name.toLowerCase)
    val shouldOrMustQuery =
      if (`type`.nameWithBool.isMust) {
        QueryBuilders.boolQuery().must(termQuery)
      } else {
        QueryBuilders.boolQuery().should(termQuery)
      }
    QueryBuilders.boolQuery().filter(shouldOrMustQuery)
  }

  private def getTypePropsQuery(`type`: Type) = {
    val nameWithBool = `type`.nameWithBool
    val termQuery = getTermQuery("contexts.types.name", nameWithBool.name.toLowerCase)
    val termsQuery = getTermsQuery("contexts.types.props", `type`.props.map(_.toLowerCase))
    val boolQuery = QueryBuilders.boolQuery()

    if (nameWithBool.isMust) {
      boolQuery.must(termQuery)
      boolQuery.must(termsQuery)
    } else {
      boolQuery.must(termQuery)
      boolQuery.should(termsQuery)
    }

    val filterQuery = QueryBuilders.boolQuery().filter(boolQuery)
    QueryBuilders.nestedQuery("contexts.types", filterQuery)
  }

  private def getBoolQueryWithShould[T <: QueryBuilder](queryBuildersList: List[T]*) = {
    val boolQuery = QueryBuilders.boolQuery()
    queryBuildersList.foreach { queryBuilders =>
      queryBuilders.foreach(boolQuery.should)
    }
    boolQuery
  }

  private def getTermQuery(field: String, value: String) =
    QueryBuilders.termQuery(field, value)

  private def getTermsQuery(field: String, values: List[String]) =
    QueryBuilders.termsQuery(field, values: _*)
}
