package utils

import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.functionscore.script.ScriptScoreFunctionBuilder
import org.elasticsearch.script.Script

object QueryBuilderUtils {

  def makeIntentQuery(words: String) = {
    val matchQuery = QueryBuilders.matchQuery("searchText", words)
    val functionScore = new ScriptScoreFunctionBuilder(
      new Script("""_score * doc["score"].value"""))
    QueryBuilders.functionScoreQuery(matchQuery, functionScore)
  }

  def makeSrchQuery(types: List[Type]) = {
    val onlyTypes = types.filter(_.props.isEmpty)
    val typeProps = types.filter(_.props.nonEmpty)

    val typeQueries = onlyTypes.map(makeTypeQuery)
    val typePropsQueries = typeProps.map(makeTypePropsQuery)

    val boolQuery = QueryBuilders.boolQuery()
    typeQueries.foreach(boolQuery.should)
    typePropsQueries.foreach(boolQuery.should)
    boolQuery
  }

  def makeSrcFileQuery(files: List[String]) = {
    val termQueries = files.map(file => QueryBuilders.termQuery("fileName", file))
    val boolQuery = QueryBuilders.boolQuery()
    termQueries.foreach(boolQuery.should)
    boolQuery
  }

  private def makeTypeQuery(`type`: Type) = {
    val termQuery = getTypeQuery(`type`.nameWithBool.name.toLowerCase)
    val shouldOrMustQuery =
      if (`type`.nameWithBool.isMust) {
        QueryBuilders.boolQuery().must(termQuery)
      } else {
        QueryBuilders.boolQuery().should(termQuery)
      }
    QueryBuilders.boolQuery().filter(shouldOrMustQuery)
  }

  private def makeTypePropsQuery(`type`: Type) = {
    val nameWithBool = `type`.nameWithBool
    val termQuery = getTypeQuery(nameWithBool.name.toLowerCase)
    val termsQuery = getPropsQuery(`type`.props.map(_.toLowerCase))
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

  private def getTypeQuery(typeName: String) =
    QueryBuilders.termQuery("contexts.types.name", typeName)

  private def getPropsQuery(props: List[String]) =
    QueryBuilders.termsQuery("contexts.types.props", props: _*)

}
