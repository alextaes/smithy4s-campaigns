package infrastructure.http

object TypesConversion:

    given dateFromStdToSmithy: Conversion[java.util.Date, smithy4s.Timestamp] with

        def apply(self: java.util.Date): smithy4s.Timestamp =
            val seconds = self.getTime() / 1000
            val nanos = (self.getTime() % 1000) * 1000000
            smithy4s.Timestamp(seconds, nanos.toInt)
