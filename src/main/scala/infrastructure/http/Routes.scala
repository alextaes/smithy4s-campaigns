package infrastructure.http

import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.dsl.io.*
import logstage.IzLogger
import smithy4s.hello.*
import Converter.*
import cats.data.*
import org.http4s.HttpRoutes
import cats.syntax.all.*
import io.opentelemetry.api.GlobalOpenTelemetry
import org.typelevel.ci.CIString
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer

class Middleware[F[_] : Sync : Async : Concurrent : Tracer](
                                                             routes: HttpRoutes[IO],
                                                             local: IOLocal[Option[RequestInfo]]
                                                           ) {

  private def otelResource[F[_] : Sync : Async : LiftIO]: Resource[F, Otel4s[F]] = {
    Resource
      .eval(Sync[F].delay(GlobalOpenTelemetry.get))
      .evalMap(OtelJava.forAsync[F])
  }

  private def buildInternal[F[_] : Sync : Async : Concurrent : LiftIO](otel: Otel4s[F]) = {
    for {

      traceProvider <- otel.tracerProvider.get("smithy4s-campaigns")
      // metricsProvider <- otel.meterProvider.get("smithy4s-campaigns")

    } yield {
      implicit val tracer: Tracer[F] = traceProvider
      new Middleware[F](routes, local)
    }
  }

  def build[F[_] : Sync : Async : Concurrent : LiftIO] = {
    otelResource.use(buildInternal[F])
  }
  
  def withRequestInfo: HttpRoutes[IO] =
    HttpRoutes[IO] { request =>
      val requestInfo = Some(RequestInfo(
        request.headers.headers.find(key => key.name == CIString("userId")).map(el => el.value)
      ))

      OptionT.liftF(local.set(requestInfo)) *> routes(request)
    }
}


class ServerRoutes[F[_] : Sync : Async : Concurrent : Tracer](
               logger: Option[IzLogger]
               ):

  import org.typelevel.otel4s.trace.Tracer.Implicits._
  
  def getAll(local: IOLocal[Option[RequestInfo]]): Resource[IO, HttpRoutes[IO]] = {
    val getRequestInfo: IO[RequestInfo] = local.get.flatMap {
      case Some(value) => IO.pure(value)
      case None => IO.raiseError(new IllegalAccessException("Tried to access the value outside of the lifecycle of an http request"))
    }
    
      SimpleRestJsonBuilder.routes(
      HttpServerImpl(logger, getRequestInfo).transform(Converter.toIO)

    )
      .mapErrors(
        ex => ServiceUnavailableError("", "", ex.getMessage())
      )
    .resource
      .map { routes =>
        Middleware[IO](routes, local).withRequestInfo
      }
    } 
