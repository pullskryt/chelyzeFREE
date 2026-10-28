package dev.chesspocket

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChessViewModel(application: Application) : AndroidViewModel(application) {
    val games = mutableStateListOf<ChessGame>()
    val logs = mutableStateListOf<String>()
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Готово к импорту")
        private set
    var selected by mutableStateOf<PlayedGame?>(null)
        private set
    var ply by mutableStateOf(0)
        private set
    var review by mutableStateOf<GameReview?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var timeMs by mutableStateOf(220)
    var flipped by mutableStateOf(false)
    var showDebug by mutableStateOf(false)
    private var analyzeJob: Job? = null
    private var engine: Stockfish? = null
    private val storage = LocalStorage(application)
    private val importer = ImportService(::log)

    init {
        games.addAll(storage.load())
        log("Загружено сохранённых партий: ${games.size}")
    }

    fun log(message: String) {
        Log.d("ChessPocket", message)
        // Called by network IO, only post mutations to Main thread.
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (logs.size >= 120) logs.removeAt(0)
            logs.add("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $message")
        }
    }

    private fun updateGames(newGames: List<ChessGame>) {
        val merged = (newGames + games).distinctBy { it.id }.take(100)
        games.clear()
        games.addAll(merged)
        viewModelScope.launch(Dispatchers.IO) { storage.save(merged) }
        status = "Загружено ${newGames.size} партий"
        log(status)
    }

    fun loadFromUsername(source: String, user: String) {
        if (busy) return
        busy = true
        status = "Запрашиваем последние 20 партий…"
        viewModelScope.launch {
            try {
                val result = if (source == "Chess.com") importer.chessCom(user) else importer.lichess(user)
                updateGames(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Ошибка импорта: ${e.message ?: "неизвестно"}"
                log(status)
            } finally { busy = false }
        }
    }

    fun importText(text: String) {
        if (busy) return
        busy = true
        status = "Разбираем PGN…"
        viewModelScope.launch {
            try {
                val newGames = withContext(Dispatchers.Default) {
                    Pgn.separate(text).take(100).mapIndexed { index, pgn ->
                        val game = Pgn.makeGame(pgn, "PGN", "pgn-${pgn.hashCode()}-$index")
                        Pgn.parse(game) // validate before adding
                        game
                    }
                }
                require(newGames.isNotEmpty()) { "Нет партий" }
                updateGames(newGames)
            } catch (e: Exception) {
                status = "Некорректный PGN: ${e.message}"
                log(status)
            } finally { busy = false }
        }
    }

    fun importLichessLink(link: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val game = importer.lichessLink(link)
                Pgn.parse(game)
                updateGames(listOf(game))
            } catch (e: Exception) {
                status = "Ошибка ссылки: ${e.message}"
                log(status)
            } finally { busy = false }
        }
    }

    fun open(game: ChessGame) {
        try {
            cancelAnalysis()
            selected = Pgn.parse(game)
            ply = 0
            review = null
            progress = 0f
            status = "Открыто: ${game.title} (${selected?.uci?.size ?: 0} полуходов)"
            log(status)
        } catch (e: Exception) {
            status = "Не удалось открыть партию: ${e.message}"
            log(status)
        }
    }

    fun goto(index: Int) { ply = index.coerceIn(0, selected?.fens?.size?.minus(1) ?: 0) }
    fun back() { cancelAnalysis(); selected = null; review = null }
    fun flipBoard() { flipped = !flipped }
    fun toggleDebug() { showDebug = !showDebug }

    fun analyze() {
        val game = selected ?: return
        if (busy) return
        busy = true
        progress = 0f
        review = null
        status = "Запуск локального анализа…"
        val instance = Stockfish(getApplication(), ::log)
        engine = instance
        analyzeJob = viewModelScope.launch {
            try {
                val output = instance.analyze(game, timeMs) { done, total, move ->
                    viewModelScope.launch(Dispatchers.Main) {
                        progress = done.toFloat() / total.coerceAtLeast(1)
                        status = "Анализ $done/$total • ${move.category} ${move.san}"
                    }
                }
                review = output
                status = "Готово • точность ${output.whiteAccuracy}% / ${output.blackAccuracy}% (оценочная)"
                log(status)
            } catch (e: CancellationException) {
                status = "Анализ остановлен"
                log(status)
            } catch (e: Exception) {
                status = "Ошибка движка: ${e.message}"
                log(status)
            } finally {
                engine = null
                busy = false
            }
        }
    }

    fun cancelAnalysis() {
        analyzeJob?.cancel()
        // Abort wait/read and destroy process to release memory.
        engine?.close()
        engine = null
        analyzeJob = null
        busy = false
    }

    override fun onCleared() {
        cancelAnalysis()
        super.onCleared()
    }
}
