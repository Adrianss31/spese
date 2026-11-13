package it.avanzi.spese.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object SpeseIcons {
    val categoryKeys = listOf(
        "cart", "home", "train", "fork", "ticket", "dots", "car", "heart", "gift",
        "book", "shirt", "plane", "bolt", "wifi", "phone", "dumbbell", "music", "paw",
    )

    private val paths = mapOf(
        "cart" to "M3 4h2l2.4 10.2a1.5 1.5 0 0 0 1.5 1.1h8.2a1.5 1.5 0 0 0 1.5-1.1L20.5 8H6.1 M8.5 20a1 1 0 1 0 2 0a1 1 0 1 0-2 0 M16 20a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "home" to "M4 11l8-7 8 7 M6 9.5V20h12V9.5 M10 20v-5h4v5",
        "train" to "M7 3h10a2 2 0 0 1 2 2v9a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a2 2 0 0 1 2-2z M5 10h14 M8 21l2-4 M16 21l-2-4 M8 13.5h.01 M16 13.5h.01",
        "fork" to "M7 3v8 M4.5 3v5a2.5 2.5 0 0 0 5 0V3 M7 11v10 M17 21V3c-2 1.5-3 4-3 7v3h3",
        "ticket" to "M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2.5a2.5 2.5 0 0 0 0 5V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2.5a2.5 2.5 0 0 0 0-5z M14 5v2 M14 11v2 M14 17v2",
        "dots" to "M4 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0 M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0 M17 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "car" to "M5 11l1.6-4.2A2 2 0 0 1 8.5 5.5h7a2 2 0 0 1 1.9 1.3L19 11 M4 11h16v6H4z M6 17v2 M18 17v2 M7.5 14h.01 M16.5 14h.01",
        "heart" to "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
        "gift" to "M4 9h16v4H4z M5 13h14v7H5z M12 9v11 M12 9c-1.5-3-5-3-5-1s3 1 5 1 M12 9c1.5-3 5-3 5-1s-3 1-5 1",
        "book" to "M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z M5 17a3 3 0 0 1 3-3h9",
        "shirt" to "M8 4L4 7l2 4 2-1v10h8V10l2 1 2-4-4-3c-1 1.5-2.4 2-4 2s-3-.5-4-2z",
        "plane" to "M10.5 13.5L4 11l1-2 7 1 4-5a1.6 1.6 0 0 1 2.4 2.1L15 12l1 7-2 1-2.5-6.5 M8 16l-3 3",
        "bolt" to "M13 3L5 13h6l-1 8 8-10h-6z",
        "wifi" to "M4 9.5a12 12 0 0 1 16 0 M7 12.8a7.5 7.5 0 0 1 10 0 M10 16a3 3 0 0 1 4 0 M12 19h.01",
        "phone" to "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M11 18h2",
        "dumbbell" to "M6 8v8 M3 10v4 M18 8v8 M21 10v4 M6 12h12",
        "tv" to "M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z M10 9.5v5l4-2.5z M8 21h8",
        "music" to "M9 18V6l10-2v12 M4 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0 M14 16a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
        "paw" to "M8 14c-2 2-2 5 1 5 1.2 0 2-.6 3-.6s1.8.6 3 .6c3 0 3-3 1-5-1.2-1.2-2.4-2-4-2s-2.8.8-4 2z M6 10a1.5 2 0 1 0 3 0a1.5 2 0 1 0-3 0 M15 10a1.5 2 0 1 0 3 0a1.5 2 0 1 0-3 0 M9.5 6a1.5 2 0 1 0 3 0a1.5 2 0 1 0-3 0 M11.5 6a1.5 2 0 1 0 3 0a1.5 2 0 1 0-3 0",
        "tabHome" to "M4 5h7v7H4z M13 5h7v4h-7z M13 11h7v8h-7z M4 14h7v5H4z",
        "tabStats" to "M12 3a9 9 0 1 0 9 9h-9z M15 3.5A9 9 0 0 1 20.5 9H15z",
        "tabRec" to "M17 2l3 3-3 3 M4 11V9a4 4 0 0 1 4-4h12 M7 22l-3-3 3-3 M20 13v2a4 4 0 0 1-4 4H4",
        "tabMore" to "M4 7h16 M4 12h16 M4 17h10",
        "wallet" to "M4 7a2 2 0 0 1 2-2h11v4 M4 7v10a2 2 0 0 0 2 2h13V9H6a2 2 0 0 1-2-2z M15 14a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "tag" to "M3 12V4h8l10 10-8 8z M7.5 7.5h.01",
        "share" to "M12 3v12 M7 8l5-5 5 5 M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6",
        "download" to "M12 4v11 M7 10l5 5 5-5 M5 20h14",
        "widget" to "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M16.5 13v7 M13 16.5h7",
        "chevronDown" to "M6 9l6 6 6-6",
        "chevronRight" to "M9 5l7 7-7 7",
        "back" to "M15 5l-7 7 7 7",
        "search" to "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z M20 20l-4.6-4.6",
        "close" to "M6 6l12 12 M18 6L6 18",
        "check" to "M5 12.5l4.5 4.5L19 7.5",
        "delete" to "M9 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6-7z M12 9.5l5 5 M17 9.5l-5 5",
        "calendar" to "M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z M4 10h16 M8 4v4 M16 4v4",
        "reload" to "M20 11a8 8 0 0 0-14.5-4.5L4 8 M4 4v4h4 M4 13a8 8 0 0 0 14.5 4.5L20 16 M20 20v-4h-4",
        "spinner" to "M12 3a9 9 0 1 0 9 9",
        "plus" to "M12 5v14 M5 12h14",
        "drag" to "M9 6h.01 M15 6h.01 M9 12h.01 M15 12h.01 M9 18h.01 M15 18h.01",
    )

    private val vectors = paths.mapValues { (key, path) ->
        val stroke = when (key) {
            "plus", "spinner" -> 2.4f
            "drag" -> 3f
            "back", "chevronDown", "chevronRight", "close" -> 2f
            else -> 1.8f
        }
        ImageVector.Builder(key, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }

    operator fun get(key: String): ImageVector = vectors[key] ?: vectors.getValue("dots")
}
