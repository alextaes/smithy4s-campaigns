package telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer

object OtelServiceModule {

  private def otelResource[F[_] : Sync : Async : LiftIO]: Resource[F, Otel4s[F]] = {
    Resource
      .eval(Sync[F].delay(GlobalOpenTelemetry.get))
      .evalMap(OtelJava.forAsync[F])
  }

  private def buildInternal[F[_] : Sync : Async : Concurrent : LiftIO](otel: Otel4s[F]) =
    for {
      traceProvider <- otel.tracerProvider.get("root")
    } yield {
      implicit val tracer: Tracer[F] = traceProvider
    }

  def build[F[_] : Sync : Async : Concurrent : LiftIO] = {
    otelResource.use(buildInternal[F])
  }

}
