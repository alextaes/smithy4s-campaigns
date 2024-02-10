package infrastructure.resources

import org.http4s.implicits.*
import com.comcast.ip4s.*
import logstage.IzLogger
import main.Configs.*
import infrastructure.http.{RequestInfo, ServerRoutes}
import org.http4s.server.Server

import fs2.*
import fs2.io.net.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.otel4s.trace.Tracer
import telemetry.ServerMiddleware.ServerMiddlewareOps
import org.typelevel.otel4s.trace.Tracer.Implicits._

class HttpServerResource[F[_] : Sync : Async : Concurrent : LiftIO](
                           logger: IzLogger,
                           )(using
                           config: HttpServerConfig):

    def resource(local: IOLocal[Option[RequestInfo]]): Resource[IO, Server] = 
      
      ServerRoutes[IO](Some(logger)).getAll(local)
      .flatMap:
          routes =>
                for {
                      res <- EmberServerBuilder
                        .default[IO]
                        .withHttp2
                        .withHost(host"localhost")
                        .withPort(Port.fromInt(config.port).get)
                        .withHttpApp(routes.orNotFound.traced(logger, local))
                        .build
                } yield res
