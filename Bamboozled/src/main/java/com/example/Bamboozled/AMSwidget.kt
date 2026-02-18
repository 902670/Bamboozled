package com.example.Bamboozled

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.*

class AMSwidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val printerState by PrinterDataManager.state.collectAsState()
            WidgetContent(printerState)
        }
    }

     suspend fun providePreview(context: Context, widgetCategory: Int) {
        val printerState = PrinterDataManager.state.value
        provideContent {
            WidgetContent(printerState)
        }
    }

    @Composable
    internal fun WidgetContent(state: PrinterState) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                state.amsFilaments.forEachIndexed { index, rawColor ->
                    val displayColor = try {
                        val hex = rawColor.removePrefix("#")
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
                            .cornerRadius(14.dp)
                    ) {}

                    if (index < state.amsFilaments.size - 1) {
                        Spacer(modifier = GlanceModifier.width(10.dp))
                    }
                }
            }
        }
    }
}

class BambuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AMSwidget()
}
