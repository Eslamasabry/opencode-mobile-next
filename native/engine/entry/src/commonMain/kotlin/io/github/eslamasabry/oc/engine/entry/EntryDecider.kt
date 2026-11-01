package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Drop
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Go
import io.github.eslamasabry.oc.engine.entry.EntryDecision.Wait
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * `decide(request, world)` (07 §7.3 rule 2): a pure function, table-tested, re-run on every change. It never touches a
 * server; it names the effects the router runs. The trust rule (Astra M2, 16 §1.8.1) lives here: [EntryEffect.Connect]
 * and [EntryEffect.CreateSession] appear only for [EntryOrigin.Internal] requests, plus the share's CreateSession
 * (07 §7.7, an empty session holding an unsent draft).
 */
public object EntryDecider {
    public fun decide(request: EntryRequest, world: EntryWorld, now: Instant): EntryDecision {
        // Rule 3: nothing before bootstrap and the migration gate.
        if (!world.open) return Wait(WaitReason.Bootstrap)
        return when (request) {
            is EntryRequest.Launch -> launch(request, world)
            is EntryRequest.PinnedSession -> pinned(request, world)
            is EntryRequest.Link -> link(request, world)
            is EntryRequest.SharedText -> share(request, world)
            is EntryRequest.AlertOpen -> alert(request, world)
            is EntryRequest.ScheduledOpen -> scheduled(request, world, now)
            is EntryRequest.TilePreferences ->
                when (request.tile) {
                    TileId.Attention -> Go(EntryTarget.Settings(SettingsSectionTarget.Notifications))
                    TileId.Pause -> Go(EntryTarget.Settings(SettingsSectionTarget.Background))
                }
            is EntryRequest.Internal -> Go(request.target)
        }
    }

    // ---- 07 §7.4 launcher actions ----------------------------------------------------------------------------------

    private fun launch(request: EntryRequest.Launch, world: EntryWorld): EntryDecision =
        when (val action = request.action) {
            LaunchAction.Connect -> Go(EntryTarget.Servers(null))
            LaunchAction.NewTask -> newTask(request.origin, world)
            LaunchAction.Activity -> withProfile(world) { Go(EntryTarget.NeedsYou) }
            LaunchAction.Usage -> withProfile(world) { Go(EntryTarget.Usage(UsageSectionTarget.Plans)) }
            LaunchAction.UsageSettings -> Go(EntryTarget.UsageWidgetSettings)
            LaunchAction.BackgroundSettings -> Go(EntryTarget.Settings(SettingsSectionTarget.Background))
            LaunchAction.Linux -> if (world.isAndroid) Go(EntryTarget.ThisPhone) else Drop()
            LaunchAction.PhoneSetup, LaunchAction.PhoneSetupDone -> phoneSetup(world)
            is LaunchAction.UsageTarget -> usageTarget(action, request.origin, world)
        }

    private fun newTask(origin: EntryOrigin, world: EntryWorld): EntryDecision {
        val active = world.activeProfileId ?: return noProfile(world)
        // Untrusted: the sheet with the server named and a Start button; nothing is created before the tap.
        if (origin == EntryOrigin.Untrusted) return Go(EntryTarget.NewConversation(active))
        return whenReady(world, active) {
            Go(
                EntryTarget.Conversations(ConversationsFilter.None),
                listOf(
                    EntryEffect.CreateSession(
                        active,
                        focusComposer = true,
                        onFailure = Fallback(EntryTarget.Conversations(ConversationsFilter.None), EntryNotice.CreateFailed),
                    ),
                ),
            )
        }
    }

    private fun phoneSetup(world: EntryWorld): EntryDecision {
        if (!world.isAndroid) return Drop()
        return when (world.phoneSetup) {
            PhoneSetupStatus.Running, PhoneSetupStatus.Stopped -> Go(EntryTarget.PhoneSetup(SetupStage.Progress))
            PhoneSetupStatus.DoneReadyNotShown -> Go(EntryTarget.PhoneSetup(SetupStage.Ready))
            PhoneSetupStatus.None, PhoneSetupStatus.Done -> Drop()
        }
    }

    /** B-NAV-077: Claude on this phone → agent sign-in; set_up → Read usage from; everything else → provider sign-in. */
    private fun usageTarget(action: LaunchAction.UsageTarget, origin: EntryOrigin, world: EntryWorld): EntryDecision {
        val profile = action.profileId
        val provider =
            world.usageProviders.firstOrNull { it.profileId == profile && it.providerId == action.providerId }
        if (profile !in world.savedProfileIds || provider == null) {
            return Go(EntryTarget.Usage(UsageSectionTarget.Plans), notice = EntryNotice.UsageRowGone)
        }
        val page =
            when {
                action.kind == UsageTargetKind.SetUp -> EntryTarget.UsageReadFrom(profile, action.providerId)
                action.kind == UsageTargetKind.SignIn && provider.isClaudeOnThisPhone ->
                    EntryTarget.AgentSignIn(action.providerId)
                else -> EntryTarget.ProviderSignIn(profile, action.providerId)
            }
        val link = world.linkOf(profile)
        if (link == LinkState.Ready && profile == world.activeProfileId) return Go(page)
        if (origin == EntryOrigin.Untrusted) return Go(EntryTarget.ConfirmConnect(profile, page)) // never connects
        return when (link) {
            LinkState.Connecting -> Wait(WaitReason.Connection, profile)
            LinkState.NeedsReentry -> reentry(profile)
            LinkState.Ready, LinkState.Idle, LinkState.Failed ->
                Go(
                    page,
                    listOf(
                        EntryEffect.Connect(
                            profile,
                            Fallback(EntryTarget.Usage(UsageSectionTarget.Plans), EntryNotice.ConnectionFailed),
                        ),
                    ),
                )
        }
    }

