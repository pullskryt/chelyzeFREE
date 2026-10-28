package dev.chesspocket

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Engine executed ONLY on Dispatchers.IO, accessed serially by analyze(). */
class Stockfish(private val context: Context, private val logger: (String) -> Unit) : Closeable {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    fun available(): Boolean = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so").exists()

    private fun command(message: String) {
        writer?.apply { write(message); newLine(); flush() } ?: error("Движок не запущен")
    }

    private fun until(marker: String) {
        val stream = reader ?: error("Нет вывода движка")
        while (true) {
            val line = stream.readLine() ?: error("Stockfish завершился до $marker")
            if (line.startsWith(marker)) return
        }
    }

    private fun start() {
        if (process?.isAlive == true) return
        val path = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        require(path.exists()) { "Нет Stockfish для архитектуры устройства. Нужна debug-сборка из GitHub Actions." }
        logger("Запуск Stockfish (${path.length() / 1024 / 1024} МБ)")
        process = ProcessBuilder(path.absolutePath).redirectErrorStream(true).start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
        reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
        command("uci")
        until("uciok")
        command("setoption name Threads value 1")
        command("setoption name Hash value 4")
        command("setoption name MultiPV value 1")
        command("setoption name Ponder value false")
        command("isready")
        until("readyok")
        logger("Stockfish готов: 1 поток / Hash 4 MB")
    }

    private fun evaluate(fen: String, timeMs: Int): EngineEvaluation {
        command("position fen $fen")
        command("go movetime $timeMs")
        val whiteTurn = fen.split(' ').getOrNull(1) != "b"
        var best: String? = null
        var cp: Int? = null
        var mate: Int? = null
        var depth = 0
        val input = reader ?: error("Нет вывода движка")
        while (true) {
            val line = input.readLine() ?: error("Stockfish неожиданно завершился")
            if (line.startsWith("bestmove ")) {
                if (best == null) best = line.split(' ').getOrNull(1)
                break
            }
            if (!line.startsWith("info ") || !line.contains(" score ")) continue
            val d = Regex("\\bdepth (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val value = Regex("\\bscore (cp|mate) (-?\\d+)").find(line) ?: continue
            if (line.contains(" lowerbound") || line.contains(" upperbound")) continue
            depth = d
            val raw = value.groupValues[2].toInt()
            cp = if (value.groupValues[1] == "cp") if (whiteTurn) raw else -raw else null
            mate = if (value.groupValues[1] == "mate") if (whiteTurn) raw else -raw else null
            best = Regex("\\bpv ([a-h][1-8][a-h][1-8][qrbn]?)").find(line)?.groupValues?.get(1) ?: best
        }
        return EngineEvaluation(cp, mate, best, depth)
    }

    suspend fun analyze(
        game: PlayedGame,
        timeMs: Int,
        onProgress: (Int, Int, MoveReview) -> Unit
    ): GameReview = withContext(Dispatchers.IO) {
        require(timeMs in 60..5000)
        val result = mutableListOf<MoveReview>()
        try {
            start()
            var previous = evaluate(game.fens.first(), timeMs)
            for (i in game.uci.indices) {
                coroutineContext.ensureActive()
                val next = evaluate(game.fens[i + 1], timeMs)
                val (category, loss) = ReviewClassifier.classify(previous, next, i % 2 == 0, game.uci[i])
                val reviewed = MoveReview(i, game.san[i], category, loss, previous, next, previous.bestMove)
                result += reviewed
                if (i > 0 && ReviewClassifier.isSacrificeCandidate(
                        result[i - 1], game.fens[i - 1], game.fens[i + 1], (i - 1) % 2 == 0, next
                    )) {
                    result[i - 1] = result[i - 1].copy(category = "Блестящий 💎*")
                }
                onProgress(i + 1, game.uci.size, reviewed)
                previous = next
            }
            logger("Анализ окончен: ${result.size} полуходов")
            GameReview(result)
        } catch (cancelled: CancellationException) {
            logger("Анализ отменён")
            throw cancelled
        } finally {
            // Reclaim native memory as soon as analysis ends; idle RAM ≈ zero.
            close()
        }
    }

    override fun close() {
        try { command("quit") } catch (_: Exception) { }
        try { writer?.close() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        process?.destroyForcibly()
        process = null
        writer = null
        reader = null
    }
}
