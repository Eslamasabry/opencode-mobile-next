package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageWidgetPlansTest {
    private fun numbers(provider: String = "codex") = UsageWidgetRow(
        provider = provider, displayName = provider, profileName = "Computer",
        seconds = 18000, label = "", left = 62, resetsAt = 5000,
        fetchedAt = 1000, authNeeded = false
    )

    private fun setup(provider: String = "glm") = numbers(provider).copy(
        seconds = 0, left = 0, signInOnly = true, fixAction = "add_key"
    )

    @Test fun emptyDefaultFeedUsesTheAvailableMeasuredLimit() {
        val measured = listOf(numbers())
        assertEquals(measured, UsageWidgetPlans.chooseFeed(emptyList(), measured, false))
    }

    @Test fun defaultSetupRowsDoNotHideAvailableMeasuredLimits() {
        val pending = setup()
        val measured = numbers()
        assertEquals(listOf(measured, pending),
            UsageWidgetPlans.chooseFeed(listOf(pending), listOf(measured), false))
    }

    @Test fun explicitHiddenSelectionStaysEmpty() {
        assertEquals(emptyList<UsageWidgetRow>(),
            UsageWidgetPlans.chooseFeed(emptyList(), listOf(numbers()), true))
    }

    @Test fun explicitSetupOnlySelectionDoesNotResurrectHiddenNumbers() {
        val selected = listOf(setup())
        assertEquals(selected, UsageWidgetPlans.chooseFeed(selected, listOf(numbers()), true))
    }

    @Test fun missingFeedDoesNotBypassExplicitHiddenSelection() {
        assertEquals(emptyList<UsageWidgetRow>(),
            UsageWidgetPlans.chooseFeed(null, listOf(numbers()), true))
    }

    @Test fun fallbackMeasurementReplacesDuplicateSetupForThatProvider() {
        val measured = numbers()
        assertEquals(listOf(measured), UsageWidgetPlans.chooseFeed(
            listOf(setup("codex")), listOf(measured), false))
    }

    @Test fun subscriptionNumbersKeepTheirChosenWindowsAndOrder() {
        val selected = listOf(numbers("minimax").copy(seconds = 604800))
        assertEquals(selected, UsageWidgetPlans.chooseFeed(selected, listOf(numbers()), false))
    }

    @Test fun absenceOfAnyMeasurementNeverInventsNumbers() {
        val pending = listOf(setup())
        assertEquals(pending, UsageWidgetPlans.chooseFeed(pending, emptyList(), false))
        assertEquals(emptyList<UsageWidgetRow>(),
            UsageWidgetPlans.chooseFeed(emptyList(), emptyList(), false))
    }
}
