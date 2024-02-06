$version: "2"

namespace smithy4s.hello

structure Request {}

structure Response {}

@mixin
structure ErrorMixin {
  @required
  code: String
  @required
  title: String
  @required
  description: String
}

@httpError(400)
@error("client")
structure BadRequestError with [ErrorMixin]{}

@httpError(404)
@error("client")
structure NotFoundError with [ErrorMixin]{}

@httpError(409)
@error("client")
structure ConflictError with [ErrorMixin]{}

@httpError(500)
@error("server")
structure InternalServerError with [ErrorMixin]{}

@httpError(501)
@error("server")
structure NotImplementedError with [ErrorMixin]{}

@httpError(503)
@error("server")
structure ServiceUnavailableError with [ErrorMixin]{}
