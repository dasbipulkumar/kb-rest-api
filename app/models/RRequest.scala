package models

import play.api.libs.json.Json

/**
  * Created by sachint on 8/3/16.
  */
object RRequest {

  case class RRequest(id: String, code: String)
  case class ModelRequest(id: String, model: String, data: String, dataType: String)

  implicit val rWrites = Json.writes[RRequest]
  implicit val rReads = Json.reads[RRequest]

  implicit val mWrites = Json.writes[ModelRequest]
  implicit val mReads = Json.reads[ModelRequest]
}
