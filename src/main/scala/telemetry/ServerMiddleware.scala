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
        val res2: F[Response[F]] = Tracer[F]
          .spanBuilder("handle-incoming-request")
          .addAttribute(Attribute("http.method", req.method.name))
          .addAttribute(Attribute("http.url", req.uri.renderString))
          .withSpanKind(SpanKind.Server)
          .build
          .use { span =>

            val res = for {
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
            res

          }
        res2
      }
    }
  }
}

object ServerMiddleware extends ServerMiddleware

import org.http4s.server.middleware.*
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.Hints
import smithy4s.http4s.ServerEndpointMiddleware

import cats.FlatMap
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Sync
import cats.effect.SyncIO
import cats.effect.std.UUIDGen
import cats.syntax.all._
import cats.~>
import org.typelevel.ci._
import org.typelevel.vault.Key

class SmithyMiddleware[F[_]: Sync: Async: Tracer]:

  private[this] val requestIdHeader = ci"X-trarce-ID"

  val requestIdAttrKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  import cats.effect.unsafe.implicits.global

  private def middleware: HttpApp[F] => HttpApp[F] = { service =>
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
          val traceIdHeader =
            Header.Raw(CIString("traceId2"), span.context.traceIdHex)
          println("okokok")

          for {
            response <- service(
              req
                .withAttribute(requestIdAttrKey, "traceId2")
                .putHeaders(traceIdHeader)
            )
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

  def apply(): ServerEndpointMiddleware[F] =
    new ServerEndpointMiddleware.Simple[F] {
      def prepareWithHints(
          serviceHints: Hints,
          endpointHints: Hints
      ): HttpApp[F] => HttpApp[F] = middleware

    }
