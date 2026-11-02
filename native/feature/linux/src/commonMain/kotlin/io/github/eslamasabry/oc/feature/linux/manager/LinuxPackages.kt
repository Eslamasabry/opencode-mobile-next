package io.github.eslamasabry.oc.feature.linux.manager

/*
 * Ubuntu packages in the app's own Linux (port of lib/builtin/linux_manager/linux_packages.dart, B-LINUX-090/091,
 * B-SEC-096): the installed list, what an install or removal would do, and the long jobs as scripts that report
 * progress with `::oc` lines. Names are validated against Debian's rules before they reach a script, so typed text
 * never becomes shell.
 */

/** One installed package. */
public data class LinuxPackage(
    val name: String,
    val version: String,
    /** Installed size as dpkg reports it, in kilobytes. */
    val sizeKb: Long,
    val essential: Boolean = false,
    val required: Boolean = false,
) {
    /** Removal is refused for these and for [LinuxPackages.kept]: Linux or the app's tools stop working without them. */
    val locked: Boolean get() = essential || required || name in LinuxPackages.kept
}

/** Whether apt can install a package, from `::cand` lines. */
public data class LinuxPackageCandidate(val name: String, val version: String?, val installed: Boolean) {
    val available: Boolean get() = version != null
}

/** What a long job does: apt work, or a backup or restore of Linux. */
public enum class LinuxJobKind(public val wire: String) {
    Install("install"), Remove("remove"), Update("update"), Backup("backup"), Restore("restore");

    public companion object {
        public fun of(wire: String): LinuxJobKind? = entries.firstOrNull { it.wire == wire }
    }
}

public object LinuxPackages {
    /** The tools phone setup installs with apt and relies on. They leave through This phone, not from here. */
    public val kept: Set<String> =
        setOf(
            "curl", "ca-certificates", "git", "openssh-client", "python3", "python3-venv", "python3-pip", "bash", "dash",
            "coreutils", "apt", "dpkg", "procps",
        )

    /** The query that lists installed packages, one `::pkg` line each. */
    public const val LIST_SCRIPT: String = $$"""# oc-linux-packages
dpkg-query -W -f='::pkg ${Package}\t${Version}\t${Installed-Size}\t${Essential}\t${Priority}\t${db:Status-Abbrev}\n' 2>/dev/null
exit 0
"""

    private val packageName = Regex("^[a-z0-9][a-z0-9+.-]+$")

    /** Debian package names: lower case letters, digits and `+ - .`, two or more, starting with a letter or digit. */
    public fun isValidName(name: String): Boolean = name.length <= 100 && packageName.matches(name)

