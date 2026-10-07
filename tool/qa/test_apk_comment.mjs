import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
// Execute the actual inline workflow code with fake APIs: never call GitHub.
const source = readFileSync('.github/workflows/android-apk-comment.yml', 'utf8');
const script = source.split('          script: |\n')[1].split('\n').map(line => line.startsWith('            ') ? line.slice(12) : line).join('\n');
const sha = 'a'.repeat(40);
async function run({expired=false, stale=false, fork=false, prior=false, artifacts=1,mergeHead=false}={}) {
  const writes=[];
  const calls={artifacts(){},prs(){},comments(){}};
  const github={rest:{actions:{listWorkflowRunArtifacts:calls.artifacts}, repos:{listPullRequestsAssociatedWithCommit:calls.prs}, issues:{listComments:calls.comments, createComment:async x=>writes.push(x), updateComment:async x=>writes.push(x)}}};
  github.paginate=async fn=>fn===calls.artifacts ? Array.from({length:artifacts}, (_,i)=>({id:100+i,expired,name:`opencode-mobile-ci-test-signed-${sha}`})) : fn===calls.prs ? [{number:7,state:'open',head:{sha:stale?'b'.repeat(40):sha,repo:{id:fork?2:1}},base:{repo:{id:1}}}] : prior ? [{id:9,user:{login:'github-actions[bot]'},body:'<!-- oc-android-test-apk -->\nold'}] : [];
  const context={payload:{workflow_run:{id:42,head_sha:mergeHead?'c'.repeat(40):sha,pull_requests:mergeHead?[{number:7,head:{sha}}]:[],repository:{id:1},conclusion:'failure'}},repo:{owner:'owner',repo:'repo'},serverUrl:'https://github.com'};
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  await new AsyncFunction('github','context','core',script)(github,context,{info(){}});
  return writes;
}
test('links exact artifact without claiming failed quality gate passed',async()=>{const [x]=await run();assert.match(x.body,/runs\/42\/artifacts\/100/);assert.match(x.body,/Quality run: failure/);});
test('fork PR receives link without executing fork code',async()=>{assert.equal((await run({fork:true})).length,1);});
test('stale candidate receives no comment',async()=>{assert.equal((await run({stale:true})).length,0);});
test('expired, absent or ambiguous artifacts are refused',async()=>{for(const opts of [{expired:true},{artifacts:0},{artifacts:2}])assert.equal((await run(opts)).length,0);});
test('existing bot comment is updated',async()=>{assert.equal((await run({prior:true}))[0].comment_id,9);});

test('recorded PR head handles a run identified by merge SHA',async()=>{const [x]=await run({mergeHead:true});assert.ok(x);assert.match(x.body,new RegExp(sha));assert.equal((await run({mergeHead:true,stale:true})).length,0);});
