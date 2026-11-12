package com.bicf.t3plugin.server

import java.nio.file.Files
import java.nio.file.Path

/** A fake `t3` command for tests: a POSIX shell script with a given body, in `<root>/home/bin/t3`. */
object FakeCli {

    fun create(root: Path, body: String): T3Cli {
        val home = T3Home(root.resolve("home"))
        val script = home.dir.resolve("bin").resolve("t3")
        Files.createDirectories(script.parent)
        Files.writeString(script, "#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return T3Cli(script, home, workingDirectory = root)
    }
}
