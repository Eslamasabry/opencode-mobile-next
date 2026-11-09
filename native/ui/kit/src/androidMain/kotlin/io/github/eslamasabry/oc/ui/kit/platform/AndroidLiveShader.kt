package io.github.eslamasabry.oc.ui.kit.platform

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.live.androidRunsLiveShaders

/**
 * A live look's AGSL shader on Android 13+: one [RuntimeShader] for the look's lifetime, its uniforms updated in place
 * each frame (no heap allocation), drawn through one [ShaderBrush].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidLiveShader(source: String) : LiveShaderProgram() {
    private val shader = RuntimeShader(source)
    override val brush: Brush = ShaderBrush(shader)

    override fun float(name: String, v: Float) = shader.setFloatUniform(name, v)

    override fun float2(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)

    override fun float3(name: String, x: Float, y: Float, z: Float) = shader.setFloatUniform(name, x, y, z)

    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = shader.setFloatUniform(name, x, y, z, w)

    companion object {
        /**
         * The full-resolution finish of a reduced look on [ground] (LiveShaderLib.FINISH as a runtime-shader render
         * effect over the full-size layer the reduced pass is filtered up into); null below API 33 or if it does not
         * compile. Built once per theme: it has no time uniform.
         */
        fun finish(ground: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.RenderEffect? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return try {
                val shader = RuntimeShader(io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib.FINISH)
                shader.setFloatUniform("uGround", ground.red, ground.green, ground.blue, 1f)
                android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Null below API 33 or when the text does not compile (the page draws the still fallback). */
        fun compile(source: String): LiveShaderProgram? {
            // Spelled out for lint's API check; the same line as androidRunsLiveShaders (LIVE_SHADER_MIN_SDK).
            val sdk = Build.VERSION.SDK_INT
            if (sdk < Build.VERSION_CODES.TIRAMISU || !androidRunsLiveShaders(sdk)) return null
            return try {
                AndroidLiveShader(source)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
