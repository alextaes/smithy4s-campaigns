package infrastructure.http.server.common

import smithy4s.hello._

import logstage.IzLogger

type SmithyModelErrors = NotFoundError | BadRequestError | ServiceUnavailableError

// not used
def errorHandler(ex: java.lang.Throwable) = {
    IO.raiseError(NotFoundError("", "", ex.getMessage()))
}

// not used
trait CommonHTTP(logger: Option[IzLogger]):

    def errorHandler
      (ex: java.lang.Throwable) = {
        logger.foreach(_.error(s"Error =================>>>>> $ex"))

        ex match {
          case e: SmithyModelErrors => IO.raiseError(e)
          case _ => IO.raiseError(ServiceUnavailableError("503", "", ex.getMessage()))
        }
      }
