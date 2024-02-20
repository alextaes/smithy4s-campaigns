package infrastructure
package http

import smithy4s.hello._
import logstage.IzLogger
import scala.language.implicitConversions
import smithy4s.kinds.PolyFunction
import cats.data.EitherT

type SmithyModelErrors = NotFoundError | BadRequestError |
  ServiceUnavailableError
type Result[A] = EitherT[IO, SmithyModelErrors, A]

case class RequestInfo(
    userId: Option[String],
    traceId: Option[String]
)

class HttpServerImpl(
    logger: Option[IzLogger],
    requestInfo: IO[RequestInfo]
    // local: IOLocal[Option[RequestInfo]],
) extends HelloWorldService[Result] {

  // def requestInfo: IO[RequestInfo] =
  //     local.get.flatMap {
  //     case Some(value) => IO.pure(value)
  //     case None =>
  //       IO.raiseError(
  //         new IllegalAccessException(
  //           "Tried to access the value outside of the lifecycle of an http request"
  //         )
  //       )
  //   }

  // https://0.0.0.0:9006/person/pepe?town=granada
  def hello(name: String, town: Option[String]): Result[Greeting] =
    logger.foreach(_.info(s"Hello $name from $town!"))

    EitherT(IO { Right(Greeting(s"Hello $name from $town! (Result)")) })

    val response = requestInfo.flatMap { (reqInfo: RequestInfo) =>
      IO.println("REQUEST_INFO: " + reqInfo.toString)
        .as(Right(Greeting(s"Hello $name from $town! (IO)")))
    }

    EitherT(response)

}

object Converter:
  val toIO: PolyFunction[Result, IO] = new PolyFunction[Result, IO] {
    def apply[A](result: Result[A]): IO[A] = {

      result.foldF(
        error => IO.raiseError(error),
        value => IO { value }
      )

    }
  }
