<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# T3 Code Host Changelog

## [Unreleased]

### Added

- "T3 Details" tool window: the "Thread details" card of T3 (environment, branch, pull requests, changes, servers, automations, lineage, usage) of the selected conversation tab, at the full size of the tool window. "Toggle thread details panel" in a conversation tab opens and closes it. The conversation tab no longer shows the card. When an item of the card opens the right panel of T3, the panel opens in the "T3 Workspace" tool window.
- A conversation scrolled up has a "Scroll to end" button above the composer, also when T3 shows no button of its own (for example, while the find bar is open). The page never shows 2 buttons.

## [0.3.2] - 2026-11-27

### Added

- "New T3 Thread in This Worktree" (Tools menu and "T3 Code" tool window, only in the IDE window of a Git worktree): starts a T3 thread on the branch and in the folder of the worktree, with the T3 action "New thread on <branch>" of the thread of the worktree.
- The IDE dialog "Create worktree" proposes the T3 worktrees folder of the project (`<Worktree location>/<project folder>`), when the project has no worktree folder of its own. The worktrees of the IDE and of T3 are then in one folder.

### Fixed

- A Git worktree that the IDE creates is not a separate T3 project: as a T3 worktree, its window adds no T3 project and opens the thread of the worktree.
- The plugin uses the T3 setting "Worktree location" (`worktreesDirectory`), not always `~/.t3/worktrees`.
- The plugin finds the IDE window of a worktree also through a symlink, and opens no second window for the same folder.
- The window of a T3 worktree opens without a "Trust project" question when the window of its project is trusted.

## [0.3.1] - 2026-11-21

### Added

- Each code block in a conversation (a block between ```` ``` ```` fences, also a Mermaid diagram) has an "Edit in composer" button, after "Copy code". The button puts the text of the block in the composer, after the draft; it sends nothing.

## [0.3.0] - 2026-11-19

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
- The "T3 Code" tool window shows only the T3 sidebar, and opens on the left. New threads, drafts and the T3 settings, pull requests and usage pages also open in editor tabs.
- Editor tabs show the conversation without the T3 sidebars. "Toggle right panel" opens the right panel of T3 (browser, device, pull requests, side chat) in the new "T3 Workspace" tool window.
- The "Diff" panel surface opens the IDE diff of the working tree. The "Terminal", "Files" and "Diff" items of the empty right panel open the IDE tools.
- The action row under each message of the user (time, "Copy message", "Copy prompt link") has 2 more buttons: "Disable line wrap" / "Wrap lines" and "Edit in composer". The row shows on mouse hover or focus. "Edit in composer" puts the original text of the message in the composer, after the draft; it sends nothing.
- The T3 sidebar of the tool window marks the thread of the last selected T3 editor tab.
- [`docs/ui-map.svg`](https://github.com/bicf/t3-plugin/blob/main/docs/ui-map.svg): a map of the IDE window with the names of the T3 and plugin objects.
- The menu of a T3 conversation tab has the items of the "Thread actions" menu of T3 (rename, pin, settle, snooze, archive, delete, copy, …), with their state and submenus. T3 runs the action, with its own confirmations.
- One IDE window per T3 project, with a local server. A new T3 project ("Add existing project", "New project") opens in a new IDE window. A thread or a new thread of another project, selected in the T3 sidebar, opens in the IDE window of that project; the IDE opens the window when it is not open.
- The T3 worktree of a new thread opens in a new IDE window, with the thread in an editor tab. The tab of the same thread closes in the other IDE windows. The window of a T3 worktree adds no T3 project, and "Ask T3 About Selection…" sends the selection to the thread of the worktree.
- Settings: "Open T3 projects in IDE windows" and "Open the worktree of a new thread in an IDE window", on by default.

### Fixed

- Copy buttons in the T3 web UI put the text on the system clipboard.
- Paste in the T3 web UI uses the IDE clipboard (text and images), not an old clipboard value. Ctrl+C in the page also writes to the IDE clipboard.
- The text caret shows in the T3 composer after a click in the page.
- Linux: a selection in the T3 page goes to the primary selection, and a middle-click paste uses the primary selection of the desktop, not an old X11 value.
- After the first pairing, links to other sites and copy buttons work without a page reload.
- The tool window opens threads started from the CLI, with ids such as `mcp:1c760312-…`.
- A click on the project in the breadcrumb of a conversation opens one draft tab, not several.
- A tab that T3 redirects to another page without a click shows that page, and does not reload in a loop.
- A thread keeps its unread state ("Done", "Failed") when its run completes while its editor tab is in the background. The tab marks the thread as read when it shows on screen. A hidden tab holds the last "thread visited" command of each thread and sends it when the tab shows.
- The message box of the user has no plugin buttons: they never cover the text.
- The hover styles of T3 work in the IDE browser. Before, the browser reported no mouse, so the action row under a message of the user showed only with the focus in the message, and rows and buttons had no hover color. The `matchMedia` of the page gives the same answer, so T3 reports a hover device to its tool views.

[Unreleased]: https://github.com/bicf/t3-plugin/compare/0.3.2...HEAD
[0.3.2]: https://github.com/bicf/t3-plugin/compare/0.3.1...0.3.2
[0.3.1]: https://github.com/bicf/t3-plugin/compare/0.3.0...0.3.1
[0.3.0]: https://github.com/bicf/t3-plugin/commits/0.3.0
