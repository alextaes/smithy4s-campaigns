package main

import Configs.*

import distage.Injector
import distage.ModuleDef
import distage.config.{AppConfig, ConfigModuleDef}
import com.typesafe.config.ConfigFactory
import org.http4s.server.Server

import logstage.{ConsoleSink, IzLogger, Trace}
import izumi.logstage.api.routing.StaticLogRouter
import infrastructure.resources.*

import io.opentelemetry.api.GlobalOpenTelemetry
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.metrics.Meter

object DI:

  val configModule =
    new ConfigModuleDef:
      makeConfig[HttpServerConfig]("httpServer")
      make[AppConfig].from(
        AppConfig.provided(
          ConfigFactory
            .defaultApplication()
            .getConfig("app")
            .resolve()
        )
      )

  val mainModule =
    new ModuleDef:

      make[IzLogger].from: () =>
        val textSink = ConsoleSink.text(colored = true)
        val sinks = List(textSink)
        val res = IzLogger(Trace, sinks)
        StaticLogRouter.instance.setup(res.router)
        res

      make[Otel4s[IO]].fromResource: 
        Resource
         .eval(Sync[IO].delay(GlobalOpenTelemetry.get))
         .evalMap(OtelJava.forAsync[IO])

      make[HttpServerResource[IO]].from: (
          config: HttpServerConfig,
          logger: IzLogger
      ) =>
        given HttpServerConfig = config
        HttpServerResource[IO](logger)

object App extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    import DI.*

    Injector[IO]().produceRun(
      mainModule ++ configModule
    ): (
        httpServer: HttpServerResource[IO],
        otel: Otel4s[IO]
    ) =>

      val program: IO[Unit] =
        IOLocal(Option.empty[infrastructure.http.RequestInfo]).flatMap {
          local =>
            for
              traceProvider <- otel.tracerProvider.get("tickets-service")
              metricsProvider: Meter[IO] <- otel.meterProvider.get("tickets-service")
              _ <- httpServer
                .resource(local, traceProvider)
                .use((server: Server) => IO.never)
            yield ()
        }

      program.as(
        ExitCode.Success
      )

  end run
