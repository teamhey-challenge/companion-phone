package ai.hey.phone

import ai.hey.phone.ui.HeyTextProbe
import ai.hey.phone.ui.HeyTextRecord
import ai.hey.phone.ui.LocalHeyTextProbe
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import java.io.File

/**
 * Design-conformance dump (tests only). A Paparazzi test wraps the screen it renders in [capture]; every HeyText/InkText
 * reports its laid-out bounds (dp) and TextStyle to the probe, and [write] puts them in `build/reports/design-diff/<name>.json`.
 * `ops/design-diff/run.ts` in pleos-challenge compares that file with the studio design. Same file format as the car's DesignDump.
 */
class DesignDump(private val name: String, private val widthDp: Int, private val heightDp: Int, private val dark: Boolean) {
    private val seen = LinkedHashMap<String, HeyTextRecord>()

    @Composable
    fun capture(content: @Composable () -> Unit) {
        val probe = HeyTextProbe { r -> seen["${r.text}@${r.x.toInt()},${r.y.toInt()}"] = r }
        CompositionLocalProvider(LocalHeyTextProbe provides probe) { content() }
    }

    fun write(dir: File = File("build/reports/design-diff")) {
        dir.mkdirs()
        val body = seen.values.joinToString(",\n") { r ->
            """    {"text":${q(r.text)},"x":${r.x},"y":${r.y},"w":${r.w},"baselineY":${r.baselineY},"lines":${r.lines},"fontSize":${r.fontSizeSp},""" +
                """"fontWeight":${r.fontWeight},"letterSpacing":${r.letterSpacing},"letterSpacingUnit":"${r.letterSpacingUnit}",""" +
                """"lineHeight":${r.lineHeightSp},"color":"${r.colorHex}","maxLines":${r.maxLines}}"""
        }
        File(dir, "$name.json").writeText(
            "{\"name\":${q(name)},\"width\":$widthDp,\"height\":$heightDp,\"dark\":$dark,\"texts\":[\n$body\n]}\n",
        )
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
