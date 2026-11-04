package io.github.eslamasabry.oc.android.termux

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The app's preferences file, narrowed to what the move needs (the app binds its SharedPreferences). */
public interface MigrationPreferences {
    public fun getString(key: String): String?

    public fun getBoolean(key: String): Boolean?

    /** True when the value was committed. */
    public fun setString(key: String, value: String): Boolean

    public fun setBoolean(key: String, value: Boolean): Boolean

    /** True when the key is gone afterwards (also when it was never there). */
    public fun remove(key: String): Boolean

    public fun contains(key: String): Boolean

    public fun keys(): Set<String>
}

/** [MigrationPreferences] over an Android [SharedPreferences] file, committing synchronously (call on an IO lane). */
public class SharedPreferencesMigrationPreferences(private val preferences: SharedPreferences) : MigrationPreferences {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getBoolean(key: String): Boolean? = if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    override fun setString(key: String, value: String): Boolean = preferences.edit().putString(key, value).commit()

    override fun setBoolean(key: String, value: Boolean): Boolean = preferences.edit().putBoolean(key, value).commit()

    override fun remove(key: String): Boolean = preferences.edit().remove(key).commit()

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun keys(): Set<String> = preferences.all.keys
}

/** The durable record of one move, per source profile. It survives process death; resume is an explicit action. */
public interface TermuxMigrationJournal {
    public suspend fun read(sourceProfileId: String): JsonObject?

    public suspend fun write(sourceProfileId: String, value: JsonObject)

    public suspend fun remove(sourceProfileId: String)
}

/**
 * The journal under `oc.termuxMigration.<sourceProfileId>`, the profile-scoped key the deletion sweep removes with the
 * profile. [mayWrite] says whether the source profile still exists: a deletion that starts while a write is under way
 * never gets its scoped key recreated.
 */
public class PreferencesTermuxMigrationJournal(
    private val preferences: MigrationPreferences,
    private val mayWrite: ((String) -> Boolean)? = null,
) : TermuxMigrationJournal {
    public companion object {
        public fun key(sourceProfileId: String): String = "oc.termuxMigration.$sourceProfileId"
    }

    override suspend fun read(sourceProfileId: String): JsonObject? =
        preferences.getString(key(sourceProfileId))?.let { Json.parseToJsonElement(it) as JsonObject }

    override suspend fun remove(sourceProfileId: String) {
        if (!preferences.remove(key(sourceProfileId))) fail(TermuxMigrationFailure.storage)
    }

    override suspend fun write(sourceProfileId: String, value: JsonObject) {
        if (mayWrite?.invoke(sourceProfileId) == false) fail(TermuxMigrationFailure.cancelled)
        if (!preferences.setString(key(sourceProfileId), value.toString())) fail(TermuxMigrationFailure.storage)
        // A deletion can begin while the write is under way: do not leave a scoped key behind its sweep.
        if (mayWrite?.invoke(sourceProfileId) == false) {
            preferences.remove(key(sourceProfileId))
            fail(TermuxMigrationFailure.cancelled)
        }
    }
}
