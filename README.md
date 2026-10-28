# ♞ ChessPocket (Android, DEBUG)

**Free local chess game review for Android**. This source project contains a Compose UI, a Stockfish 18 UCI integration, imported PGN games, and diagnostic logging.

## What is included

- Import up to **20 recent public Chess.com games by username** via monthly PubAPI archives, sequential requests.
- Import up to **20 recent public Lichess games by username** via Lichess game export API.
- Import **`.pgn` files** using Android's file picker, paste PGN, or paste a Lichess game URL.
- PGN SAN replay and legal move validation using Chesslib (standard chess only).
- Chess board (flip and jump to any ply), move list, original game ratings and results.
- **Offline Stockfish analysis** of each ply, evaluation from White's perspective, suggested UCI moves, centipawn loss, rough accuracy percentage, categories (best/excellent/good/inaccuracy/mistake/blunder), and an evaluation graph.
- Single-thread low-memory engine settings: `Hash=4 MB`, `Threads=1`, `MultiPV=1`; 120/220/450 ms per position.
- Debug badge with a copyable network/engine log (no credentials logged).
- Local saved games (max 100), no login, no backend, no analytics, no subscriptions.

**Important honesty:** Classification and accuracy are homegrown heuristics, **not** Chess.com formulas. “Brilliant” moves are marked only as experimental sacrifice *candidates*, and are not official Chess.com brilliant moves; no separate LLM or natural-language AI is included. One local Stockfish engine does the chess computation. Performance on real 1 GB RAM devices is **not yet measured**. Imported games require Internet; engine analysis does not.

## Build the actual DEBUG APK

This environment cannot build an APK because Android SDK and Gradle distributions are unavailable. The repository includes a **GitHub Actions workflow** that downloads authentic Stockfish 18 ARM binaries, verifies SHA-256, runs tests and creates per-architecture debug APKs automatically.

1. Create a GitHub repo and upload **all files** from the project ZIP, preserving `.github/workflows/android-debug.yml`.
2. Go to **Actions → Build Android debug APKs → Run workflow**.
3. After a successful run, download the artifact `ChessPocket-arm64-v8a-debug` (64-bit) or `ChessPocket-armeabi-v7a-debug` (32-bit).
4. Extract the ZIP and install `app-debug.apk`. The package is `dev.chesspocket.debug` and installs separately from other chess apps.

Alternatively, open this project in **Android Studio** with JDK 17 and Android SDK 35. The project needs **Gradle 8.9** and network access to JitPack, Google Maven, Maven Central and official Stockfish releases. Copy `libstockfish.so` into `app/src/main/jniLibs/<abi>/` to support local engine execution. The Actions workflow does this for you. **Build variant: debug**.

### Debugging

- Tap **DEBUG** in the top-right corner to view and copy logs.
- Filter `adb logcat -s ChessPocket:D AndroidRuntime:E` for diagnostic messages and crashes.
- If the engine is missing, analysis displays an error instead of pretending to work.
- To reduce load on older hardware, choose **120 ms** per position. The engine terminates after analysis or cancellation.
- A 100-ply game at 220ms per evaluation takes at least ~22 seconds plus startup and search overhead; the device may need more time.

### Known limitations

- Game import by **Chess.com game URL** is not implemented; by nickname and PGN are supported. Lichess direct game URL is supported.
- Standard chess only. Variants (Chess960, Atomic...) are rejected rather than analyzed with incorrect rules.
- Static runtime RAM targets are **not guaranteed**; Stockfish NNUE network and Android UI also consume RAM.
- Import buffers a monthly Chess.com JSON archive with an 8 MB cap and uses local memory. Very large archives report an error.
- Local PGNs and cached game list are stored in app-private files. Review results are not persisted after closing the screen.
- Very low end devices need testing for rendering speed, binary compatibility and native engine memory.

### Licensing

- App sample source: GPL-3.0-or-later (to be compatible with the integrated Stockfish, when distributed together).
- Stockfish 18 is GPL-3.0; official source code: https://github.com/official-stockfish/Stockfish/tree/sf_18
- Chesslib is Apache-2.0; source: https://github.com/bhlangonijr/chesslib

Redistributing an APK containing Stockfish requires full GPLv3 compliance, including making the complete corresponding source code available under the applicable conditions. This repository is a debug prototype, not a released commercial product.