    // ---- 07 §7.5 pinned sessions -----------------------------------------------------------------------------------

    private fun pinned(request: EntryRequest.PinnedSession, world: EntryWorld): EntryDecision {
        val active = world.activeProfileId ?: return noProfile(world)
        if (request.profileId != active) return Drop(EntryNotice.OtherServer) // never switches silently
        return openChat(world, ConversationRef(active, request.sessionId), EntryTarget.Chat(ConversationRef(active, request.sessionId)))
    }

    // ---- 07 §7.6 deep links ----------------------------------------------------------------------------------------

    private fun link(request: EntryRequest.Link, world: EntryWorld): EntryDecision =
        when (val parsed = EntryLinkParser.parse(request.raw)) {
            null -> Drop()
            is ParsedLink.TeamGate -> withProfile(world) { Go(EntryTarget.NeedsYou) }
            is ParsedLink.TeamRun -> withProfile(world) { Go(EntryTarget.Conversations(ConversationsFilter.Running)) }
            is ParsedLink.Session -> {
                val ref = ConversationRef(parsed.profileId, parsed.sessionId)
                when (parsed.profileId) {
                    world.activeProfileId -> openChat(world, ref, EntryTarget.Chat(ref))
                    // Links are untrusted (16 §1.8.1): ask before switching instead of 07 §7.6's Connect (D-E1).
                    in world.savedProfileIds ->
                        Go(EntryTarget.SwitchServerThen(parsed.profileId, SwitchReason.Link, EntryTarget.Chat(ref)))
                    else ->
                        Go(
                            EntryTarget.SessionAddress(0),
                            listOf(EntryEffect.ParkSessionLink(parsed.profileId, parsed.sessionId)),
                        )
                }
            }
        }

    // ---- 07 §7.7 share in ------------------------------------------------------------------------------------------

    private fun share(request: EntryRequest.SharedText, world: EntryWorld): EntryDecision {
        val active =
            world.activeProfileId
                ?: return Go(EntryTarget.Servers(null), notice = EntryNotice.ShareNeedsServer)
        return whenReady(world, active) {
            Go(
                EntryTarget.Conversations(ConversationsFilter.None),
                listOf(
                    EntryEffect.CreateSession(active, focusComposer = true, onFailure = Fallback(null, EntryNotice.ShareFailed)),
                    EntryEffect.SeedDraft(request.text, Fallback(null, EntryNotice.ShareFailed, keepTarget = true)),
                ),
            )
        }
    }

    // ---- 07 §7.8 notification taps ---------------------------------------------------------------------------------

    private fun alert(request: EntryRequest.AlertOpen, world: EntryWorld): EntryDecision {
        val token = request.monitorToken
        if (token != null) {
            // Another saved server's watch: revalidate, then ask to switch; never answers, never waits for transport.
            val profile = request.profileId ?: return Drop()
            val session = request.sessionId ?: return Drop()
            val ref = ConversationRef(profile, session)
            return Go(
                EntryTarget.SwitchServerThen(profile, SwitchReason.Alert, EntryTarget.Chat(ref, landOn = LandOn.OldestRequest)),
                listOf(EntryEffect.ResolveMonitoredRequest(token, profile, session, Fallback(null, EntryNotice.RequestGone))),
            )
        }
        return when (request.kind) {
            AlertKind.Test -> Drop()
            AlertKind.TeamDecision, AlertKind.TeamReview -> withProfile(world) { Go(EntryTarget.NeedsYou) }
            AlertKind.TeamRunFailed, AlertKind.TeamCompleted, AlertKind.TeamProgress ->
                withProfile(world) { Go(EntryTarget.Conversations(ConversationsFilter.None)) }
            AlertKind.Quota, AlertKind.QuotaReset ->
                withProfile(world) {
                    val plans = EntryTarget.Usage(UsageSectionTarget.Plans)
                    val quotaToken = request.sessionId?.raw ?: return@withProfile Go(plans)
                    Go(plans, listOf(EntryEffect.VerifyQuotaToken(request.profileId, quotaToken, Fallback(plans, EntryNotice.PlanChanged))))
                }
            AlertKind.Permission, AlertKind.Question, AlertKind.Complete, AlertKind.Error, AlertKind.Checkin ->
                chatAlert(request, world)
        }
    }

