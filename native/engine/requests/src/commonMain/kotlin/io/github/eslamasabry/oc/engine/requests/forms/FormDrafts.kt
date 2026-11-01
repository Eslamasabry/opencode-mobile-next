package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * Drafts of open forms (plan 09 §5.5, map CHAT-GENUI-48): what the person typed and chose is kept per form until it is
 * sent or declined, so swipe, back, Esc and close all close silently and reopening brings the answers back. Keyed by the
 * form's identity ([FormRevision.identityKey]), so a newer revision of the same form never inherits an old draft.
 *
 * Multi-line answers are also stored under [storageKey] so they survive a restart and the profile deletion sweep finds
 * them (B-STATE-024); the store and its `ProfileScope` registration belong to the drafts owner (`:engine:composer`).
 */
public class FormDrafts {
    private val drafts = HashMap<String, FormDraft>()

    public fun get(identity: String): FormDraft? = drafts[identity]

    public fun put(identity: String, draft: Map<String, FormValue>) {
        drafts[identity] = draft.toImmutableMap()
    }

    /** Sent or declined: the draft goes. */
    public fun clear(identity: String) {
        drafts.remove(identity)
    }

    /** The kept draft, else the form's defaults. */
    public fun open(identity: String, spec: FormSpec): FormDraft = drafts[identity] ?: FormRules.initialDraft(spec)

    public companion object {
        /** `oc.draft.<target>.<profileId>` (B-STATE-024); the target names the form identity and the field. */
        public fun storageKey(identity: String, fieldKey: String, profileId: String): String {
            require(profileId.isNotEmpty() && '.' !in profileId) { "a draft key needs a plain profile id" }
            val field = DartJson.sha256Hex(fieldKey).take(16) // any key spelling, no collisions, no dots
            return "oc.draft.form-$identity-$field.$profileId"
        }

        public val EMPTY: FormDraft = persistentMapOf()
    }
}
