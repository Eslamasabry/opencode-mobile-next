package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Scheduled prompts (plan 07 §5 K, row 78). Owned by :feature:schedules. */
@Serializable
public sealed interface SchedulesRoute : AppRoute {
    /**
     * One server's scheduled prompts. [projectDir] seeds a new schedule's project; [from] is the conversation the
     * list was opened from (a new schedule keeps that conversation and its model); [highlight] is the schedule a
     * notification asked for.
     */
    @Serializable
    public data class List(
        val profileId: ProfileId,
        val projectDir: String? = null,
        val from: ConversationRef? = null,
        val highlight: String? = null,
    ) : SchedulesRoute

    /** The editor: [scheduleId] null makes a new schedule. */
    @Serializable
    public data class Edit(
        val profileId: ProfileId,
        val scheduleId: String? = null,
        val projectDir: String? = null,
        val from: ConversationRef? = null,
    ) : SchedulesRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }
}
