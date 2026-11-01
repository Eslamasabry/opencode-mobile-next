package io.github.eslamasabry.oc.core.common.ids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// Identifiers (plan 03 §10.1). Value classes; serializable for route keys and storage. The raw string is the
// server's (or the app's) opaque id, never parsed. Gateways take these, not bare Strings (S0-CORE D-C3).

@Serializable @JvmInline public value class ProfileId(public val raw: String)

@Serializable @JvmInline public value class SessionId(public val raw: String)

@Serializable @JvmInline public value class MessageId(public val raw: String)

@Serializable @JvmInline public value class PartId(public val raw: String)

/** A permission, question or form request id. */
@Serializable @JvmInline public value class RequestId(public val raw: String)

@Serializable @JvmInline public value class ScheduleId(public val raw: String)

@Serializable @JvmInline public value class ProjectId(public val raw: String)

@Serializable @JvmInline public value class WorkspaceId(public val raw: String)

@Serializable @JvmInline public value class InboxItemId(public val raw: String)

@Serializable @JvmInline public value class FormId(public val raw: String)

@Serializable @JvmInline public value class PtyId(public val raw: String)

@Serializable public data class ConversationRef(val profileId: ProfileId, val sessionId: SessionId)
