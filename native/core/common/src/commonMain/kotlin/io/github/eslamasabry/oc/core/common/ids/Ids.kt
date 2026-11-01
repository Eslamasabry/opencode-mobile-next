package io.github.eslamasabry.oc.core.common.ids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable @JvmInline public value class ProfileId(public val raw: String)

@Serializable @JvmInline public value class SessionId(public val raw: String)

@Serializable @JvmInline public value class MessageId(public val raw: String)

@Serializable @JvmInline public value class RequestId(public val raw: String)

@Serializable public data class ConversationRef(val profileId: ProfileId, val sessionId: SessionId)
