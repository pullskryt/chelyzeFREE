package dev.chesspocket

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

private const val MAX_RESPONSE_BYTES = 8_000_000

class ImportService(private val logger: (String) -> Unit) {
    private fun fetch(url: String, accept: String = "application/json"): String {
        val uri = URL(url)
        require(uri.protocol == "https" && uri.host in setOf("api.chess.com", "lichess.org")) { "Неподдерживаемый URL" }
        val connection = uri.openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 12_000
            readTimeout = 22_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "ChessPocket-Debug/0.1 (Android; local chess analyzer)")
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val status = connection.responseCode
            logger("GET ${uri.host}${uri.path} → $status")
            if (status !in 200..299) {
                throw IOException(when (status) {
                    404 -> "Не найден пользователь или партия (404)"
                    429 -> "Лимит API (429). Повторите позже; для Lichess — не ранее минуты"
                    in 500..599 -> "Сервис временно недоступен ($status)"
                    else -> "Ошибка API ($status)"
                })
            }
            val result = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                var total = 0
                while (true) {
                    val n = reader.read(buffer)
                    if (n == -1) break
                    total += n
                    if (total > MAX_RESPONSE_BYTES) throw IOException("Архив слишком большой для экономного режима")
                    result.append(buffer, 0, n)
                }
            }
            return result.toString()
        } finally { connection.disconnect() }
    }

    private fun safeUser(username: String): String {
        val user = username.trim()
        require(Regex("[A-Za-z0-9_-]{2,30}").matches(user)) { "Неверный ник пользователя" }
        return user
    }

    suspend fun chessCom(username: String, limit: Int = 20): List<ChessGame> = withContext(Dispatchers.IO) {
        val user = safeUser(username)
        val archiveArray = JSONObject(fetch("https://api.chess.com/pub/player/$user/games/archives"))
            .getJSONArray("archives")
        val games = mutableListOf<ChessGame>()
        for (index in archiveArray.length() - 1 downTo 0) {
            coroutineContext.ensureActive()
            val url = archiveArray.getString(index)
            // Never follow attacker-controlled domains from API response.
            require(URL(url).host == "api.chess.com") { "Некорректный архив" }
            val month = JSONObject(fetch(url)).getJSONArray("games")
            for (i in 0 until month.length()) {
                val item = month.getJSONObject(i)
                val pgn = item.optString("pgn")
                if (pgn.isBlank()) continue
                val endTime = item.optLong("end_time", 0)
                val id = item.optString("url", "$user-$index-$i")
                games.add(Pgn.makeGame(pgn, "Chess.com", id, endTime))
            }
            logger("Chess.com: найдено ${games.size} партий")
            if (games.size >= limit) break
        }
        games.distinctBy { it.id }.sortedByDescending { it.endTime }.take(limit)
    }

    suspend fun lichess(username: String, limit: Int = 20): List<ChessGame> = withContext(Dispatchers.IO) {
        val user = safeUser(username)
        val content = fetch("https://lichess.org/api/games/user/$user?max=$limit&sort=dateDesc&moves=true&tags=true&clocks=false&evals=false", "application/x-chess-pgn")
        Pgn.separate(content).take(limit).mapIndexed { index, pgn ->
            Pgn.makeGame(pgn, "Lichess", "$user-$index-${pgn.hashCode()}")
        }
    }

    suspend fun lichessLink(link: String): ChessGame = withContext(Dispatchers.IO) {
        val regex = Regex("(?:https://)?(?:www\\.)?lichess\\.org/([A-Za-z0-9]{8})(?:/.*)?")
        val id = regex.matchEntire(link.trim())?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Нужна ссылка https://lichess.org/XXXXXXXX")
        val pgn = fetch("https://lichess.org/game/export/$id?moves=true&tags=true", "application/x-chess-pgn")
        Pgn.makeGame(pgn, "Lichess", id)
    }
}
