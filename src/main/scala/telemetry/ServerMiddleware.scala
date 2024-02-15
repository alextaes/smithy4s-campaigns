package telemetry

import cats.data.Kleisli
import cats.effect.{Async, Sync}
import cats.implicits.*
import infrastructure.http.RequestInfo
import logstage.IzLogger
import org.http4s.{Header, HttpApp, Request}
import org.typelevel.ci.CIString
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{SpanKind, Status, Tracer}

trait ServerMiddleware {
  implicit class ServerMiddlewareOps[F[_]: Sync: Async: Tracer](
      service: HttpApp[F]
  ) {
    def traced: HttpApp[F] = {
      Kleisli { (req: Request[F]) =>
        Tracer[F]
          .spanBuilder("handle-incoming-request")
          .addAttribute(Attribute("http.method", req.method.name))
          .addAttribute(Attribute("http.url", req.uri.renderString))
          .withSpanKind(SpanKind.Server)
          .build
          .use { span =>
            for {
              response <- service(req)
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
              // println(s"OTEL4s traces: ${traceIdHeader.name} -> value: ${traceIdHeader.value}")
              // local.get.flatMap {
              //   case Some(value) =>
              //     Some(logger).foreach(_.info(s"REQUEST_INFO: ${value.userId}, span: ${span.toString}"))
              //     IO.println(s"REQUEST_INFO: ${value.userId}, span: ${span.toString}")
              //   case None => IO.println("None")
              // }
              response.putHeaders(traceIdHeader)
            }
          }
      }
    }
  }
}

object ServerMiddleware extends ServerMiddleware

import org.http4s.server.middleware.*
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.Hints
import smithy4s.http4s.ServerEndpointMiddleware

class SmithyMiddleware[F[_]: Sync: Async: Tracer]:

  private def middleware(local: IOLocal[Option[RequestInfo]]): HttpApp[F] => HttpApp[F] = { service =>
    Kleisli { (req: Request[F]) =>

      val h = req.headers.headers
        .find(key => key.name == CIString("user"))
        .map(el => el.value)
      println(s"User header: $h")

      Tracer[F]
        .spanBuilder("handle-incoming-request")
        .addAttribute(Attribute("http.method", req.method.name))
        .addAttribute(Attribute("http.url", req.uri.renderString))
        .withSpanKind(SpanKind.Server)
        .build
        .use { span =>
          val traceIdHeader = Header.Raw(CIString("traceId"), span.context.traceIdHex)
          
          
          println(s"traceId header ${span.context.traceIdHex}")

          val requestInfo = Some(
            RequestInfo(
              req.headers.headers
                .find(key => key.name == CIString("userId"))
                .map(el => el.value),
                Some(span.context.traceIdHex)
            )
          )

          import cats.effect.unsafe.implicits.global
          //  OptionT.liftF(local.set(requestInfo)) *> routes(request)
           local.set(requestInfo).unsafeRunSync()
      
          for {
            response <- service(req)
            _ <- span.addAttribute(
              Attribute("http.status-code", response.status.code.toLong)
            )
            _ <- {
              if (response.status.isSuccess) span.setStatus(Status.Ok)
              else span.setStatus(Status.Error)
            }
          } yield {
            // val traceIdHeader =
            //   Header.Raw(CIString("traceId"), span.context.traceIdHex)
            println(
              s"OTEL4s traces: ${traceIdHeader.name} -> value: ${traceIdHeader.value}"
            )
            // local.get.flatMap {
            //   case Some(value) =>
            //     Some(logger).foreach(_.info(s"REQUEST_INFO: ${value.userId}, span: ${span.toString}"))
            //     IO.println(s"REQUEST_INFO: ${value.userId}, span: ${span.toString}")
            //   case None => IO.println("None")
            // }
            response.putHeaders(traceIdHeader)
          }
        }
    }

  }

  def apply(local: IOLocal[Option[RequestInfo]]): ServerEndpointMiddleware[F] =
    new ServerEndpointMiddleware.Simple[F] {
      def prepareWithHints(
          serviceHints: Hints,
          endpointHints: Hints
      ): HttpApp[F] => HttpApp[F] = middleware(local)

    }
