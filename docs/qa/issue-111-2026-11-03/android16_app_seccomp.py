#!/usr/bin/env python3
"""Issue #111 evidence tool, x86_64 host only, NOT shipped.

Runs argv under an emulation of Android 16's app seccomp policy: every
syscall in bionic android16-release SYSCALLS.TXT + SECCOMP_ALLOWLIST_COMMON
+ SECCOMP_ALLOWLIST_APP minus the blocklists is allowed; anything else gets
SECCOMP_RET_TRAP (SIGSYS), as bionic's genseccomp policy does for apps.
A few glibc start-up/libc-choice syscalls that bionic never makes are added
as a host shim (see `allow|=`); none of them is a call the probe tests.
Set BIONIC_LIBC_DIR to the downloaded libc/ files.
"""
# Emulates bionic android16-release app seccomp policy (x86_64) and runs argv under it.
import ctypes, os, re, sys, struct
import pathlib
# Directory holding bionic android16-release libc/SECCOMP_*.TXT and SYSCALLS.TXT.
A=os.environ.get('BIONIC_LIBC_DIR','/var/tmp/oc111/a16')+'/'
nums={}
for line in open('/usr/include/x86_64-linux-gnu/asm/unistd_64.h'):
    m=re.match(r'#define __NR_(\w+)\s+(\d+)',line)
    if m: nums[m.group(1)]=int(m.group(2))
def names(f, arch='x86_64'):
    out=set()
    for line in open(A+f):
        line=line.split('#')[0].strip()
        if not line: continue
        m=re.match(r'^(?:[\w\s\*]+?\s+)?([\w:|]+)\s*\((.*)\)\s+([\w,]+)\s*$',line)
        if not m: continue
        spec,archs=m.group(1),m.group(3).split(',')
        if not ({'all','lp64',arch} & set(archs)): continue
        call=spec.split(':')[-1]
        for n in call.split('|'): out.add(n.strip())
    return out
allow=(names('SYSCALLS.TXT')|names('SECCOMP_ALLOWLIST_COMMON.TXT')|names('SECCOMP_ALLOWLIST_APP.TXT'))-names('SECCOMP_BLOCKLIST_APP.TXT')-names('SECCOMP_BLOCKLIST_COMMON.TXT')
allow|={'rseq','set_robust_list','faccessat2','poll','openat2','fchmodat2','access','rename'}  # glibc host-start shim only (bionic never calls these)
nrs=sorted({nums[n] for n in allow if n in nums})
if '--list' in sys.argv:
    for n in ['io_uring_setup','landlock_create_ruleset','landlock_add_rule','landlock_restrict_self','process_vm_readv','setns','unshare','ptrace','truncate','renameat','clone3','rseq','statx','getrandom','close_range','faccessat2','prlimit64','set_robust_list','pidfd_open','epoll_pwait2','openat2','mremap','sched_getaffinity','sigaltstack','poll','fchmodat2']:
        print(n, n in allow); sys.exit(0)
prog=[(0x20,0,0,4),(0x15,1,0,0xc000003e),(0x06,0,0,0),(0x20,0,0,0)]
for nr in nrs: prog+= [(0x15,0,1,nr),(0x06,0,0,0x7fff0000)]
prog.append((0x06,0,0,0x00030000))  # SECCOMP_RET_TRAP
buf=b''.join(struct.pack('HBBI',*p) for p in prog)
cbuf=ctypes.create_string_buffer(buf)
class F(ctypes.Structure): _fields_=[('len',ctypes.c_ushort),('filter',ctypes.c_void_p)]
fp=F(len(prog),ctypes.addressof(cbuf))
libc=ctypes.CDLL(None,use_errno=True)
assert libc.prctl(38,1,0,0,0)==0
assert libc.prctl(22,2,ctypes.byref(fp),0,0)==0, ctypes.get_errno()
os.execv(sys.argv[1],sys.argv[1:])
