package io.github.eslamasabry.oc.migration.readers.model

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.prefs.DecodedPref
import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.migration.readers.prefs.StoredKind
import io.github.eslamasabry.oc.migration.readers.xml.RawPref

/** Profile ids as `ProfileId.parse` accepts them (plan/05 §3.2): `^[A-Za-z0-9_-]{1,128}$`, never a dot. */
public object ProfileIds {
    private val SHAPE = Regex("^[A-Za-z0-9_-]{1,128}$")
    public const val AGENTS_SUFFIX: String = ".agents"
    public const val APP_SEGMENT: String = "app"
    public fun isValid(id: String): Boolean = SHAPE.matches(id)
}

public enum class UnknownReason {
    /** No registered shape matches. */
    NoShape,
    /** A registered prefix matches, but the rest is not a valid id. */
    BadIdShape,
    /** The key has the shape of a secure-storage name (a secret written into prefs by mistake?). */
    SecretShapeInPrefs,
}

/** Where a logical key belongs. */
public data class ClassifiedKey(
    val logicalKey: String,
    val key: FlutterKey?,
    /** Profile, owner or team id when the shape carries one (null for `oc.draft.<target>.app`). */
    val id: String?,
    /** `<more>` for IdThenMore, `<target>` for LastSegment, the opaque part for Opaque. */
    val qualifier: String?,
    /** The id came from `<phoneId>.agents`: the agent-backend pseudo-profile of the phone profile. */
    val agentsPseudoProfile: Boolean,
    val unknownReason: UnknownReason?,
) {
    val known: Boolean get() = key != null
}

public object KeyClassifier {

    public fun classify(logicalKey: String): ClassifiedKey {
        FlutterKey.exactMatch(logicalKey)?.let { return ClassifiedKey(logicalKey, it, null, null, false, null) }
        var sawPrefix = false
        for (shape in FlutterKey.prefixCandidates(logicalKey)) {
            sawPrefix = true
            val rest = logicalKey.substring(shape.pattern.length)
            when (shape.form) {
                IdForm.Suffix -> {
                    if (ProfileIds.isValid(rest)) return ClassifiedKey(logicalKey, shape, rest, null, false, null)
                    if (rest.endsWith(ProfileIds.AGENTS_SUFFIX)) {
                        val id = rest.removeSuffix(ProfileIds.AGENTS_SUFFIX)
                        if (ProfileIds.isValid(id)) return ClassifiedKey(logicalKey, shape, id, null, true, null)
                    }
                }
                IdForm.IdThenMore -> {
                    val dot = rest.indexOf('.')
                    if (dot > 0 && dot < rest.length - 1) {
                        val id = rest.substring(0, dot)
                        if (ProfileIds.isValid(id)) return ClassifiedKey(logicalKey, shape, id, rest.substring(dot + 1), false, null)
                    }
                }
                IdForm.LastSegment -> {
                    val dot = rest.lastIndexOf('.')
                    if (dot > 0 && dot < rest.length - 1) {
                        val target = rest.substring(0, dot)
                        val last = rest.substring(dot + 1)
                        if (last == ProfileIds.APP_SEGMENT) return ClassifiedKey(logicalKey, shape, null, target, false, null)
                        if (ProfileIds.isValid(last)) return ClassifiedKey(logicalKey, shape, last, target, false, null)
                    }
                }
                IdForm.Opaque -> return ClassifiedKey(logicalKey, shape, null, rest, false, null)
                IdForm.Exact -> Unit
            }
        }
        if (SecretKey.entries.any { (it.form == IdForm.Exact && it.pattern == logicalKey) || (it.form != IdForm.Exact && logicalKey.startsWith(it.pattern)) }) {
            return ClassifiedKey(logicalKey, null, null, null, false, UnknownReason.SecretShapeInPrefs)
        }
        return ClassifiedKey(logicalKey, null, null, null, false, if (sawPrefix) UnknownReason.BadIdShape else UnknownReason.NoShape)
    }

