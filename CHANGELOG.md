<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# T3 Code Host Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- `T3ServerService`: connects to a running T3 server, or starts `t3 serve --no-browser` when no server answers.
- `T3PairingService`: creates a one-time pairing link with `t3 auth pairing create`.
- "T3 Code" tool window: shows the T3 Code web UI in JCEF and pairs the IDE only when no session exists. Without JCEF, it opens T3 Code in the system browser.
- The tool window registers the IDE project in T3 and opens its newest thread.
- Links to other sites and popups open in the system browser.
- The T3 web UI follows the light or dark theme of the IDE, also after a theme change.
- Settings | Tools | T3 Code: path of the `t3` command, URL of a remote T3 server, automatic start of `t3 serve`.
- Remote server: paste a pairing link from `t3 pair`.
- "Ask T3 About Selection…" in the editor context menu.
- Status bar widget with the state of the T3 server.
- A new pairing revokes the old sessions of the same IDE installation.
