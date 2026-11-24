package com.finora.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Two-or-more inline items that wrap like CSS flex-wrap (column-gap [hGap], row-gap [vGap]). */
@Composable
fun Wrap(modifier: Modifier = Modifier, hGap: Int, vGap: Int, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val h = hGap.dp.roundToPx()
        val v = vGap.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var x = 0
        var y = 0
        var rowH = 0
        val pos = ArrayList<Pair<Int, Int>>()
        for (p in placeables) {
            if (x > 0 && x + p.width > constraints.maxWidth) { x = 0; y += rowH + v; rowH = 0 }
            pos.add(x to y)
            x += p.width + h
            rowH = maxOf(rowH, p.height)
        }
        layout(constraints.maxWidth, y + rowH) {
            placeables.forEachIndexed { i, p -> p.placeRelative(pos[i].first, pos[i].second) }
        }
    }
}

