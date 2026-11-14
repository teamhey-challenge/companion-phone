package ai.hey.phone.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun icon(name: String, vararg d: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    d.forEach { data ->
        builder.addPath(
            pathData = addPathNodes(data),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private const val CIRCLE = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"
private const val THERMO = "M7 14.5V5a2 2 0 1 1 4 0v9.5a4 4 0 1 1-4 0z"

/** One 24-grid, 2-unit round stroke family; tinted by HeyIcon. */
object HeyIcons {
    val ArrowLeft = icon("arrow-left", "M19 12H5", "M12 19l-7-7 7-7")
    val ArrowRight = icon("arrow-right", "M5 12h14", "M12 5l7 7-7 7")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")
    val ChevronRight = icon("chevron-right", "M9 6l6 6-6 6")
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z", "M20 20l-4-4")
    val Pin = icon("pin", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z", "M12 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z")
    val Flag = icon("flag", "M5 21V4", "M5 4h12l-2 4 2 4H5")
    val Door = icon("door", "M6 21V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17", "M4 21h16", "M14 12h1")
    val Mic = icon("mic", "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M6 11a6 6 0 0 0 12 0", "M12 17v4")
    val VolumeOff = icon("volume-off", "M4 9v6h4l5 4V5L8 9H4z", "M17 9l4 6", "M21 9l-4 6")
    val Music = icon("music", "M9 18V5l11-2v13", "M6 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z", "M17 13a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val ThermometerCool = icon("thermometer-cool", THERMO, "M17 5v8", "M13.5 7l7 4", "M20.5 7l-7 4")
    val ThermometerWarm = icon("thermometer-warm", THERMO, "M15 6h5", "M15 10h5", "M15 14h5")
    val Car = icon("car", "M5 17H4v-5l2-5h12l2 5v5h-1", "M4 12h16", "M5 17v2h3v-2", "M16 17v2h3v-2")
    val Clock = icon("clock", CIRCLE, "M12 7v5l3 2")
    val Route = icon("route", "M6 15a2 2 0 1 0 0 4 2 2 0 0 0 0-4z", "M18 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z", "M8 17h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7")
    val Toll = icon("toll", CIRCLE, "M14.5 9.5c-.5-1-1.5-1.5-2.5-1.5-1.5 0-2.5.8-2.5 2s1 1.6 2.5 2 2.5.8 2.5 2-1 2-2.5 2c-1 0-2-.5-2.5-1.5", "M12 6.5V8", "M12 16v1.5")
    val Speaker = icon("speaker", "M4 9v6h4l5 4V5L8 9H4z", "M16.5 9a4 4 0 0 1 0 6", "M19 6.5a8 8 0 0 1 0 11")
    val Message = icon("message", "M4 5h16v11H9l-5 4V5z")
    val Send = icon("send", "M21 3L10 14", "M21 3l-7 18-4-7-7-4 18-7z")
    val User = icon("user", "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M4 21a8 8 0 0 1 16 0")
    val Users = icon("users", "M9 5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z", "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 5.5a3.5 3.5 0 0 1 0 6.5", "M18 14a6.5 6.5 0 0 1 3.5 6")
    val Globe = icon("globe", CIRCLE, "M3 12h18", "M12 3c3 3 3 15 0 18", "M12 3c-3 3-3 15 0 18")
    val Menu = icon("menu", "M4 7h16", "M4 12h16", "M4 17h16")
    val Info = icon("info", CIRCLE, "M12 11v5", "M12 7.5v.5")
    val Warning = icon("warning", "M12 3L2.5 20h19L12 3z", "M12 10v4.5", "M12 17.5v.5")
    val Error = icon("error", CIRCLE, "M12 7.5v5.5", "M12 16v.5")
    val Refresh = icon("refresh", "M20 12a8 8 0 1 1-2.4-5.7", "M20 4v4h-4")
    val Qr = icon("qr", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h2v2h-2z", "M18 14h2", "M14 18h2v2", "M20 18v2")
    val Card = icon("card", "M3 6h18v12H3z", "M3 10h18", "M6 15h4")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Minus = icon("minus", "M5 12h14")
    val Link = icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Logout = icon("logout", "M9 4H5v16h4", "M16 8l4 4-4 4", "M20 12H9")
    val Trash = icon("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13", "M10 11v6", "M14 11v6")
    val Pause = icon("pause", "M8 5v14", "M16 5v14")
    val Book = icon("book", "M5 4h12a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2V4z", "M5 18a2 2 0 0 1 2-2h12", "M10 4v6l2-1.5 2 1.5V4")
    val Gear = icon(
        "gear", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z", "M12 3v3", "M12 18v3", "M3 12h3", "M18 12h3",
        "M5.6 5.6l2.1 2.1", "M16.3 16.3l2.1 2.1", "M5.6 18.4l2.1-2.1", "M16.3 7.7l2.1-2.1",
    )

    /** Every icon, in the order the gallery shows them. */
    val all: List<ImageVector> = listOf(
        ArrowLeft, ArrowRight, Check, Close, ChevronDown, ChevronRight, Search, Pin, Flag, Door, Mic, VolumeOff, Music,
        ThermometerCool, ThermometerWarm, Car, Clock, Route, Toll, Speaker, Message, Send, User, Users, Globe,
        Menu, Info, Warning, Error, Refresh, Qr, Card, Plus, Minus, Link, Logout, Trash, Pause, Book, Gear,
    )
}
