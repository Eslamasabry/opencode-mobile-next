package io.github.eslamasabry.oc.android.termux

import io.github.eslamasabry.oc.android.termux.TermuxMigrationItem.config
import io.github.eslamasabry.oc.android.termux.TermuxMigrationItem.projects
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Flutter test/termux_migration_space_test.dart, case for case.
class TermuxMigrationSpaceTest {
    private val largeFree = 2L * 1024 * 1024 * 1024

    private fun source(sizes: List<TermuxMigrationSize>, free: Long = largeFree) = TermuxMigrationSource(items = sizes, freeBytes = free)

    private fun space(source: TermuxMigrationSource, selected: Set<TermuxMigrationItem>, app: Long?): TermuxMigrationSpace =
        (migrationSpaceFor(source, selected, app) as MigrationAnswer.Ok).value

    private fun refused(source: TermuxMigrationSource, selected: Set<TermuxMigrationItem>, app: Long?): TermuxMigrationFailure =
        (migrationSpaceFor(source, selected, app) as MigrationAnswer.Refused).code

    @Test
    fun `B-LINUX-236 a selected subset uses the conservative retained-copy budget`() {
        val inventory = source(listOf(TermuxMigrationSize(projects, 100, 2), TermuxMigrationSize(config, 200, 3)))
        assertEquals(3 * (100L + 2 * 4096 + 10240) + 64L * 1024 * 1024, space(inventory, setOf(projects), largeFree).requiredBytes)
        val both = space(inventory, setOf(projects, config), largeFree)
        assertEquals(3 * (100L + 2 * 4096 + 10240 + 200 + 3 * 4096 + 10240) + MIGRATION_RESERVE_BYTES, both.requiredBytes)
        assertEquals(true, both.sufficient)
    }

    @Test
    fun `B-LINUX-236 capacity is the smaller reading and equality fits`() {
        val inventory = source(listOf(TermuxMigrationSize(projects, 10, 1)))
        val required = space(inventory, setOf(projects), largeFree).requiredBytes!!
        val appLimited = space(inventory, setOf(projects), required - 1)
        assertEquals(required - 1, appLimited.availableBytes)
        assertEquals(false, appLimited.sufficient)
        val sourceLimited = space(source(inventory.items, free = required - 1), setOf(projects), largeFree)
        assertEquals(required - 1, sourceLimited.availableBytes)
        assertEquals(false, sourceLimited.sufficient)
        assertEquals(true, space(source(inventory.items, free = required), setOf(projects), required).sufficient)
    }

    @Test
    fun `B-LINUX-236 unknown readings remain unknown and retain the measured side`() {
        val inventory = source(listOf(TermuxMigrationSize(projects, 10, 1)))
        for (appFree in listOf(null, -1L)) {
            val budget = space(inventory, setOf(projects), appFree)
            assertTrue(budget.requiredBytes!! > 0)
            assertNull(budget.appAvailableBytes)
            assertEquals(largeFree, budget.sourceAvailableBytes)
            assertNull(budget.availableBytes)
            assertNull(budget.sufficient)
        }
        val unknownSource = space(source(inventory.items, free = -1), setOf(projects), largeFree)
        assertEquals(largeFree, unknownSource.appAvailableBytes)
        assertNull(unknownSource.sourceAvailableBytes)
        assertNull(unknownSource.availableBytes)
        assertNull(unknownSource.sufficient)
    }

    @Test
    fun `B-LINUX-236 an empty selection requires zero and does not count refused items`() {
        val inventory = source(listOf(TermuxMigrationSize(config, 0, 0, problem = TermuxMigrationFailure.unsupportedEntry)))
        assertEquals(0L, space(inventory, emptySet(), 0).requiredBytes)
        assertEquals(true, space(inventory, emptySet(), 0).sufficient)
        assertNull(space(inventory, emptySet(), null).sufficient)
    }

    @Test
    fun `B-LINUX-236 a refused selection fails with its fixed code and an unselected refusal is ignored`() {
        val inventory = source(
            listOf(
                TermuxMigrationSize(projects, 5, 1),
                TermuxMigrationSize(config, 0, 0, problem = TermuxMigrationFailure.unsupportedEntry),
            ),
        )
        assertEquals(TermuxMigrationFailure.unsupportedEntry, refused(inventory, setOf(config), largeFree))
        assertEquals(true, space(inventory, setOf(projects), largeFree).sufficient)
    }

    @Test
    fun `B-LINUX-236 missing or malformed measurements cannot produce a budget`() {
        for (inventory in listOf(
            source(emptyList()),
            source(listOf(TermuxMigrationSize(projects, -1, 0))),
            source(listOf(TermuxMigrationSize(projects, 0, -1))),
            source(listOf(TermuxMigrationSize(projects, 0, 0), TermuxMigrationSize(projects, 0, 0))),
        )) {
            assertEquals(TermuxMigrationFailure.invalidSelection, refused(inventory, setOf(projects), largeFree))
        }
    }

    @Test
    fun `B-LINUX-236 archive and entry limits include their boundaries and apply per selected item`() {
        val exactArchive = source(listOf(TermuxMigrationSize(projects, MIGRATION_MAX_ARCHIVE_BYTES - 1024 - 10240, 1)))
        assertEquals(true, space(exactArchive, setOf(projects), largeFree).sufficient)
        val exactEntries = source(listOf(TermuxMigrationSize(projects, 0, MIGRATION_MAX_ENTRIES)))
        assertEquals(true, space(exactEntries, setOf(projects), largeFree).sufficient)
        for (size in listOf(
            TermuxMigrationSize(projects, MIGRATION_MAX_ARCHIVE_BYTES - 1024 - 10240 + 1, 1),
            TermuxMigrationSize(projects, 0, MIGRATION_MAX_ENTRIES + 1),
        )) {
            assertEquals(TermuxMigrationFailure.tooLarge, refused(source(listOf(size)), setOf(projects), largeFree))
            assertEquals(0L, space(source(listOf(size)), emptySet(), largeFree).requiredBytes)
            // The review says the same limit before packing.
            assertEquals(TermuxMigrationFailure.tooLarge, migrationItemProblem(size))
        }
        assertNull(migrationItemProblem(exactArchive.items.single()))
    }
}
