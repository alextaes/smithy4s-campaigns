$version: "2.0"

namespace smithy4s.hello

use alloy#simpleRestJson
use smithy4s.meta#generateServiceProduct
use smithytranslate#contentType

use smithy4s.meta#unwrap

@simpleRestJson
@generateServiceProduct
service HelloWorldService {
  version: "1.0.0",
  operations: [
    Hello
  ]
}


@http(method: "POST", uri: "/hello/{name}", code: 200)
operation Hello{
  input: Message,
  output: Greeting
  errors: [
    BadRequestError
    NotFoundError
    ServiceUnavailableError
  ]
}

structure Message {
  @httpLabel
  @required
  name: String,

  @httpQuery("town")
  town: String
}

structure Greeting {
  @required
  message: String
}
