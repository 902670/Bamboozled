package com.example.Bamboozled

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*


class BambuWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    fun WidgetContent() {
        Box(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.onPrimary)
        ) {
            Row (GlanceModifier.fillMaxSize()) {
                CircularProgressIndicator(
                    GlanceModifier.fillMaxSize()


                )
                CircularProgressIndicator(
                    GlanceModifier.fillMaxSize()
                )

            }
        }
    }
}

class BambuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BambuWidget()
}
