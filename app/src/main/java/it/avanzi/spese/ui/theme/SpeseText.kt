package it.avanzi.spese.ui.theme

import android.os.Build
import androidx.annotation.FontRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.avanzi.spese.R

object SpeseText {
    val Space = variableFamily(R.font.space_grotesk, 500, 600, 700)
    val Inter = variableFamily(R.font.inter, 400, 500, 600)
    val Mono = variableFamily(R.font.jetbrains_mono, 400, 500)

    val ScreenTitle = space(24, FontWeight.SemiBold, -0.01f)
    val Total = space(52, FontWeight.SemiBold, -0.03f)
    val TotalSuffix = space(24, FontWeight.Medium)
    val Amount = space(60, FontWeight.SemiBold, -0.03f)
    val SheetTitle = space(20, FontWeight.SemiBold)
    val CardTitle = space(18, FontWeight.SemiBold)
    val RowAmount = space(16, FontWeight.Medium)
    val Key = space(24, FontWeight.Medium)
    val Body = inter(15, FontWeight.Normal)
    val BodyMedium = inter(15, FontWeight.Medium)
    val Meta = inter(12, FontWeight.Normal)
    val MetaMedium = inter(13, FontWeight.Medium)
    val Section = inter(12, FontWeight.Medium).copy(letterSpacing = 0.06.em)
    val Export = TextStyle(fontFamily = Mono, fontSize = 13.sp)

    private fun space(size: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
        fontFamily = Space, fontSize = size.sp, fontWeight = weight,
        letterSpacing = spacing.em, fontFeatureSettings = "tnum",
    )

    private fun inter(size: Int, weight: FontWeight) = TextStyle(
        fontFamily = Inter, fontSize = size.sp, fontWeight = weight,
    )

    private fun variableFamily(@FontRes id: Int, vararg weights: Int): FontFamily {
        if (Build.VERSION.SDK_INT < 26) return FontFamily(Font(id))
        return FontFamily(weights.map { weight ->
            Font(
                resId = id,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        })
    }
}
