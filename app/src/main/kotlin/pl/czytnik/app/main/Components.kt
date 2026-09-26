package pl.czytnik.app.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.czytnik.app.ui.theme.Black
import pl.czytnik.app.ui.theme.White
import pl.czytnik.app.ui.theme.Yellow

val TouchTarget = 76.dp
val PrimaryTouchTarget = 96.dp
private val Shape = RoundedCornerShape(12.dp)

/** Duży przycisk (docs/TECH-SPEC.md, rozdz. 5.2): min. 76 dp, pogrubiony tekst, kontrast ≥ 7:1. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    minHeight: Dp = TouchTarget,
    stateDescription: String? = null,
) {
    val semantics = if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier
    val content: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight).then(semantics),
            shape = Shape,
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight).then(semantics),
            shape = Shape,
            border = BorderStroke(3.dp, White),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
        ) { content() }
    }
}

/** Przełącznik z tekstem stanu („TŁUMACZ: WŁĄCZONE”) – stan nie jest przekazywany tylko kolorem. */
@Composable
fun BigToggle(
    label: String,
    stateText: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = TouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        shape = Shape,
        color = if (checked) Yellow else Black,
        contentColor = if (checked) Black else White,
        border = BorderStroke(3.dp, if (checked) Yellow else White),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "$label: $stateText",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
