<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# T3 Code Host Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- `T3ServerService`: connects to a running T3 server, or starts `t3 serve --no-browser` when no server answers.
- `T3PairingService`: creates a one-time pairing link with `t3 auth pairing create`.
