from pathlib import Path
import subprocess, os, sys
root = Path(__file__).resolve().parents[2] if '/tool/qa/' in str(Path(__file__).resolve()) else Path('/home/eslam/Storage/Code/oc_app-sol-bb')
main = root/'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile'
test = root/'android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile'
junit='/home/eslam/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar'
hamcrest='/home/eslam/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar'
jar='/home/eslam/Storage/tmp/oc-bb3-pure-tests.jar'
runner=Path('/home/eslam/Storage/tmp/OcBb3PureRunner.kt')
runner.write_text('''import org.junit.runner.JUnitCore
import org.junit.runner.Request
fun main(args: Array<String>) {
 val result = JUnitCore().run(Request.method(Class.forName("io.github.eslamasabry.opencode_mobile." + args[0]), args[1]))
 println("Exact test: " + args[1] + ": " + result.runCount + " run, " + result.failureCount + " failures")
 result.failures.forEach { println(it.toString()) }
 if (!result.wasSuccessful()) kotlin.system.exitProcess(1)
}
''')
files=[main/(x+'.kt') for x in ['NativeRuntimeOwnership','NativeServerRecipe','RestartBackoff']]+[test/(x+'Test.kt') for x in ['NativeRuntimeOwnership','NativeServerRecipe','RestartBackoff']]+[runner]
lock=[str(root/'tool/qa/machine_lock.sh'),'build','--']
compilecmd=lock+['/home/eslam/.sdkman/candidates/kotlin/current/bin/kotlinc','-J-Xmx256m','-J-XX:MaxMetaspaceSize=192m','-cp',junit]+list(map(str,files))+['-d',jar]
runcmd=lock+['/home/eslam/.sdkman/candidates/kotlin/current/bin/kotlin','-J-Xmx128m','-cp',jar+':'+junit+':'+hamcrest]
def compile():
 subprocess.run(compilecmd,cwd=root,check=True)
def green():
 compile()
 result=subprocess.run(runcmd+['org.junit.runner.JUnitCore']+['io.github.eslamasabry.opencode_mobile.'+x+'Test' for x in ['NativeRuntimeOwnership','NativeServerRecipe','RestartBackoff']],cwd=root,capture_output=True,text=True)
 print(result.stdout,flush=True)
 with (root/'docs/qa/BB3-2026-10-07/jvm-green.txt').open('a') as log:
  log.write('Current pure helper checkpoint exit='+str(result.returncode)+'\n'+result.stdout)
 result.check_returncode()
green()
mutations=[
 ('NativeRuntimeOwnership.kt','receiptGeneration <= processBirthGeneration','true','NativeRuntimeOwnershipTest','denialDrainsOnlyPriorProcessReceiptAndNeverANewerManualStart'),
 ('NativeRuntimeOwnership.kt','require(byPid[leader.pid] == null || leader.sameProcess(byPid[leader.pid])) { "ownershipUnknown" }','Unit','NativeRuntimeOwnershipTest','mismatchedLeaderIdentityRefusesSignalsEvenWhenNonceStillMatches'),
 ('NativeRuntimeOwnership.kt','require(byPid[root.pid] == null || root.sameProcess(byPid[root.pid])) { "ownershipUnknown" }','Unit','NativeRuntimeOwnershipTest','mismatchedRootIdentityRefusesSignalsBeforeSelectingAnyChildren'),
 ('NativeRuntimeOwnership.kt','expectedRevision == currentRevision','true','NativeRuntimeOwnershipTest','staleStopCannotCommitOrDrainTheReplacementManualGeneration'),
 ('NativeRuntimeOwnership.kt','= recipeValid && identityCommitted && admitted &&','= identityCommitted && admitted &&','NativeRuntimeOwnershipTest','stickyRequiresValidDurableRecipeOwnershipAndBudget'),
 ('NativeRuntimeOwnership.kt','other.startTicks == startTicks','true','NativeRuntimeOwnershipTest','exactKernelIdentityRejectsPidReuse'),
 ('NativeRuntimeOwnership.kt','require(!requireCompleteInventory || current.all { it.pid in registered || it.pid in server || it.pid in knownOther }) { "ownershipUnknown" }','Unit','NativeRuntimeOwnershipTest','unknownSameUidProcessCannotBeKilled'),
 ('NativeRuntimeOwnership.kt','requestProfile == owner','true','NativeRuntimeOwnershipTest','manualUnboundStartDoesNotArmColdRestoration'),
 ('NativeRuntimeOwnership.kt','= !released && drained && wanted','= drained && wanted','NativeRuntimeOwnershipTest','unsupportedGateAllowsOnlyAnUnreleasedDrainedWantedManualFallback'),
 ('NativeServerRecipe.kt','require(request.keys == REQUEST_KEYS) { SAFE_ERROR }','Unit','NativeServerRecipeTest','arbitraryLaunchAndCredentialPayloadsAreRejectedWithSafeErrors'),
 ('NativeRuntimeOwnership.kt','check(identityDurable && !released) { "ownershipUnknown" }','Unit','NativeRuntimeOwnershipTest','permitCannotBeWrittenBeforeDurableIdentityCommit'),
 ('NativeServerRecipe.kt','require(integer(request["version"]) == 1L) { SAFE_ERROR }','Unit','NativeServerRecipeTest','schemaVersionRequiresIntOrLongOne'),
 ('RestartBackoff.kt','nextDelayMs = nextDelay; restartAtMs = deadline','nextDelayMs = 1000L; restartAtMs = deadline','RestartBackoffTest','snapshotRestoresTheSameDeadlineAndNextDelay'),
]
for filename,before,after,klass,method in mutations:
 if len(sys.argv)>1 and method not in sys.argv[1:]: continue
 p=main/filename; original=p.read_text()
 assert before in original
 print('RED mutation '+method,flush=True)
 try:
  p.write_text(original.replace(before,after,1)); compile()
  result=subprocess.run(runcmd+['OcBb3PureRunnerKt',klass,method],cwd=root,capture_output=True,text=True)
  print(result.stdout,flush=True)
  with (root/'docs/qa/BB3-2026-10-07/jvm-red.txt').open('a') as log:
   log.write('Mutation '+method+' exit='+str(result.returncode)+'\n'+result.stdout)
  if result.returncode != 1: raise RuntimeError('Expected exact test failure: '+method)
 finally: p.write_text(original)
print('Restored final green checkpoint',flush=True)
green()
