package io.github.eslamasabry.oc.ui.kit.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.kit.effects.GlassLightMath

/**
 * Visibility- and capability-gated Android gravity sensor for Apple Liquid Glass live speculars (B-610).
 *
 * Battery & idle CPU invariants:
 * - Registered ONLY when [active] is true (Glass style, API 35+ AppleLiquid tier, window visible, motion enabled).
 * - Uses hardware-batched `SENSOR_DELAY_UI` with 50 ms batch latency.
 * - EMA-smoothed and deadband-filtered (`>= 0.45°`) so a resting phone emits zero state writes (~0% visible idle CPU).
 * - Immediately unregistered when [active] becomes false (app hidden / backgrounded / Reduced Motion -> 0% hidden CPU).
 */
internal object AndroidGlassLightSensor {
    private const val MAX_REPORT_LATENCY_US: Int = 50_000
    private const val GRAVITY_AXES: Int = 3

    @Composable
    fun Observe(context: Context, active: Boolean, onAngleDeg: (Float) -> Unit) {
        val latest by rememberUpdatedState(onAngleDeg)
        DisposableEffect(context, active) {
            if (!active) {
                latest(GlassTuning.LIGHT_DEG)
                return@DisposableEffect onDispose {}
            }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor =
                manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                    ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (manager == null || sensor == null) {
                return@DisposableEffect onDispose {}
            }
            var smoothed = GlassTuning.LIGHT_DEG
            var emitted = GlassTuning.LIGHT_DEG
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val v = event.values
                        if (v.size < GRAVITY_AXES) return
                        val raw = GlassLightMath.tiltAngleDeg(v[0], v[1], v[2])
                        smoothed += (raw - smoothed) * GlassLightMath.TILT_EMA
                        val next = GlassLightMath.applyDeadband(emitted, smoothed)
                        if (next != emitted) {
                            emitted = next
                            latest(next)
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
            val ok =
                try {
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, MAX_REPORT_LATENCY_US)
                } catch (_: RuntimeException) {
                    false
                }
            onDispose {
                if (ok) {
                    try {
                        manager.unregisterListener(listener)
                    } catch (_: RuntimeException) {
                        // Already released.
                    }
                }
            }
        }
    }
}
