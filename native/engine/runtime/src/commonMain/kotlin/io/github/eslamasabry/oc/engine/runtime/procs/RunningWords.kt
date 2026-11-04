package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.math.roundToLong

/**
 * Port of lib/ui/widgets/running_on_phone_text.dart and `runningRowStatus` of lib/ui/widgets/running_on_phone_row.dart
 * (the words of "Running on this phone": a process's plain name, never its command line; how hard it works; This
 * phone's line, the most pressing first). The engine chooses WHICH words and their arguments; the sentences live in
 * the app's strings, named per value.
 */
public enum class RunningNameKind(public val key: String) {
    FlutterTest("flutterTest"),
    Command("command"),
    Linux("linux"),
    OpenCode("openCode"),
    ClaudeCode("claudeCode"),
    AgentHelper("agentHelper"),
    SignIn("signIn"),
    Team("team"),
    Node("node"),
    Python("python"),
    Git("git"),
    Npm("npm"),
    Dart("dart"),
    Flutter("flutter"),
    Gradle("gradle"),
    Java("java"),
    Search("search"),
    App("app"),

    /** A process with no name at all ("Program"). */
    Other("other"),
}

/** How a process is named: a plain name (Flutter l10n `runningPhoneName(kind.key)`), or the kernel's short name. */
public sealed interface RunningProcessName {
    public data class Plain(val kind: RunningNameKind) : RunningProcessName

    /** A program with no plain name keeps the kernel's short name ("make"). */
    public data class Kernel(val name: String) : RunningProcessName
}

/** The plain-name kind of [p], or null when its program has no plain name and the kernel's short name is shown. */
public fun runningNameKind(p: RunningProcess): RunningNameKind? {
    if (p.owner == PhoneProcessOwner.App) return RunningNameKind.App
    val name = p.name.lowercase()
    val command = p.command.lowercase()
    when (name) {
        "flutter_tester" -> return RunningNameKind.FlutterTest
        "bash", "sh", "dash", "zsh", "ash", "fish", "busybox" -> return RunningNameKind.Command
        "proot", "proot-rs", "libproot.so", "loader" -> return RunningNameKind.Linux
        "claude" -> return RunningNameKind.ClaudeCode
        "opencode", ".opencode" -> return RunningNameKind.OpenCode
        "rg" -> return RunningNameKind.Search
        "git", "git-remote-http", "git-remote-https" -> return RunningNameKind.Git
        "npm", "npx", "npm exec" -> return RunningNameKind.Npm
        "dart", "dartvm", "dartaotruntime" -> return RunningNameKind.Dart
        "flutter" -> return RunningNameKind.Flutter
        "java" -> return if (command.contains("gradle")) RunningNameKind.Gradle else RunningNameKind.Java
        // Node names its main thread "MainThread" in the kernel.
        "node", "node-mainthread", "mainthread" -> return when {
            command.contains("opencode") -> RunningNameKind.OpenCode
            command.contains("claude") -> RunningNameKind.ClaudeCode
            command.contains("paseo") -> RunningNameKind.AgentHelper
            command.contains("npm-cli") || command.contains("/npm ") -> RunningNameKind.Npm
            else -> RunningNameKind.Node
        }
    }
    if (name.startsWith("python")) return RunningNameKind.Python
    // The in-app server's binary ("opencode2") and the helper's renamed processes ("Paseo Supervisor").
    if (name.startsWith("opencode")) return RunningNameKind.OpenCode
    if (name.startsWith("paseo")) return RunningNameKind.AgentHelper
    if (p.owner == PhoneProcessOwner.Engine) return RunningNameKind.Team
    if (p.owner == PhoneProcessOwner.AgentSignIn) return RunningNameKind.SignIn
    if (name.isEmpty()) return RunningNameKind.Other
    return null
}

/** "Flutter test", "Command", "Claude Code"; the kernel's own short name ("make") when there is no plain name. */
public fun runningProcessName(p: RunningProcess): RunningProcessName =
    runningNameKind(p)?.let { RunningProcessName.Plain(it) } ?: RunningProcessName.Kernel(p.name)

/** The load word (Flutter l10n `runningPhoneCpu` level: idle / light / busy / other = Very busy). */
public enum class CpuLevel(public val key: String) { Idle("idle"), Light("light"), Busy("busy"), VeryBusy("veryBusy") }

/** "Idle", "Light", "Busy" or "Very busy" by share of one core. */
public fun runningCpuLevel(percent: Double): CpuLevel = when {
    percent < 5 -> CpuLevel.Idle
    percent < 30 -> CpuLevel.Light
    percent <= PhoneProcessWatch.HEAVY_CPU -> CpuLevel.Busy
    else -> CpuLevel.VeryBusy
}

/** A percent as the page writes it (Flutter `formatTermuxCpuPct`): whole from 10, one decimal below. Latin digits. */
public fun formatCpuPercent(pct: Double): String {
    if (pct >= 10) return pct.roundToLong().toString()
    val tenths = (pct * 10).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}.${abs % 10}"
}

/** The words of a processor reading: a level and a percent (Flutter l10n `runningPhoneCpu(level, percent)`). */
public data class CpuWords(val level: CpuLevel, val percent: String)

/** "Busy · 45%"; nothing measured is "0%", never "0.0%". */
public fun runningCpuWords(percent: Double): CpuWords =
    CpuWords(runningCpuLevel(percent), if (percent < 0.05) "0" else formatCpuPercent(percent))

/** This phone's Running on this phone line, the most pressing first (Flutter `runningRowStatus`). */
public sealed interface RunningRowStatus {
    /** l10n `runningPhoneRowLeftBehind(count)`: "6 left behind, still using the phone". */
    public data class LeftBehind(val count: Int) : RunningRowStatus

    /** l10n `runningPhoneRowHeavy(count, name, percent)`: "Flutter test is heavy · 96%". */
    public data class Heavy(val count: Int, val name: RunningProcessName, val percent: String) : RunningRowStatus

    /** l10n `runningPhoneRowNearLimit(count, limit)`: "29 of 32: Android may stop the servers". */
    public data class NearLimit(val count: Int, val limit: Int) : RunningRowStatus

    /** l10n `runningPhoneRowNormal(count, memory)`: "12 running · 1.2 GB". */
    public data class Normal(val count: Int, val rssBytes: Long) : RunningRowStatus
}

/** The row's line for [reading]; null when the reading holds nothing (the row then says `runningPhoneRowIdle`). */
public fun runningRowStatus(reading: RunningReading): RunningRowStatus? {
    val left = reading.leftBehind
    if (left.isNotEmpty()) return RunningRowStatus.LeftBehind(left.size)
    val heavy = reading.heavy
    if (heavy.isNotEmpty()) {
        val top = heavy.first().process
        return RunningRowStatus.Heavy(heavy.size, runningProcessName(top), formatCpuPercent(top.cpuPercent))
    }
    if (reading.nearLimit) return RunningRowStatus.NearLimit(reading.childCount, PhoneProcessWatch.ANDROID_LIMIT)
    val count = reading.snapshot.processes.size
    if (count == 0) return null
    return RunningRowStatus.Normal(count, reading.rssBytes)
}