    /** Secure-storage name → shape and id. */
    public fun classifySecret(name: String): Pair<SecretKey, String?>? {
        for (s in SecretKey.entries.sortedByDescending { it.pattern.length }) {
            when (s.form) {
                IdForm.Exact -> if (name == s.pattern) return s to null
                IdForm.Suffix -> if (name.startsWith(s.pattern)) {
                    val rest = name.substring(s.pattern.length)
                    if (ProfileIds.isValid(rest)) return s to rest
                }
                IdForm.IdThenMore -> if (name.startsWith(s.pattern)) {
                    val rest = name.substring(s.pattern.length)
                    val dot = rest.indexOf('.')
                    if (dot > 0 && ProfileIds.isValid(rest.substring(0, dot))) return s to rest.substring(0, dot)
                }
                IdForm.Opaque -> if (name.startsWith(s.pattern) && name.length > s.pattern.length) return s to null
                IdForm.LastSegment -> Unit
            }
        }
        return null
    }
}

/** Does the stored kind match what the writer writes? */
public enum class TypeCheck { Ok, Mismatch, Undecodable, NullValue }

/** One preference with its key classification and decoded value. */
public class AdoptedEntry(public val physicalKey: String, public val classified: ClassifiedKey, public val decoded: DecodedPref) {
    public val typeCheck: TypeCheck = typeCheckOf(classified.key?.type, decoded)
    override fun toString(): String = "AdoptedEntry(key=${classified.key}, id=${classified.id}, kind=${decoded.kind}, check=$typeCheck)"

    private companion object {
        fun typeCheckOf(expected: ExpectedType?, d: DecodedPref): TypeCheck {
            val v = d.value ?: return TypeCheck.NullValue
            if (v is PrefValue.Undecodable) return TypeCheck.Undecodable
            if (expected == null) return TypeCheck.Ok
            val ok = when (expected) {
                ExpectedType.Bool -> v is PrefValue.Bool
                ExpectedType.Int64 -> v is PrefValue.Int64
                ExpectedType.Dbl -> v is PrefValue.Dbl
                ExpectedType.Str -> v is PrefValue.Str
                ExpectedType.StrList -> v is PrefValue.StrList
                ExpectedType.StrOrList -> v is PrefValue.Str || v is PrefValue.StrList
            }
            return if (ok) TypeCheck.Ok else TypeCheck.Mismatch
        }
    }
}

/** What `oc.profiles` says, read leniently (the full port is `ServerProfileJson` in `:core:model`). */
public sealed interface ProfilesRead {
    public data object Absent : ProfilesRead
    /** Not a JSON list, or not a string: the Flutter app reads that as "no profiles" and never rewrites it on load. */
    public data object Unreadable : ProfilesRead
    public data class Read(val ids: List<String>, val invalidIds: Int, val nonObjects: Int) : ProfilesRead
}

/**
 * The adopted preferences, typed. Nothing is dropped: [toPhysicalMap] gives back exactly the input map
 * (same keys, same raw values), whatever was known, unknown, unprefixed or undecodable.
 */
public class FlutterPrefsModel internal constructor(
    /** Known keys that carry no profile id (app-wide, shared, opaque ids, `oc.draft.<target>.app`). */
    public val app: Map<String, AdoptedEntry>,
    /** Known keys that carry a profile (or owner/team) id, grouped by that id. */
    public val byId: Map<String, Map<String, AdoptedEntry>>,
    /** Keys under `flutter.` that no shape explains. Preserved and counted, never deleted or rewritten. */
    public val unknown: Map<String, AdoptedEntry>,
    /** Physical keys without the `flutter.` prefix (Dart never sees them). Preserved untouched. */
    public val unprefixed: Map<String, RawPref>,
    public val profiles: ProfilesRead,
    private val physicalOrder: List<String>,
    private val rawByPhysical: Map<String, RawPref>,
) {
    public fun get(key: FlutterKey): AdoptedEntry? = if (key.form == IdForm.Exact) app[key.pattern] else null

    public fun forProfile(id: String): Map<String, AdoptedEntry> = byId[id].orEmpty()

    public fun forProfile(id: String, key: FlutterKey): List<AdoptedEntry> = forProfile(id).values.filter { it.classified.key == key }

    /** Ids that own data but are not in `oc.profiles` (deleted servers' leftovers, owner ids of shared agents). */
    public fun idsWithoutProfile(): Set<String> {
        val known = (profiles as? ProfilesRead.Read)?.ids?.toSet() ?: emptySet()
        return byId.keys - known
    }

    public fun toPhysicalMap(): LinkedHashMap<String, RawPref> {
        val out = LinkedHashMap<String, RawPref>()
        for (k in physicalOrder) out[k] = rawByPhysical.getValue(k)
        return out
    }

    public fun census(): PrefsCensus {
        val all = app.values + byId.values.flatMap { it.values } + unknown.values
        return PrefsCensus(
            physicalKeys = physicalOrder.size,
            unprefixedKeys = unprefixed.size,
            knownKeys = all.size - unknown.size,
            unknownKeys = unknown.size,
            unknownByReason = unknown.values.groupingBy { it.classified.unknownReason!! }.eachCount(),
            byStoredKind = all.groupingBy { it.decoded.kind }.eachCount(),
            typeMismatches = all.count { it.typeCheck == TypeCheck.Mismatch },
            undecodable = all.count { it.typeCheck == TypeCheck.Undecodable },
            idsWithData = byId.size,
            idsWithoutProfile = idsWithoutProfile().size,
            agentsPseudoKeys = all.count { it.classified.agentsPseudoProfile },
        )
    }

    override fun toString(): String = "FlutterPrefsModel(${census()})"
}

