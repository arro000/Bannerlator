package com.winlator.star.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.star.R
import com.winlator.star.ui.XServerDialogState
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DebugDialogContent(state: XServerDialogState) {
    val logLines  by state.logLines.collectAsState()
    val logPaused by state.logPaused.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(-1) }
    val searchMatches = remember(logLines, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            emptyList()
        }
        else {
            logLines.mapIndexedNotNull { index, line ->
                if (line.lowercase(Locale.US).contains(query.lowercase(Locale.US))) index else null
            }
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty() && !logPaused) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    LaunchedEffect(searchQuery, searchMatches.size) {
        currentMatchIndex = if (searchMatches.isEmpty()) -1 else currentMatchIndex.coerceIn(0, searchMatches.lastIndex)
    }

    Dialog(
        onDismissRequest = { state.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.logs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_logs)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        enabled = searchMatches.isNotEmpty(),
                        onClick = {
                            currentMatchIndex = if (currentMatchIndex <= 0) searchMatches.lastIndex else currentMatchIndex - 1
                            scope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                        }
                    ) {
                        Text(stringResource(R.string.search_previous))
                    }
                    TextButton(
                        enabled = searchMatches.isNotEmpty(),
                        onClick = {
                            currentMatchIndex = if (currentMatchIndex >= searchMatches.lastIndex) 0 else currentMatchIndex + 1
                            scope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                        }
                    ) {
                        Text(stringResource(R.string.search_next))
                    }
                    if (searchMatches.isNotEmpty()) {
                        Text(
                            text = "${currentMatchIndex + 1}/${searchMatches.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(logLines) { index, line ->
                        DebugLogRow(
                            line = line,
                            index = index,
                            selected = index == selectedIndex,
                            searchQuery = searchQuery,
                            searchMatch = searchMatches.contains(index),
                            currentSearchMatch = currentMatchIndex >= 0 && searchMatches.getOrNull(currentMatchIndex) == index,
                            onClick = {
                                selectedIndex = if (selectedIndex == index) -1 else index
                                state.setLogPaused(true)
                            }
                        )
                    }
                    if (logLines.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_log_output),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = {
                            selectedIndex = -1
                            currentMatchIndex = -1
                            state.clearLog()
                        }) {
                            Text(stringResource(R.string.clear_logs))
                        }
                        TextButton(onClick = { copyAllLogs(context, logLines) }) {
                            Text(stringResource(R.string.copy_all_logs))
                        }
                        TextButton(onClick = { copySelectedLog(context, logLines.getOrNull(selectedIndex)) }) {
                            Text(stringResource(R.string.copy_selected_log))
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { state.setLogPaused(!logPaused) }) {
                            Text(stringResource(if (logPaused) R.string.resume_logs else R.string.pause_logs))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { state.dismiss() }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLogRow(
    line: String,
    index: Int,
    selected: Boolean,
    searchQuery: String,
    searchMatch: Boolean,
    currentSearchMatch: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val lineColor = debugLogChannelColor(line)
    val rowColor = when {
        selected -> colorScheme.primaryContainer
        currentSearchMatch -> Color(0xfffff59d)
        searchMatch -> Color(0xfffff9c4)
        index % 2 != 0 -> colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> colorScheme.surface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .background(lineColor)
        )
        Text(
            text = highlightedLogLine(line, searchQuery, currentSearchMatch),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = lineColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

private fun copyAllLogs(context: Context, logLines: List<String>) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(context.getString(R.string.logs), logLines.joinToString("\n"))
    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
}

private fun copySelectedLog(context: Context, selectedLine: String?) {
    if (selectedLine.isNullOrEmpty()) {
        Toast.makeText(context, R.string.no_log_selected, Toast.LENGTH_SHORT).show()
        return
    }

    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(context.getString(R.string.copy_selected_log), selectedLine)
    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, R.string.selected_log_copied, Toast.LENGTH_SHORT).show()
}

private fun highlightedLogLine(line: String, query: String, currentSearchMatch: Boolean) = buildAnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        append(line)
        return@buildAnnotatedString
    }

    val lowerLine = line.lowercase(Locale.US)
    val lowerQuery = trimmedQuery.lowercase(Locale.US)
    var startIndex = 0
    var matchIndex = lowerLine.indexOf(lowerQuery, startIndex)
    val highlightColor = if (currentSearchMatch) Color(0xffffeb3b) else Color(0xfffff176)

    while (matchIndex >= 0) {
        append(line.substring(startIndex, matchIndex))
        withStyle(SpanStyle(background = highlightColor, color = Color.Black)) {
            append(line.substring(matchIndex, matchIndex + trimmedQuery.length))
        }
        startIndex = matchIndex + trimmedQuery.length
        matchIndex = lowerLine.indexOf(lowerQuery, startIndex)
    }

    append(line.substring(startIndex))
}

private fun debugLogChannelColor(line: String): Color {
    val lowerLine = line.lowercase(Locale.US)
    return when {
        lowerLine.contains("err:") || lowerLine.contains("error") -> Color(0xffb71c1c)
        lowerLine.contains("warn:") || lowerLine.contains("warning") -> Color(0xffef6c00)
        lowerLine.contains("fixme:") -> Color(0xff6a1b9a)
        lowerLine.contains("trace:") -> Color(0xff1565c0)
        lowerLine.contains("box64") || lowerLine.contains("box86") -> Color(0xff2e7d32)
        lowerLine.contains("x11") || lowerLine.contains("xserver") -> Color(0xff00838f)
        lowerLine.contains("wine") -> Color(0xff283593)
        else -> Color(0xff212121)
    }
}
