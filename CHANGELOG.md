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
- The T3 web UI uses the colors of the IDE theme and editor color scheme, also after a theme change.
- Settings | Tools | T3 Code: path of the `t3` command, URL of a remote T3 server, automatic start of `t3 serve`.
- Remote server: paste a pairing link from `t3 pair`.
- "Ask T3 About Selection…" in the editor context menu.
- Status bar widget with the state of the T3 server.
- A new pairing revokes the old sessions of the same IDE installation.
- In a narrow tool window, drag the right edge of the T3 sidebar to resize it. Double-click the edge to restore the width.
- Each T3 conversation opens in its own editor tab, with the thread title on the tab. The tool window shows the thread list and new conversations.
- File links in a conversation and "Open in…" open the file or directory in the IDE.
- "Open diff" and the files of a "changed files" card open the turn diff in the IDE diff viewer.
- "Toggle terminal drawer" and the "Terminal" panel open an IDE terminal in the workspace of the thread. The "Files" panel selects the workspace in the Project view.
- The branch selector, "Changes vs …" and "Commit" of a thread open the IDE Git tools.
- Settings: "Open conversations in editor tabs" and "Use the IDE tools in T3".

### Fixed
- Copy buttons in the T3 web UI put the text on the system clipboard.
- Paste in the T3 web UI uses the IDE clipboard (text and images), not an old clipboard value. Ctrl+C in the page also writes to the IDE clipboard.
- The text caret shows in the T3 composer after a click in the page.
- After the first pairing, links to other sites and copy buttons work without a page reload.
- The tool window opens threads started from the CLI, with ids such as `mcp:1c760312-…`.
