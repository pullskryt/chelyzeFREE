# Third-party software

- **Stockfish 18** — copyright Stockfish contributors, GPL-3.0. The CI downloads the official GPL-licensed executable from https://github.com/official-stockfish/Stockfish/releases/tag/sf_18 and validates its SHA-256 hash. Matching source: https://github.com/official-stockfish/Stockfish/tree/sf_18 (release tag).
- **Chesslib 1.3.4** — copyright respective authors, Apache-2.0. Source: https://github.com/bhlangonijr/chesslib. The Gradle dependency is retrieved via JitPack.
- **AndroidX / Jetpack Compose / Kotlin libraries** — respective open-source licenses. See Gradle dependencies.

The APK should be distributed together with the complete corresponding sources and all applicable third-party license notices. The repository's workflow builds DEBUG APKs only.
