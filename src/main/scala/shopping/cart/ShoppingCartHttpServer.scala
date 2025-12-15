package shopping.cart

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ShoppingCartHttpServer {

  private val logger = LoggerFactory.getLogger(getClass)

  // JSON case classes for request/response
  case class AddItemRequest(itemId: String, quantity: Int)
  case class UpdateItemRequest(itemId: String, quantity: Int)
  case class Item(itemId: String, quantity: Int)
  case class CartResponse(items: Seq[Item], checkedOut: Boolean)
  case class ErrorResponse(error: String)

  // JSON formats
  implicit val addItemRequestFormat: RootJsonFormat[AddItemRequest] = jsonFormat2(AddItemRequest)
  implicit val updateItemRequestFormat: RootJsonFormat[UpdateItemRequest] = jsonFormat2(UpdateItemRequest)
  implicit val itemFormat: RootJsonFormat[Item] = jsonFormat2(Item)
  implicit val cartResponseFormat: RootJsonFormat[CartResponse] = jsonFormat2(CartResponse)
  implicit val errorResponseFormat: RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse)

  def start(
      interface: String,
      port: Int,
      system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    implicit val timeout: Timeout =
      Timeout.create(system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

    val sharding = ClusterSharding(system)
    val routes = createRoutes(sharding)

    val bound = Http()
      .newServerAt(interface, port)
      .bind(routes)
      .map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "HTTP server online at http://{}:{}/",
          address.getHostString,
          address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  private def createRoutes(sharding: ClusterSharding)(implicit ec: ExecutionContext, timeout: Timeout): Route = {
    implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
      case _: TimeoutException =>
        complete(StatusCodes.ServiceUnavailable -> ErrorResponse("Operation timed out"))
      case ex: Exception =>
        complete(StatusCodes.BadRequest -> ErrorResponse(ex.getMessage))
    }

    handleExceptions(exceptionHandler) {
      pathPrefix("carts" / Segment) { cartId =>
        val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)

        concat(
          // GET /carts/{cartId} - Get cart
          get {
            pathEndOrSingleSlash {
              val response: Future[CartResponse] = entityRef
                .ask(ShoppingCart.Get(_))
                .map(toCartResponse)
              onSuccess(response) { cart =>
                if (cart.items.isEmpty && !cart.checkedOut)
                  complete(StatusCodes.NotFound -> ErrorResponse(s"Cart $cartId not found"))
                else
                  complete(cart)
              }
            }
          },
          // POST /carts/{cartId}/items - Add item to cart
          path("items") {
            post {
              entity(as[AddItemRequest]) { request =>
                logger.info("addItem {} to cart {}", request.itemId, cartId)
                val response: Future[CartResponse] = entityRef
                  .askWithStatus(ShoppingCart.AddItem(request.itemId, request.quantity, _))
                  .map(toCartResponse)
                onSuccess(response) { cart =>
                  complete(StatusCodes.Created -> cart)
                }
              }
            }
          },
          // PUT /carts/{cartId}/items/{itemId} - Update item quantity
          path("items" / Segment) { itemId =>
            put {
              entity(as[UpdateItemRequest]) { request =>
                logger.info("updateItem {} in cart {}", itemId, cartId)
                val response: Future[CartResponse] =
                  if (request.quantity == 0)
                    entityRef
                      .askWithStatus(ShoppingCart.RemoveItem(itemId, _))
                      .map(toCartResponse)
                  else
                    entityRef
                      .askWithStatus(ShoppingCart.AdjustItemQuantity(itemId, request.quantity, _))
                      .map(toCartResponse)
                onSuccess(response) { cart =>
                  complete(cart)
                }
              }
            }
          },
          // DELETE /carts/{cartId}/items/{itemId} - Remove item from cart
          path("items" / Segment) { itemId =>
            delete {
              logger.info("removeItem {} from cart {}", itemId, cartId)
              val response: Future[CartResponse] = entityRef
                .askWithStatus(ShoppingCart.RemoveItem(itemId, _))
                .map(toCartResponse)
              onSuccess(response) { cart =>
                complete(cart)
              }
            }
          },
          // POST /carts/{cartId}/checkout - Checkout cart
          path("checkout") {
            post {
              logger.info("checkout cart {}", cartId)
              val response: Future[CartResponse] = entityRef
                .askWithStatus(ShoppingCart.Checkout(_))
                .map(toCartResponse)
              onSuccess(response) { cart =>
                complete(cart)
              }
            }
          }
        )
      }
    }
  }

  private def toCartResponse(summary: ShoppingCart.Summary): CartResponse = {
    CartResponse(
      items = summary.items.map { case (itemId, quantity) => Item(itemId, quantity) }.toSeq,
      checkedOut = summary.checkedOut
    )
  }
}
