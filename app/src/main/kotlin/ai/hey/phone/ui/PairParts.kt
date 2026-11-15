package ai.hey.phone.ui

import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

// Parts used by the W1/W2 screens (feature/pair). Sizes are measured from the delivery-v05 phone mockups (px / 2 = dp).

/** One line of the W1 "what this is" list: a 24 dp icon in a 42 dp column, a heading and one explanatory line. */
@Composable
fun InfoRow(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(42.dp).padding(top = HeyTokens.Space.s4), contentAlignment = Alignment.TopStart) {
            HeyIcon(icon, null)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HeyText(title, variant = HeyTextVariant.PairRowTitle)
            HeyText(body, variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        }
    }
}

/** Full-width page message led by the character, used for "QR이 만료됐거나 맞지 않아요" and "QR을 찍어 주세요". */
@Composable
fun PageMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    VStack(HeyGap.G16, modifier.fillMaxWidth().padding(top = HeyTokens.Space.s32)) {
        CharacterHero(size = 150.dp)
        HeyText(title, Modifier.padding(top = HeyTokens.Space.s16), variant = HeyTextVariant.PairTitle)
        if (body.isNotEmpty()) HeyText(body, variant = HeyTextVariant.PairLead, tone = HeyTone.Muted)
        if (action != null) Box(Modifier.padding(top = HeyTokens.Space.s8)) { action() }
    }
}

/** Single-line text input with a hairline rim (hand-typed code, name, chat line). [capitalize] is for codes only. */
@Composable
fun HeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    capitalize: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    multiline: Boolean = false,
    counter: String? = null,
) {
    val c = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HeyGap.G8.value)) {
        if (label.isNotEmpty()) HeyText(label, variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            textStyle = HeyTextVariant.Body.style(c.fgDefault),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HeyTokens.Size.touchWeb)
                .clip(shape)
                .background(c.bgRaised, shape)
                .border(HeyTokens.Border.hairline, if (error != null) c.danger else c.borderDefault, shape)
                .padding(horizontal = HeyTokens.Space.s16, vertical = HeyTokens.Space.s12),
        )
        if (counter != null) HeyText(counter, Modifier.fillMaxWidth(), variant = HeyTextVariant.PairCaption, tone = HeyTone.Muted, align = androidx.compose.ui.text.style.TextAlign.End)
        if (error != null) HeyText(error, variant = HeyTextVariant.Caption, tone = HeyTone.Danger)
    }
}

/** Fixed bottom area of a form page: hairline on top, 24 dp above the action. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = HeyTokens.Space.s24)) {
        HeyDivider()
        Column(
            Modifier.fillMaxWidth().padding(top = HeyTokens.Space.s24, bottom = HeyTokens.Space.s32),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HeyGap.G8.value + 5.dp),
            content = content,
        )
    }
}

/** Vertical rhythm and sizes of the W1/W2 phone mockups (delivery-v05 W1.png/W2.png, px / 2 = dp at the 375/390 dp canvas). */
object PairMetrics {
    val CharacterSize = 150.dp
    val CharacterTop = 33.dp
    val CaptionTop = 23.dp
    val BarPadH = 24.dp
    val ToggleTop = 37.dp
    val NoticeTop = 16.dp
    val TitleTop = 39.dp
    val LeadTop = 21.dp
    val InfoTop = 40.dp
    val InfoGap = 23.dp
    val ActionButtonHeight = 56.dp
    val DoneButtonTop = 38.dp
    val DoneButtonPadding = 21.dp
    val DoneNoteTop = 26.dp
}
