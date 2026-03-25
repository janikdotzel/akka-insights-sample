package shopping.stream

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object StreamDemoHttpServer {

  private val logger = LoggerFactory.getLogger(getClass)

  case class RunRequest(count: Option[Int])

  implicit val runRequestFormat: RootJsonFormat[RunRequest] = jsonFormat1(RunRequest)
  implicit val pipelineResultFormat: RootJsonFormat[PipelineResult] = jsonFormat8(PipelineResult)

  private val DefaultCount = 100

  def routes(implicit system: ActorSystem[_]): Route = {
    pathPrefix("stream") {
      path("run") {
        concat(
          // POST /stream/run with optional JSON body {"count": 200}
          post {
            entity(as[RunRequest]) { request =>
              val count = request.count.getOrElse(DefaultCount)
              logger.info("Starting sensor data pipeline with {} messages", count)
              val result = SensorDataPipeline.run(count)
              onSuccess(result) { pipelineResult =>
                complete(StatusCodes.OK -> pipelineResult)
              }
            }
          },
          // GET /stream/run?count=200
          get {
            parameter("count".as[Int].withDefault(DefaultCount)) { count =>
              logger.info("Starting sensor data pipeline with {} messages (GET)", count)
              val result = SensorDataPipeline.run(count)
              onSuccess(result) { pipelineResult =>
                complete(StatusCodes.OK -> pipelineResult)
              }
            }
          })
      }
    }
  }
}
