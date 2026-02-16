package com.example.Bamboozled

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class BambuWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val printerState by PrinterDataManager.state.collectAsState()
            GlanceTheme {



                    //Widget Content

                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.secondaryContainer)
                                .cornerRadius(20.dp)
                                .fillMaxSize()
                                .defaultWeight()

                        ) {


                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.tertiaryContainer)
                                .cornerRadius(20.dp)
                                .fillMaxSize()
                                .defaultWeight()
                        ) {

                        }
                    }
                }
            }
        }
    }


class BambuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BambuWidget()
}
