package main

import Configs.*

import doobie.util.ExecutionContexts

import distage.Injector
import distage.ModuleDef
import distage.config.{ AppConfig, ConfigModuleDef }
import com.typesafe.config.ConfigFactory
import org.http4s.server.Server

import logstage.{ ConsoleSink, IzLogger, Trace }
import izumi.logstage.api.routing.StaticLogRouter
import infrastructure.resources.*


object DI:

    val configModule =
      new ConfigModuleDef:
          makeConfig[HttpServerConfig]("httpServer")
          make[AppConfig].from(AppConfig.provided(
            ConfigFactory
              .defaultApplication().getConfig("app").resolve()
          ))

    val mainModule =
      new ModuleDef:

          make[IzLogger].from:
              () =>
                  val textSink = ConsoleSink.text(colored = true)
                  val sinks = List(textSink)
                  val res = IzLogger(Trace, sinks)
                  StaticLogRouter.instance.setup(res.router)
                  res

          
          make[HttpServerResource].from:
              (
                config: HttpServerConfig,
                logger: IzLogger,
                ) =>
                  given HttpServerConfig = config
                  HttpServerResource(logger)

object App extends IOApp:

    def run(args: List[String]): IO[ExitCode] =
        import DI.*

        val ioModule =
          new ModuleDef:
            make[fs2.io.net.Network[IO]].from:
              summon[fs2.io.net.Network[IO]]
            
        Injector[IO]().produceRun(ioModule ++ mainModule ++ configModule):
            (
              httpServer: HttpServerResource) =>

                val program: IO[Unit] =IOLocal(Option.empty[infrastructure.http.RequestInfo]).flatMap { local =>
                  for
                    _ <- httpServer.resource(local).use(
                           (server: Server) =>
                             IO.never
                         )
                  yield ()
                }

                program.as(
                  ExitCode.Success
                )

    end run