    private fun chatAlert(request: EntryRequest.AlertOpen, world: EntryWorld): EntryDecision {
        val session = request.sessionId ?: return Drop()
        val active = world.activeProfileId
        val owner = request.profileId ?: active ?: return noProfile(world)
        val ref = ConversationRef(owner, session)
        val chat =
            when (request.kind) {
                AlertKind.Permission, AlertKind.Question -> EntryTarget.Chat(ref, landOn = LandOn.OldestRequest)
                AlertKind.Error -> EntryTarget.Chat(ref, landOn = LandOn.Failure)
                AlertKind.Complete -> EntryTarget.Chat(ref, focusComposer = request.reply)
                else -> EntryTarget.Chat(ref)
            }
        // A widget row (internal) may sit on another project or connection: resolve it, switching first.
        if (request.kind == AlertKind.Complete && request.profileId != null && request.origin == EntryOrigin.Internal) {
            return Go(
                EntryTarget.Land(null),
                listOf(
                    EntryEffect.ResolveWidgetConversation(
                        owner,
                        session,
                        request.reply,
                        Fallback(EntryTarget.Land(null), EntryNotice.ConversationNotFound),
                    ),
                ),
            )
        }
        if (active == null) return noProfile(world)
        if (owner != active) {
            if (owner !in world.savedProfileIds) return Drop(EntryNotice.ConversationNotFound)
            return Go(EntryTarget.SwitchServerThen(owner, SwitchReason.Alert, chat))
        }
        return openChat(world, ref, chat)
    }

    // ---- 07 §7.9 scheduled-run taps --------------------------------------------------------------------------------

    private fun scheduled(request: EntryRequest.ScheduledOpen, world: EntryWorld, now: Instant): EntryDecision {
        // A deleted schedule or a changed server → nothing (never falls back to another target).
        val info = world.schedules[request.scheduleId] ?: return Drop()
        if (info.profileId != request.profileId) return Drop()
        val list = EntryTarget.ScheduledPrompts(info.profileId, info.projectDir, request.scheduleId)
        val session = request.sessionId
        if (info.morningSummary || session == null) return Go(list)
        val ref = ConversationRef(info.profileId, session)
        val held = now - request.receivedAt < EntryLimits.SCHEDULE_HOLD_SECONDS.seconds
        if (info.profileId != world.activeProfileId) {
            return when {
                request.origin == EntryOrigin.Internal ->
                    Go(
                        EntryTarget.Chat(ref),
                        listOf(EntryEffect.Connect(info.profileId, Fallback(list, null)), EntryEffect.ResolveRef(ref, Fallback(list, null))),
                    )
                info.profileId in world.savedProfileIds ->
                    Go(EntryTarget.SwitchServerThen(info.profileId, SwitchReason.Schedule, EntryTarget.Chat(ref)))
                else -> Drop()
            }
        }
        return when (world.linkOf(info.profileId)) {
            LinkState.Ready -> Go(EntryTarget.Chat(ref), listOf(EntryEffect.ResolveRef(ref, Fallback(list, null))))
            LinkState.Connecting, LinkState.Idle ->
                if (held) Wait(WaitReason.ScheduleSettling, info.profileId) else Go(list)
            LinkState.NeedsReentry, LinkState.Failed -> Go(list)
        }
    }

    // ---- shared rules ----------------------------------------------------------------------------------------------

    /** Rule 4, final: no active profile → Servers with a plain notice. */
    private fun noProfile(world: EntryWorld): EntryDecision =
        Go(
            EntryTarget.Servers(null),
            notice = if (world.savedProfileIds.isEmpty()) EntryNotice.AddServerFirst else EntryNotice.ChooseServerFirst,
        )

    private inline fun withProfile(world: EntryWorld, go: (ProfileId) -> EntryDecision): EntryDecision {
        val active = world.activeProfileId ?: return noProfile(world)
        return go(active)
    }

    private fun reentry(profile: ProfileId): EntryDecision =
        Go(EntryTarget.Servers(ServersRequestTarget.Edit(profile, focusPassword = true)), notice = EntryNotice.SignInAgain)

    /** Rule 4: waiting while the active server is on its way; final on re-entry or failure. */
    private inline fun whenReady(world: EntryWorld, profile: ProfileId, ready: () -> EntryDecision): EntryDecision =
        when (world.linkOf(profile)) {
            LinkState.Ready -> ready()
            LinkState.Connecting, LinkState.Idle -> Wait(WaitReason.Connection, profile)
            LinkState.NeedsReentry -> reentry(profile)
            LinkState.Failed -> Go(EntryTarget.Servers(null), notice = EntryNotice.ConnectionFailed)
        }

    private fun openChat(world: EntryWorld, ref: ConversationRef, chat: EntryTarget.Chat): EntryDecision =
        whenReady(world, ref.profileId) {
            Go(chat, listOf(EntryEffect.ResolveRef(ref, Fallback(EntryTarget.Land(null), EntryNotice.ConversationNotFound))))
        }
}
