package infrastructure.http

import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.dsl.io.*

import logstage.IzLogger

import smithy4s.hello.*

import Converter.*


import cats.data._
import org.http4s.HttpRoutes
import cats.syntax.all._
import org.typelevel.ci.CIString

object Middleware {

  def withRequestInfo(
      routes: HttpRoutes[IO],
      local: IOLocal[Option[RequestInfo]]
  ): HttpRoutes[IO] =
    HttpRoutes[IO] { request =>
      val requestInfo = Some(RequestInfo(
         request.headers.headers.find( key => key.name == CIString("userId")).map( el => el.value )
      ))
        
      OptionT.liftF(local.set(requestInfo)) *> routes(request)
    }

}


class ServerRoutes(
               logger: Option[IzLogger]
               ):
      
  def getAll(local: IOLocal[Option[RequestInfo]]): Resource[IO, HttpRoutes[IO]] = {
    val getRequestInfo: IO[RequestInfo] = local.get.flatMap {
      case Some(value) => IO.pure(value)
      case None => IO.raiseError(new IllegalAccessException("Tried to access the value outside of the lifecycle of an http request"))
    }
      SimpleRestJsonBuilder.routes(
      HttpServerImpl2(logger, getRequestInfo).transform(Converter.toIO)

    )
      .mapErrors(
        ex => ServiceUnavailableError("", "", ex.getMessage())
      )
    .resource
      .map { routes =>
        Middleware.withRequestInfo(routes, local)
      }
    } 
    // }

    private val healthCheck: HttpRoutes[IO] = HttpRoutes.of[IO]:
        // TODO: Check DB connection
        case GET -> Root / "alive" => Ok()
        case GET -> Root / "ready" => Ok()
