package io.github.eslamasabry.opencode_mobile

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmPolicyTest {
    @Test fun gpuRequestFallsBackToCpuButCpuRequestNeverTriesGpu() {
        assertEquals(listOf("gpu", "cpu"), LocalLlmPolicy.backendOrder("gpu"))
        assertEquals(listOf("gpu", "cpu"), LocalLlmPolicy.backendOrder(null))
        assertEquals(listOf("cpu"), LocalLlmPolicy.backendOrder("cpu"))
    }

    @Test fun outputLengthIsClamped() {
        assertEquals(LocalLlmPolicy.DEFAULT_MAX_TOKENS, LocalLlmPolicy.clampMaxTokens(null))
        assertEquals(16, LocalLlmPolicy.clampMaxTokens(-5))
        assertEquals(LocalLlmPolicy.MAX_TOKENS_LIMIT, LocalLlmPolicy.clampMaxTokens(1_000_000))
    }

    @Test fun backgroundingAloneDoesNotStopARunningSummary() {
        assertFalse(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, generating = true))
        assertFalse(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, generating = true))
    }

    @Test fun idleModelIsReleasedWhenTheAppIsCached() {
        assertTrue(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, generating = false))
        assertFalse(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, generating = false))
    }

    @Test fun realPressureReleasesEvenWhileGenerating() {
        assertTrue(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, generating = true))
        assertTrue(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, generating = true))
        assertFalse(LocalLlmPolicy.unloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, generating = true))
    }

    @Test fun onlyPrivateStoragePathsAreOpened() {
        val roots = listOf("/data/user/0/app/files")
        assertTrue(LocalLlmPolicy.isInsideAppStorage("/data/user/0/app/files/local_llm/m.litertlm", roots))
        assertFalse(LocalLlmPolicy.isInsideAppStorage("/sdcard/m.litertlm", roots))
        assertFalse(LocalLlmPolicy.isInsideAppStorage("/data/user/0/app/files/../../other/m.litertlm", roots))
        assertFalse(LocalLlmPolicy.isInsideAppStorage("/data/user/0/app/files", roots))
    }
}