    /** Names typed by a person, separated by spaces or commas. Null when any is invalid, none, or more than 10. */
    public fun parseTyped(text: String): List<String>? {
        val names = text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty() || names.size > 10) return null
        return if (names.all(::isValidName)) names else null
    }

    /** Installed packages, sorted by name. Half-installed ones are left out. */
    public fun parseInstalled(output: String): List<LinuxPackage> =
        output.split('\n')
            .asSequence()
            .filter { it.startsWith("::pkg ") }
            .mapNotNull { line ->
                val f = line.substring(6).split('\t')
                if (f.size < 6 || !f[5].trim().startsWith("ii")) return@mapNotNull null
                val name = f[0].trim()
                if (!isValidName(name)) return@mapNotNull null
                LinuxPackage(
                    name = name,
                    version = f[1].trim(),
                    sizeKb = f[2].trim().toLongOrNull() ?: 0L,
                    essential = f[3].trim().lowercase() == "yes",
                    required = f[4].trim().lowercase() == "required",
                )
            }
            .sortedBy { it.name }
            .toList()

    /** What apt knows about each name, read without changing anything. */
    public fun checkScript(names: List<String>): String {
        requireNames(names)
        return $$"""# oc-linux-package-check
for p in $${names.joinToString(" ")}; do
  c=$(apt-cache policy "$p" 2>/dev/null | sed -n 's/^  Candidate: //p')
  i=$(dpkg-query -W -f='${db:Status-Abbrev}' "$p" 2>/dev/null)
  printf '::cand %s\t%s\t%s\n' "$p" "$c" "$i"
done
exit 0
"""
    }

    public fun parseCandidates(output: String): List<LinuxPackageCandidate> =
        output.split('\n').filter { it.startsWith("::cand ") }.mapNotNull { line ->
            val f = line.substring(7).split('\t')
            if (f.size < 3) return@mapNotNull null
            val version = f[1].trim()
            LinuxPackageCandidate(
                name = f[0].trim(),
                version = version.takeUnless { it.isEmpty() || it == "(none)" },
                installed = f[2].trim().startsWith("ii"),
            )
        }

    /** Packages apt would remove along with [names], without removing anything. */
    public fun removePlanScript(names: List<String>): String {
        requireNames(names)
        return $$"""# oc-linux-remove-plan
DEBIAN_FRONTEND=noninteractive apt-get -s remove $${names.joinToString(" ")} 2>&1 | sed -n 's/^Remv \([^ ]*\).*/::remv \1/p'
exit 0
"""
    }

    public fun parseRemovePlan(output: String): List<String> =
        output.split('\n')
            .filter { it.startsWith("::remv ") }
            .map { it.substring(7).trim() }
            .filter(::isValidName)

    /** How a job's script ends: the last line it prints. */
    public const val JOB_EXIT_PREFIX: String = "::oc job-exit "

    /** How long a job may run before it stops itself (Android 15 caps data-sync services; never unbounded). */
    public const val JOB_BUDGET_SECONDS: Int = 45 * 60

    /** The script of an apt job: names itself, takes the job lock, stops after the budget, ends with its exit line. */
    public fun jobScript(kind: LinuxJobKind, names: List<String> = emptyList()): String {
        if (kind == LinuxJobKind.Install || kind == LinuxJobKind.Remove) requireNames(names)
        val list = names.joinToString(", ")
        val args = names.joinToString(" ")
        val body =
            when (kind) {
                LinuxJobKind.Backup, LinuxJobKind.Restore ->
                    throw IllegalArgumentException("A backup or restore brings its own body: jobFrame.")
                LinuxJobKind.Install -> "oc_stage \"Installing $list\"\noc_apt_install $args\n"
                LinuxJobKind.Remove -> "oc_apt_repair || exit \$?\noc_stage \"Removing $list\"\noc_apt_run \"\" remove -y $args\n"
                LinuxJobKind.Update ->
                    "oc_apt_repair || exit \$?\n" +
                        "oc_apt_update || exit \$?\n" +
                        "oc_stage \"Updating packages\"\n" +
                        "oc_apt_run \"\" upgrade -y --no-install-recommends -o Dpkg::Options::=--force-confold\n"
            }
        return jobFrame(kind, body, names)
    }

    /** The frame every long job runs in. [body] runs after the setup prelude (`oc_stage`, `oc_apt_*` exist). */
    public fun jobFrame(kind: LinuxJobKind, body: String, names: List<String> = emptyList()): String {
        val ticks = JOB_BUDGET_SECONDS / 5
        return SETUP_PRELUDE + $$$"""export DEBIAN_FRONTEND=noninteractive
printf '::oc job $$${kind.wire}\n'
printf '::oc names %s\n' '$$${names.joinToString(" ")}'
mkdir -p /root/.oc-builtin
exec 9> /root/.oc-builtin/linux-job.lock
if command -v flock >/dev/null 2>&1 && ! flock -n 9; then
  printf '::oc job-exit 75\n'
  exit 75
fi
oc_main=$$
oc_done=/root/.oc-builtin/linux-job.done
rm -f "$oc_done"
(
  oc_i=0
  while [ ! -e "$oc_done" ] && [ $oc_i -lt $$$ticks ]; do
    sleep 5
    oc_i=$((oc_i + 1))
  done
  [ -e "$oc_done" ] && exit 0
  printf '::oc job-exit 124\n'
  kill -TERM $oc_main
) &
(
$$$body)
oc_rc=$?
touch "$oc_done"
printf '::oc job-exit %s\n' "$oc_rc"
exit 0
"""
    }

    private fun requireNames(names: List<String>) {
        require(names.isNotEmpty() && names.all(::isValidName)) { "Not package names." }
    }
}
