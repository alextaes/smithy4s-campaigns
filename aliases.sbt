import Utilities._

addCommandAlias("ll", "projects")
addCommandAlias("cd", "project")
addCommandAlias("c", "compile")

addCommandAlias(
  "styleCheck",
  "scalafmtSbtCheck; scalafmtCheckAll",
)

addCommandAlias(
  "styleFix",
  "scalafmtSbt; scalafmtAll",
)

addCommandAlias(
  "rl",
  "reload; update; reload",
)

addCommandAlias(
  "p",
  ";universal:packageZipTarball"
)

onLoadMessage +=
  s"""|
      |╭─────────────────────────────────╮
      |│     List of defined ${styled("aliases")}     │
      |├─────────────┬───────────────────┤
      |│ ${styled("l")} | ${styled("ll")} | ${
                                               styled(
                                                 "ls"
                                               )
                                             } │ projects          │
      |│ ${styled("cd")}          │ project           │
      |│ ${styled("c")}           │ compile           │
      |│ ${styled("styleCheck")}  │ fmt check         │
      |│ ${styled("styleFix")}    │ fmt               │
      |│ ${styled("rl")}          │ dependencyUpdates │
      |╰─────────────┴───────────────────╯""".stripMargin
