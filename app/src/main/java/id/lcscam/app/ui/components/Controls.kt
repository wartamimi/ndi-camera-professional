package id.lcscam.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.lcscam.app.ui.theme.Cine

/** Small labelled value, e.g. "ISO" / "800". */
@Composable
fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Cine.Text) {
    Column(modifier.padding(vertical = 3.dp)) {
        Text(label, style = Cine.label)
        Text(value, style = Cine.value.copy(color = valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selectable chip used for presets and options. Disabled chips are visibly greyed and inert. */
@Composable
fun CineChip(text: String, selected: Boolean, enabled: Boolean = true, locked: Boolean = false, onClick: () -> Unit) {
    val border = when {
        !enabled -> Cine.Line
        selected -> Cine.Accent
        else -> Cine.Line
    }
    Box(
        Modifier
            .padding(3.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(3.dp))
            .background(if (selected && enabled) Cine.Accent.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(if (locked) "🔒 $text" else text, fontFamily = Cine.Mono, fontSize = 12.sp,
            color = when { locked -> Cine.Dim; selected && enabled -> Cine.Accent; else -> Cine.Text })
    }
}

/** Bottom bar button: label + current value. */
@Composable
fun ControlButton(label: String, value: String, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, locked: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.35f)
            .background(if (active) Cine.PanelRaised else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (locked) "🔒$label" else label, style = Cine.label.copy(color = if (active) Cine.Accent else Cine.Dim))
        Text(value, style = Cine.value.copy(fontSize = 12.sp, color = if (locked) Cine.Dim else Cine.Text), maxLines = 1)
        Box(Modifier.padding(top = 3.dp).width(18.dp).height(2.dp).background(if (active) Cine.Accent else Color.Transparent))
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        Text(" $text", fontFamily = Cine.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), style = Cine.label.copy(fontSize = 11.sp, color = Cine.Accent),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp))
}
