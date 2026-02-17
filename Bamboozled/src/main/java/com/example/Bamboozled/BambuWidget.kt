package com.example.Bamboozled

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.core.graphics.toColorInt

class BambuWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val printerState by PrinterDataManager.state.collectAsState()
            WidgetContent(printerState)
        }
    }

    @Composable
    private fun WidgetContent(state: PrinterState) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            state.amsFilaments.forEach { rawColor ->
                val displayColor = try {
                    val hex = rawColor.removePrefix("#")
                    // Bambu sends RRGGBBAA. Android expects AARRGGBB.
                    // We take the first 6 chars (RRGGBB) and prepend FF (Opaque)
                    // to ensure the widget is never clear/transparent.
                    val cleanHex = if (hex.length >= 6) hex.substring(0, 6) else "00E676"
                    Color("#FF$cleanHex".toColorInt())
                } catch (e: Exception) {
                    Color(0xFF00E676.toInt())
                }

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(displayColor)
                ) {}
            }
        }
    }
}

class BambuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BambuWidget()
}
