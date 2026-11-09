package com.staninna.aufmachen.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.ui.theme.accent

/** Ring with one segment per tier. Within a segment the done part is solid and the open part faded. */
@Composable
fun ProgressRing(slices: List<TierSlice>, centerText: String, modifier: Modifier = Modifier, diameter: Dp = 140.dp) {
    val colors = slices.map { it.tier.accent() }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.total }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            if (total == 0) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val used = slices.count { it.total > 0 }
            val gap = if (used > 1) 3f else 0f
            val sweepable = 360f - gap * used
            var angle = -90f
            slices.forEachIndexed { i, s ->
                if (s.total == 0) return@forEachIndexed
                val sweep = sweepable * s.total / total
                val doneSweep = sweep * s.done / s.total
                val style = Stroke(stroke, cap = StrokeCap.Butt)
                if (s.open > 0) {
                    drawArc(colors[i].copy(alpha = 0.3f), angle + doneSweep, sweep - doneSweep, false, topLeft, arcSize, style = style)
                }
                if (s.done > 0) drawArc(colors[i], angle, doneSweep, false, topLeft, arcSize, style = style)
                angle += sweep + gap
            }
        }
        Text(centerText, style = MaterialTheme.typography.titleLarge)
    }
}
