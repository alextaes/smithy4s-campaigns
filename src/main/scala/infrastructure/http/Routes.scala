package infrastructure.http

import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.dsl.io.*
import logstage.IzLogger
import smithy4s.hello as h
import Converter.*
import org.http4s.HttpRoutes
import cats.syntax.all.*
import io.opentelemetry.api.GlobalOpenTelemetry
import org.typelevel.ci.CIString
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer

import telemetry.ServerMiddleware.*
import telemetry.ServerMiddleware.ServerMiddlewareOps
import telemetry.*

import cats.data.Kleisli
import cats.effect.{Async, Sync}
import cats.implicits.*
import infrastructure.http.RequestInfo
import logstage.IzLogger
import org.http4s.{Header, HttpApp, Request}
import org.typelevel.ci.CIString
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{SpanKind, Status, Tracer}
import doobie.util.yolo

import cats.data.OptionT
// import cats.instances.io.*

class Middleware(
    routes: HttpRoutes[IO],
    local: IOLocal[Option[RequestInfo]],
    tracer: Tracer[IO]
)
// (using )
{

  def withRequestInfo: HttpRoutes[IO] =
    HttpRoutes[IO] { request =>

      println("MIDDLEWARE")

      // tracer.currentSpanContext.foreach{ span =>
      //   println(s"TRACE_ID: ${span.get.traceId}")
      // }
      println("END - MIDDLEWARE")
      println(request.headers.headers)

      val res2: IO[Response[IO]] = tracer
        .spanBuilder("handle-incoming-request")
        .addAttribute(Attribute("http.method", request.method.name))
        .addAttribute(Attribute("http.url", request.uri.renderString))
        .withSpanKind(SpanKind.Server)
        .build
        .use[Response[IO]] { span =>
          val requestInfo = Some(
            RequestInfo(
              request.headers.headers
                .find(key => key.name == CIString("userId"))
                .map(el => el.value),
              Some(span.context.traceIdHex)
              // request.headers.headers
              //   .find(key => key.name == CIString("traceId2"))
              //   .map(el => el.value),
            )
          )

          val xxx = OptionT.liftF(local.set(requestInfo)) *> routes(request)
          // val xxx: OptionT[IO, Response[IO]] = routes(request)

          val tsts: IO[Response[IO]] = xxx.value.flatMap {
            case Some(value) => IO.pure(value)
            case None        => IO.raiseError(new Exception("Error"))
          }

          // val res: IO[Response[IO]] = ???
          val res: IO[Response[IO]] = for {
            response <- tsts
            _ <- span.addAttribute(
              Attribute("http.status-code", response.status.code.toLong)
            )
            _ <- {
              if (response.status.isSuccess) span.setStatus(Status.Ok)
              else span.setStatus(Status.Error)
            }
          } yield {
            val traceIdHeader =
              Header.Raw(CIString("traceId"), span.context.traceIdHex)
            response.putHeaders(traceIdHeader)
            // val ttt : Response[IO] = ???
            // ttt.putHeaders(traceIdHeader)
            // ttt
          }
          res
          // ???
        }

      // xx
      OptionT.liftF(res2)
      // res2
      // ???

    }
}

class ServerRoutes[F[_]: Sync: Async: Concurrent: Tracer](
    logger: Option[IzLogger],
    traceProvider: Tracer[IO]
):

  import org.typelevel.otel4s.trace.Tracer.Implicits._

  def getAll(
      local: IOLocal[Option[RequestInfo]]
  ): Resource[IO, HttpRoutes[IO]] = {
    val getRequestInfo: IO[RequestInfo] =
      local.get.flatMap {
        case Some(value) => IO.pure(value)
        case None =>
          IO.raiseError(
            new IllegalAccessException(
              "Tried to access the value outside of the lifecycle of an http request"
            )
          )
      }

    val md = new SmithyMiddleware[IO]()
    SimpleRestJsonBuilder
      .routes(
        HttpServerImpl(logger, getRequestInfo)
          .transform(Converter.toIO)
      )
      // .middleware(md())
      .resource
      .map { routes =>
        new Middleware(routes, local, traceProvider).withRequestInfo
      }
  }
