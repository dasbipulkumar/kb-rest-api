package controllers

import models.RRequest._
import play.api.libs.json._
import play.api.mvc._
import utils.Logger
/*import utils.{Logger, RService}*/

object Application extends Controller with Logger {
  val nodeName = System.getenv("NODE_NAME")

 /* def invokeIris = Action {
    log.debug("Running iris")
    val rj = new RService()
    Ok(rj.evalIris())

  }

  def lm = Action(BodyParsers.parse.json) { request =>
    log.debug("Running lm")
    val b = request.body.validate[ModelRequest]
    val rj = new RService()
    b.fold(
      errors => {
        BadRequest(Json.obj("node" -> nodeName, "status" -> "OK", "message" -> JsError.toFlatJson(errors)))
      },
      r => {
        Ok(Json.obj("node" -> nodeName, "status" -> "OK", "out" -> rj.executeRegressionModel(r.data)))
      }
    )
  }

  def invokeR = Action(BodyParsers.parse.json) { request =>
    log.debug("Running R script")
    val b = request.body.validate[RRequest]
    val rj = new RService()
    b.fold(
      errors => {
        BadRequest(Json.obj("node" -> nodeName, "status" -> "OK", "message" -> JsError.toFlatJson(errors)))
      },
      r => {

        Ok(Json.obj("node" -> nodeName, "status" -> "OK", "out" -> rj.evaluateScript(r.code)))
      }
    )
  }*/
}
