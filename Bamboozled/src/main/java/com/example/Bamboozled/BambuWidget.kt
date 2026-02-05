package com.example.Bamboozled

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider

class BambuWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val state = PrinterDataManager.state.collectAsState().value
        val context = LocalContext.current
        
        // Outer container: Light background
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(72.dp)
                .background(GlanceTheme.colors.onPrimary)
                .cornerRadius(28.dp)
                .padding(8.dp)
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Progress Area - Perfectly circular to match the home page dial
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .size(56.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(28.dp)
            ) {

                
                // Centered Text container
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.isIdle) "Idle" else "${state.progress}%",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Right: Time Remaining in Secondary color
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(56.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remaining",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSecondaryContainer
                    )
                )
                Text(
                    text = if (state.isIdle) "--" else formatRemainingTime(state.remainingTimeMinutes),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSecondaryContainer
                    )
                )
            }
        }
    }

    private fun formatRemainingTime(mins: Int): String {
        val hours = mins / 60
        val minutes = mins % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

class BambuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BambuWidget()
}
