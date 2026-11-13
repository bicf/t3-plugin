package com.bicf.t3plugin.ide

import java.nio.file.Path

/**
 * The IDE terminal. The plugin registers an implementation only when the IDE has the Terminal plugin
 * (`t3-terminal.xml`); without it, T3 keeps its own terminal.
 */
interface T3TerminalSupport {

    /** Opens a new terminal tab named [tabName] in [directory]. Call it on the EDT. */
    fun open(directory: Path, tabName: String): Boolean
}
