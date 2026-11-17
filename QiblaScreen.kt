package com.shehta.mushaf.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shehta.mushaf.data.MuslimPrefs
import com.shehta.mushaf.data.PrayerCalc
import com.shehta.mushaf.data.ar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Qibla compass: the phone's rotation sensor + the great-circle bearing to the Kaaba. */
@Composable
fun QiblaScreen(mp: MuslimPrefs, c: MushafColors, onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var hasSensor by remember { mutableIntStateOf(1) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val rot = FloatArray(9); val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
                // smooth the needle (shortest way round)
                var d = deg - azimuth
                if (d > 180) d -= 360; if (d < -180) d += 360
                azimuth = (azimuth + d * 0.15f + 360) % 360
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracy = a }
        }
        if (sensor == null) hasSensor = 0 else sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    ScreenScaffold("اتجاه القبلة", c, onBack, subtitle = if (mp.hasLocation) mp.cityName else null) {
        if (!mp.hasLocation) { Centered { Text("حدّد موقعك من «مواقيت الصلاة ← الإعدادات» أولًا.", color = c.muted) }; return@ScreenScaffold }
        if (hasSensor == 0) { Centered { Text("هاتفك لا يحتوي على حساس البوصلة.", color = c.muted) }; return@ScreenScaffold }
        val qibla = PrayerCalc.qibla(mp.lat, mp.lng).toFloat()
        var diff = (qibla - azimuth + 360) % 360
        if (diff > 180) diff -= 360
        val aligned = abs(diff) < 4
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (aligned) "أنت متجه إلى القبلة ✓" else if (diff > 0) "استدر يمينًا ${abs(diff).roundToInt().ar()}°" else "استدر يسارًا ${abs(diff).roundToInt().ar()}°",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (aligned) c.gharib else c.ink,
            )
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)) {
                val r = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                // the dial turns with the phone so "N" always points north
                rotate(-azimuth, center) {
                    drawCircle(c.frameFill, r, center)
                    drawCircle(c.frame, r, center, style = Stroke(width = 6f))
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0)
                        val len = if (i % 18 == 0) 0.12f else if (i % 6 == 0) 0.07f else 0.035f
                        val p1 = Offset(center.x + (r * sin(a)).toFloat(), center.y - (r * cos(a)).toFloat())
                        val p2 = Offset(center.x + (r * (1 - len) * sin(a)).toFloat(), center.y - (r * (1 - len) * cos(a)).toFloat())
                        drawLine(if (i == 0) c.mistake else c.muted, p1, p2, strokeWidth = if (i % 18 == 0) 5f else 2f)
                    }
                    // qibla arrow
                    rotate(qibla, center) {
                        val tip = Offset(center.x, center.y - r * 0.82f)
                        val path = Path().apply {
                            moveTo(tip.x, tip.y); lineTo(center.x - r * 0.09f, center.y - r * 0.55f)
                            lineTo(center.x + r * 0.09f, center.y - r * 0.55f); close()
                        }
                        drawLine(if (aligned) c.gharib else c.marker, center, Offset(center.x, center.y - r * 0.6f), strokeWidth = 10f)
                        drawPath(path, if (aligned) c.gharib else c.marker)
                        // a small Kaaba
                        drawRect(c.ink, topLeft = Offset(tip.x - r * 0.07f, tip.y - r * 0.16f), size = androidx.compose.ui.geometry.Size(r * 0.14f, r * 0.14f))
                    }
                }
                drawCircle(c.ink, 10f, center)
                // fixed marker at the top of the phone
                drawLine(c.ink, Offset(center.x, center.y - r - 18f), Offset(center.x, center.y - r + 8f), strokeWidth = 6f)
            }
            Text("القبلة ${qibla.roundToInt().ar()}° من الشمال · المسافة إلى الكعبة ${PrayerCalc.distanceToKaaba(mp.lat, mp.lng).roundToInt().ar()} كم",
                fontSize = 14.sp, color = c.muted, textAlign = TextAlign.Center)
            if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                Text("دقة البوصلة منخفضة: حرّك الهاتف في الهواء على شكل رقم ٨ عدة مرات، وابتعد عن المعادن والمغناطيس.",
                    fontSize = 13.sp, color = c.mistake, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            Text("ضع الهاتف أفقيًا على كفّك.", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
