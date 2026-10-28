package dev.chesspocket

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

private val Ink = Color(0xFF101719)
private val Slate = Color(0xFF192427)
private val Mint = Color(0xFF9AE1BD)
private val Soft = Color(0xFFB0C2C3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Slate, primary = Mint,
                onPrimary = Ink, onBackground = Color(0xFFEAF4F2),
                onSurface = Color(0xFFEAF4F2), secondary = Soft
            )) {
                val vm: ChessViewModel = viewModel()
                PocketScreen(vm)
            }
        }
    }
}

@Composable
private fun PocketScreen(vm: ChessViewModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.selected != null) {
                TextButton(onClick = vm::back) { Text("‹ Назад") }
            } else {
                Text("♞", fontSize = 27.sp, color = Mint)
                Spacer(Modifier.width(10.dp))
                Text("ChessPocket", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF30413B), shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { vm.toggleDebug() }) {
                Text("DEBUG", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (vm.showDebug) {
            DebugPanel(vm) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ChessPocket logs", vm.logs.joinToString("\n")))
            }
        }
        if (vm.selected == null) ImportScreen(vm) else ReviewScreen(vm)
    }
}

@Composable
private fun DebugPanel(vm: ChessViewModel, copy: () -> Unit) {
    Surface(Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(horizontal = 14.dp),
        shape = RoundedCornerShape(12.dp), color = Color(0xFF253238)) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Журнал отладки", color = Mint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = copy) { Text("Копировать", fontSize = 12.sp) }
            }
            LazyColumn(reverseLayout = true) {
                items(vm.logs.asReversed()) { line ->
                    Text(line, fontSize = 10.sp, color = Soft, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(vm: ChessViewModel) {
    val ctx = LocalContext.current
    var service by remember { mutableStateOf("Chess.com") }
    var nickname by remember { mutableStateOf("S1mclick") }
    var input by remember { mutableStateOf("") }
    var showPgn by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    require(bytes.size <= 2_000_000) { "Файл превышает 2 МБ" }
                    bytes.toString(Charsets.UTF_8)
                } ?: ""
                vm.importText(text)
            } catch (_: Exception) { input = "Не удалось открыть файл. Вставьте PGN вручную."; showPgn = true }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text("Шахматный анализ без ограничений", fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Spacer(Modifier.height(5.dp))
            Text("Партии импортируются онлайн. Анализ выполняется только на устройстве.", color = Soft, fontSize = 13.sp)
        }
        item {
            Surface(shape = RoundedCornerShape(19.dp), color = Slate) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("Импорт по никнейму", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Chess.com", "Lichess").forEach { provider ->
                            FilterChip(selected = service == provider, onClick = { service = provider }, label = { Text(provider) })
                        }
                    }
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Никнейм") },
                        placeholder = { Text("S1mclick") }
                    )
                    Button(onClick = { vm.loadFromUsername(service, nickname) }, enabled = !vm.busy && nickname.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Загрузить последние 20 партий")
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(19.dp), color = Slate) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PGN и ссылка Lichess", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Выбрать .pgn") }
                        OutlinedButton(onClick = { showPgn = !showPgn }) { Text("Вставить текст") }
                    }
                    if (showPgn) {
                        OutlinedTextField(value = input, onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                            label = { Text("PGN или ссылка lichess.org/XXXXXXXX") })
                        Button(enabled = !vm.busy && input.isNotBlank(), onClick = {
                            if (input.trim().contains("lichess.org/")) vm.importLichessLink(input.trim()) else vm.importText(input)
                        }, modifier = Modifier.fillMaxWidth()) { Text("Импортировать") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Мои партии", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Text("${vm.games.size}", color = Soft)
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(vm.status, color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (vm.games.isEmpty()) item {
            Surface(shape = RoundedCornerShape(14.dp), color = Slate) {
                Column(Modifier.fillMaxWidth().padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♟", fontSize = 35.sp, color = Mint)
                    Text("Импортируй партию, чтобы начать анализ", color = Soft, textAlign = TextAlign.Center)
                }
            }
        }
        items(vm.games, key = { it.id }) { game ->
            Surface(color = Slate, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { vm.open(game) }) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(game.source, color = Mint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(game.date.replace('.', '-'), fontSize = 11.sp, color = Soft)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(game.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text("${game.whiteElo} • ${game.blackElo}   |   ${game.result}", color = Soft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(vm: ChessViewModel) {
    val game = vm.selected ?: return
    val current = vm.ply
    val currentReview = vm.review?.moves?.getOrNull(current - 1)
    val review = vm.review
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(game.game.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${game.game.whiteElo} / ${game.game.blackElo} • ${game.game.result}", color = Soft, fontSize = 12.sp)
                }
                TextButton(onClick = vm::flipBoard) { Text("⇅ Доска") }
            }
        }
        item {
            ChessBoard(game.fens[current], vm.flipped)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.goto(0) }, modifier = Modifier.weight(1f)) { Text("|◀") }
                OutlinedButton(onClick = { vm.goto(current - 1) }, modifier = Modifier.weight(1f)) { Text("◀") }
                Text("$current / ${game.uci.size}", color = Soft, fontSize = 12.sp)
                OutlinedButton(onClick = { vm.goto(current + 1) }, modifier = Modifier.weight(1f)) { Text("▶") }
                OutlinedButton(onClick = { vm.goto(game.uci.size) }, modifier = Modifier.weight(1f)) { Text("▶|") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(15.dp), color = Slate) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Локальный анализ Stockfish", fontWeight = FontWeight.Bold)
                    if (review == null) {
                        Text("Оценки собственные, не алгоритм Chess.com. Блестящие жертвы помечаем экспериментальной эвристикой * — без гарантии правильности.",
                            color = Soft, fontSize = 12.sp)
                    } else {
                        Text("Точность*: ♔ ${review.whiteAccuracy}%  •  ♚ ${review.blackAccuracy}%",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Mint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(120, 220, 450).forEach { duration ->
                            FilterChip(selected = vm.timeMs == duration, enabled = !vm.busy,
                                onClick = { vm.timeMs = duration }, label = { Text("${duration}мс") })
                        }
                    }
                    if (vm.busy) {
                        LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = vm::cancelAnalysis, modifier = Modifier.fillMaxWidth()) { Text("Остановить") }
                    } else {
                        Button(onClick = vm::analyze, modifier = Modifier.fillMaxWidth()) { Text("${if (review == null) "Анализировать партию" else "Пересчитать"}") }
                    }
                    Text(vm.status, color = Soft, fontSize = 12.sp)
                    currentReview?.let { item ->
                        HorizontalDivider(color = Color(0xFF364344))
                        Text("${item.san}: ${item.category} • потеря ${item.loss} cp", color = Mint, fontWeight = FontWeight.Medium)
                        Text("Оценка ${item.after.label} • глубина ${item.after.depth}", color = Soft, fontSize = 12.sp)
                        if (item.suggestedUci != null) Text("Лучший вариант: ${item.suggestedUci}", color = Soft, fontSize = 12.sp)
                        Text(ReviewClassifier.describe(item), color = Soft, fontSize = 12.sp)
                    }
                }
            }
        }
        if (review != null) item { EvalGraph(review) }
        item {
            Text("Ходы партии", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Нажми на ход для перехода к позиции", color = Soft, fontSize = 12.sp)
        }
        items((game.san.size + 1) / 2) { number ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${number + 1}.", modifier = Modifier.width(36.dp), color = Soft)
                for (offset in 0..1) {
                    val idx = number * 2 + offset
                    if (idx < game.san.size) {
                        val item = review?.moves?.getOrNull(idx)
                        Surface(color = if (vm.ply == idx + 1) Color(0xFF315D50) else Slate,
                            shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f).padding(end = 6.dp)
                                .clickable { vm.goto(idx + 1) }) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 9.dp)) {
                                Text(game.san[idx], fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (item != null) Text(item.category, fontSize = 10.sp, maxLines = 1,
                                    color = when (item.category) {
                                        "Зевок", "Ошибка" -> Color(0xFFFF9987)
                                        "Неточность" -> Color(0xFFFFD78F)
                                        else -> Mint
                                    })
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Text("*Блестящий — кандидат по эвристике жертвы; точность — приблизительная эвристика на основе потери centipawns; не совпадает с Chess.com.", fontSize = 11.sp, color = Soft) }
    }
}

@Composable
private fun ChessBoard(fen: String, flipped: Boolean) {
    val rows = remember(fen) {
        val boardRows = fen.split(' ')[0].split('/')
        boardRows.map { row ->
            val output = mutableListOf<Char>()
            row.forEach { ch -> if (ch.isDigit()) repeat(ch.digitToInt()) { output += ' ' } else output += ch }
            output
        }
    }
    val pieces = mapOf(
        'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘", 'P' to "♙",
        'k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟"
    )
    Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(13.dp))) {
        repeat(8) { visualRow ->
            Row(Modifier.weight(1f)) {
                repeat(8) { visualCol ->
                    val r = if (flipped) 7 - visualRow else visualRow
                    val c = if (flipped) 7 - visualCol else visualCol
                    val ch = rows.getOrNull(r)?.getOrNull(c) ?: ' '
                    val isLight = (visualRow + visualCol) % 2 == 0
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (isLight) Color(0xFFE0DAC6) else Color(0xFF71938D)),
                        contentAlignment = Alignment.Center) {
                        Text(pieces[ch] ?: "", color = if (ch.isUpperCase()) Color(0xFFFAFAFA) else Color(0xFF172427),
                            fontSize = 32.sp, lineHeight = 34.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvalGraph(review: GameReview) {
    Surface(color = Slate, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("График оценки (белые + / чёрные −)", color = Soft, fontSize = 12.sp)
            Canvas(Modifier.fillMaxWidth().height(85.dp).padding(vertical = 10.dp)) {
                val midY = size.height / 2
                drawLine(Color(0xFF536260), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                val values = review.moves.map { it.after.cpForWhite().coerceIn(-600, 600) }
                values.forEachIndexed { i, cp ->
                    if (i > 0) {
                        val before = values[i - 1]
                        val x1 = (i - 1).toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                        val x2 = i.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                        val y1 = midY - (before / 600f) * midY
                        val y2 = midY - (cp / 600f) * midY
                        drawLine(Mint, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
