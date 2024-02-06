package infrastructure
package http

import smithy4s.hello._

import logstage.IzLogger

import scala.language.implicitConversions

import io.github.arainko.ducktape.*

import server.common.CommonHTTP

type SmithyModelErrors = NotFoundError | BadRequestError | ServiceUnavailableError
type SmithyModelErrors2 = NotFoundError | BadRequestError

import smithy4s.Transformation
import smithy4s.kinds.PolyFunction

import cats.data.EitherT

type Result[A] = EitherT[IO, SmithyModelErrors, A]

type ResultI[A] = Either[SmithyModelErrors, IO[A]]

type Result2[A] = EitherT[IO, SmithyModelErrors2, IO[A]]

case class RequestInfo(
                        userId: Option[String])

class HttpServerImpl2(
                       logger: Option[IzLogger],
                       requestInfo: IO[RequestInfo],
                     )
  extends HelloWorldService[Result]{

  // https://0.0.0.0:9006/person/pepe?town=granada
  def hello(name: String, town: Option[String]): Result[Greeting]=
    logger.foreach(_.info(s"Hello $name from $town!"))

    EitherT(IO{Right(Greeting(s"Hello $name from $town! (Result)"))})

    val response = requestInfo.flatMap { (reqInfo: RequestInfo) =>
      IO.println("REQUEST_INFO: " + reqInfo)
        .as(Right(Greeting(s"Hello $name from $town! (IO)")))
    }

    EitherT(response)

}

object Converter:
  val toIO: PolyFunction[Result, IO] = new PolyFunction[Result, IO]{
    def apply[A](result: Result[A]): IO[A] = {

      result.foldF(
        error => IO.raiseError(error),
        value => IO{value}
      )

    }
  }

class HttpServerImpl(
                      logger: Option[IzLogger],
                      requestInfo: IO[RequestInfo],
                    )
  extends HelloWorldService[IO], CommonHTTP(logger){

  def hello(name: String, town: Option[String]): IO[Greeting] =

    logger.foreach(_.info(s"Hello $name from $town! (IO)"))

    // val response = IO.pure{Greeting(s"Hello $name from $town! (IO)")}

    val response = requestInfo.flatMap { (reqInfo: RequestInfo) =>
      IO.println("REQUEST_INFO: " + reqInfo)
        .as(Greeting(s"Hello $name from $town! (IO)"))
    }

    response.handleErrorWith(
      errorHandler
    )
}
