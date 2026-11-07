package io.github.eslamasabry.oc.ui.kit.platform

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
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
