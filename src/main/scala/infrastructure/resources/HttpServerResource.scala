package infrastructure.resources

import org.http4s.implicits.*
import org.http4s.ember.server.*
import com.comcast.ip4s.*
import logstage.IzLogger
import main.Configs.*
import infrastructure.http.{RequestInfo, ServerRoutes}
import org.http4s.server.Server

import javax.net.ssl.SSLContext
import java.nio.file.Paths
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import fs2.io.net.tls.*
import fs2.*
import fs2.io.net.*
import fs2.io.net.tls.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.trace.Tracer
import telemetry.ServerMiddleware.ServerMiddlewareOps
import org.typelevel.otel4s.trace.Tracer.Implicits._

def loadContextFromClasspath[F[_]](keystorePassword: String, keyManagerPass: String)(implicit
    F: Sync[F]
): F[SSLContext] =
  F.delay {
    val ksStream = this.getClass.getResourceAsStream("/server.jks")
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, keystorePassword.toCharArray)
    ksStream.close()

    val kmf = KeyManagerFactory.getInstance(
      Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
        .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
    )

    kmf.init(ks, keyManagerPass.toCharArray)

    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers, null, null)

    context
  }

class SSLResource[IO[_]: Async: Network]{
  def resource: Resource[IO, TLSContext[IO]] = for {
          sslContext <- Resource.eval(
            loadContextFromClasspath("changeit", "changeit")
          )
          tlsContext = Network[IO].tlsContext.fromSSLContext(sslContext)
          
  } yield tlsContext
}

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
                        .withHttpApp(routes.orNotFound.traced)
                        .build
                } yield res
