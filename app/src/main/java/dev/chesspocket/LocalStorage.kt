package dev.chesspocket

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStorage(private val context: Context) {
    private val gamesFile = "saved_games.json"

    fun load(): List<ChessGame> = try {
        val array = JSONArray(context.openFileInput(gamesFile).bufferedReader().use { it.readText() })
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ChessGame(obj.getString("source"), obj.getString("id"), obj.getString("white"),
                obj.getString("black"), obj.optString("whiteElo", "?"), obj.optString("blackElo", "?"),
                obj.optString("result", "*"), obj.optString("date"), obj.getString("pgn"),
                obj.optLong("endTime", 0))
        }
    } catch (_: Exception) { emptyList() }

    fun save(games: List<ChessGame>) {
        val array = JSONArray()
        // Cap saved games and stored PGN to keep disk/RAM usage predictable.
        for (game in games.take(100)) {
            array.put(JSONObject().apply {
                put("source", game.source); put("id", game.id)
                put("white", game.white); put("black", game.black)
                put("whiteElo", game.whiteElo); put("blackElo", game.blackElo)
                put("result", game.result); put("date", game.date)
                put("pgn", game.pgn); put("endTime", game.endTime)
            })
        }
        context.openFileOutput(gamesFile, Context.MODE_PRIVATE).bufferedWriter().use { it.write(array.toString()) }
    }
}