/** Counts only: safe for the journal, the audit and Report a problem. */
public data class PrefsCensus(
    val physicalKeys: Int,
    val unprefixedKeys: Int,
    val knownKeys: Int,
    val unknownKeys: Int,
    val unknownByReason: Map<UnknownReason, Int>,
    val byStoredKind: Map<StoredKind, Int>,
    val typeMismatches: Int,
    val undecodable: Int,
    val idsWithData: Int,
    val idsWithoutProfile: Int,
    val agentsPseudoKeys: Int,
)

public object FlutterPrefsModelBuilder {
    /** Build from the map Android would load (`PrefsXmlRead.androidMap`, or `getSharedPreferences(..).all`). */
    public fun build(physical: Map<String, RawPref>): FlutterPrefsModel {
        val app = LinkedHashMap<String, AdoptedEntry>()
        val byId = LinkedHashMap<String, LinkedHashMap<String, AdoptedEntry>>()
        val unknown = LinkedHashMap<String, AdoptedEntry>()
        val unprefixed = LinkedHashMap<String, RawPref>()
        for ((pk, raw) in physical) {
            val logical = FlutterPrefsCodec.logicalKey(pk)
            if (logical == null) {
                unprefixed[pk] = raw
                continue
            }
            val c = KeyClassifier.classify(logical)
            val entry = AdoptedEntry(pk, c, FlutterPrefsCodec.decode(raw))
            when {
                !c.known -> unknown[logical] = entry
                c.id != null -> byId.getOrPut(c.id) { LinkedHashMap() }[logical] = entry
                else -> app[logical] = entry
            }
        }
        val profiles = readProfiles(app[FlutterKey.Profiles.pattern])
        return FlutterPrefsModel(app, byId, unknown, unprefixed, profiles, physical.keys.toList(), LinkedHashMap(physical))
    }

    private fun readProfiles(entry: AdoptedEntry?): ProfilesRead {
        if (entry == null) return ProfilesRead.Absent
        val text = (entry.decoded.value as? PrefValue.Str)?.value ?: return ProfilesRead.Unreadable
        val list = MiniJson.parseOrNull(text) as? List<*> ?: return ProfilesRead.Unreadable
        val ids = ArrayList<String>()
        var invalid = 0
        var nonObjects = 0
        for (e in list) {
            val m = e as? Map<*, *>
            if (m == null) {
                nonObjects++
                continue
            }
            val id = m["id"] as? String
            if (id == null || !ProfileIds.isValid(id)) invalid++ else ids += id
        }
        return ProfilesRead.Read(ids, invalid, nonObjects)
    }
}

/** plan/05 §3.4, verbatim: the generic profile sweep over logical keys starting with `oc.`. */
public object ProfileSweep {
    public fun scopedKeys(profileId: String, logicalKeys: Collection<String>): Set<String> {
        val suffix = ".$profileId"
        val infix = ".$profileId."
        return logicalKeys.filter { it.startsWith("oc.") && (it.endsWith(suffix) || it.contains(infix)) }.toSet()
    }
}
