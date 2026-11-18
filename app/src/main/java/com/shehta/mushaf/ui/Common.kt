package com.shehta.mushaf.ui

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Spacer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Standard full-screen page: coloured header with back button, then content. */
@Composable
fun ScreenScaffold(
    title: String,
    c: MushafColors,
    onBack: (() -> Unit)?,          // null on the home screen (the root): no back arrow
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.bar, contentColor = c.onBar) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    else Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.onBar.copy(alpha = 0.8f))
                    }
                    actions()
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(), content = content)
        }
    }
}

/** Rounded card in the paper palette. */
@Composable
fun Card2(c: MushafColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = c.frameFill, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }


/**
 * A list's scroll position that outlives the screen: going into an item and coming back (inside a screen or
 * between screens) shows the list where the reader left it.
 */
object KeptLists { internal val states = HashMap<String, androidx.compose.foundation.lazy.LazyListState>() }

@Composable
fun keptListState(key: String): androidx.compose.foundation.lazy.LazyListState =
    androidx.compose.runtime.remember(key) { KeptLists.states.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() } }
