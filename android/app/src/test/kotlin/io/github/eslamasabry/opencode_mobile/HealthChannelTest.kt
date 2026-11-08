package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthChannelTest {
    @Test
    fun runningTrimLevelsBecomeFixedWords() {
        assertEquals("moderate", HealthChannel.levelWord(5))
        assertEquals("low", HealthChannel.levelWord(10))
        assertEquals("critical", HealthChannel.levelWord(15))
    }

    @Test
    fun cachedProcessAndUnknownLevelsAreNotReported() {
        for (level in listOf(0, 20, 40, 60, 80, 999)) {
            assertNull("level $level", HealthChannel.levelWord(level))
        }
    }

    @Test
    fun cpuFamiliesUseTheContractWords() {
        assertEquals("arm64", HealthChannel.abiWord("arm64-v8a"))
        assertEquals("arm32", HealthChannel.abiWord("armeabi-v7a"))
        assertEquals("x86_64", HealthChannel.abiWord("x86_64"))
        assertNull(HealthChannel.abiWord("x86"))
        assertNull(HealthChannel.abiWord("riscv64"))
        assertNull(HealthChannel.abiWord(null))
    }
}
