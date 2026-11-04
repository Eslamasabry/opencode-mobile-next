package io.github.eslamasabry.oc.engine.runtime.services.kept

/** The frozen Flutter oc-svd v2 supervisor; kept services retain their on/off files. */
public object KeptServiceSupervisor {
    public const val NATIVE_NAME: String = "svc"
    public const val HOME: String = "/root/.oc-builtin/svc"
    public const val LOGS: String = "/var/log/oc-svc"
    public val script: String =
        """
        #!/bin/bash
        # oc-svd v2: the app's service supervisor (lib/builtin/linux_services/oc_svd_script.dart).
        # Files: /root/.oc-builtin/svc/{defs,want,kick,state,hold,keep}; logs in /var/log/oc-svc. Do not edit: the app rewrites it.
        set -m
        set +e
        SVC='/root/.oc-builtin/svc'
        LOGS='/var/log/oc-svc'
        TICK=2 STABLE=30 MAXDELAY=60 GIVEUP=5
        CAP=1048576 SIZE_EVERY=15 GAP=1
        mkdir -p "${'$'}SVC/defs" "${'$'}SVC/want" "${'$'}SVC/kick" "${'$'}SVC/state" "${'$'}LOGS" || exit 70
        exec >>"${'$'}LOGS/_svd.log" 2>&1 </dev/null
        rm -f "${'$'}SVC/.tick" "${'$'}SVC/keep"
        mkfifo "${'$'}SVC/.tick" || exit 71
        exec 3<>"${'$'}SVC/.tick"
        declare -A PID SINCE DELAY NEXT FAILS RESTARTS KICK PHASE LEXIT LEXITAT STOPBY AFTER WRITTEN
        HELD=0
        NAMEPAT='^[a-z][a-z0-9-]{1,23}${'$'}'

        now() { printf -v NOW '%(%s)T' -1; }
        say() { now; printf '%s %s\n' "${'$'}NOW" "${'$'}*"; }

        read_def() {
          D_command= D_dir=/root D_port= D_restart=on-failure D_stop=10 D_env=()
          local f="${'$'}SVC/defs/${'$'}1.svc" line k v
          [[ -f ${'$'}f ]] || return 1
          while IFS= read -r line || [[ -n ${'$'}line ]]; do
            [[ ${'$'}line == \#* || ${'$'}line != *=* ]] && continue
            k=${'$'}{line%%=*} v=${'$'}{line#*=}
            case ${'$'}k in
              command) D_command=${'$'}v ;;
              dir) D_dir=${'$'}v ;;
              port) D_port=${'$'}v ;;
              restart) D_restart=${'$'}v ;;
              stop_timeout) D_stop=${'$'}v ;;
              env.*) k=${'$'}{k#env.}; [[ ${'$'}k =~ ^[A-Za-z_][A-Za-z0-9_]*${'$'} ]] && D_env+=("${'$'}k=${'$'}v") ;;
            esac
          done <"${'$'}f"
          [[ ${'$'}D_port =~ ^[0-9]+${'$'} ]] || D_port=
          [[ ${'$'}D_stop =~ ^[0-9]+${'$'} ]] || D_stop=10
          [[ -n ${'$'}D_command ]]
        }

        want_of() {
          W=off
          [[ ${'$'}HELD == 1 ]] && return
          [[ -f ${'$'}SVC/want/${'$'}1 ]] && read -r W <"${'$'}SVC/want/${'$'}1"
          [[ ${'$'}W == on ]] || W=off
        }

        kick_of() {
          K=
          [[ -f ${'$'}SVC/kick/${'$'}1 ]] && read -r K <"${'$'}SVC/kick/${'$'}1"
        }

        put_state() {
          local n=${'$'}1 s
          s="phase=${'$'}{PHASE[${'$'}n]:-off}
        pid=${'$'}{PID[${'$'}n]}
        since=${'$'}{SINCE[${'$'}n]}
        restarts=${'$'}{RESTARTS[${'$'}n]:-0}
        last_exit=${'$'}{LEXIT[${'$'}n]}
        last_exit_at=${'$'}{LEXITAT[${'$'}n]}
        next_try=${'$'}{NEXT[${'$'}n]}
        fails=${'$'}{FAILS[${'$'}n]:-0}
        held=${'$'}HELD"
          [[ ${'$'}{WRITTEN[${'$'}n]} == "${'$'}s" ]] && return
          printf '%s\n' "${'$'}s" >"${'$'}SVC/state/${'$'}n" && WRITTEN[${'$'}n]=${'$'}s
        }

        mark() { printf '::svc %s\n' "${'$'}2" >>"${'$'}LOGS/${'$'}1.log"; }

        start_svc() {
          local n=${'$'}1 e
          now
          if ! read_def "${'$'}n"; then
            PHASE[${'$'}n]=failed; put_state "${'$'}n"; return
          fi
          if [[ -n ${'$'}D_port ]] && ( : <>"/dev/tcp/127.0.0.1/${'$'}D_port" ) 2>/dev/null; then
            local d=${'$'}{DELAY[${'$'}n]:-1}
            PHASE[${'$'}n]=port_busy NEXT[${'$'}n]=${'$'}((NOW + d))
            DELAY[${'$'}n]=${'$'}((d * 2 > MAXDELAY ? MAXDELAY : d * 2))
            mark "${'$'}n" "port-busy ${'$'}D_port"
            put_state "${'$'}n"; return
          fi
          mark "${'$'}n" "start ${'$'}NOW ${'$'}{RESTARTS[${'$'}n]:-0}"
          (
            cd "${'$'}D_dir" 2>/dev/null || cd /root 2>/dev/null || cd /
            for e in "${'$'}{D_env[@]}"; do export "${'$'}e"; done
            exec bash -c "${'$'}D_command"
          ) >>"${'$'}LOGS/${'$'}n.log" 2>&1 </dev/null &
          PID[${'$'}n]=${'$'}! SINCE[${'$'}n]=${'$'}NOW PHASE[${'$'}n]=running NEXT[${'$'}n]= STOPBY[${'$'}n]= AFTER[${'$'}n]=
          say "started ${'$'}n pid ${'$'}{PID[${'$'}n]}"
          put_state "${'$'}n"
          read -r -t "${'$'}GAP" -u 3 _
        }

        begin_stop() {
          local n=${'$'}1 p=${'$'}{PID[${'$'}n]}
          [[ -n ${'$'}p ]] || return
          [[ -n ${'$'}{STOPBY[${'$'}n]} ]] && return
          read_def "${'$'}n" || D_stop=10
          now
          kill -TERM -- "-${'$'}p" 2>/dev/null || kill -TERM "${'$'}p" 2>/dev/null
          STOPBY[${'$'}n]=${'$'}((NOW + D_stop)) PHASE[${'$'}n]=stopping
          mark "${'$'}n" "stop"
          put_state "${'$'}n"
        }

        reap() {
          local n=${'$'}1 p=${'$'}{PID[${'$'}n]} code up d
          [[ -n ${'$'}p ]] || return 1
          kill -0 "${'$'}p" 2>/dev/null && return 1
          wait "${'$'}p"; code=${'$'}?
          kill -KILL -- "-${'$'}p" 2>/dev/null
          now
          up=${'$'}((NOW - ${'$'}{SINCE[${'$'}n]:-${'$'}NOW}))
          PID[${'$'}n]= LEXIT[${'$'}n]=${'$'}code LEXITAT[${'$'}n]=${'$'}NOW
          mark "${'$'}n" "exit ${'$'}code ${'$'}up"
          say "${'$'}n exited ${'$'}code after ${'$'}up s"
          if [[ -n ${'$'}{STOPBY[${'$'}n]} ]]; then
            STOPBY[${'$'}n]= PHASE[${'$'}n]=off NEXT[${'$'}n]=
            if [[ ${'$'}{AFTER[${'$'}n]} == start ]]; then
              AFTER[${'$'}n]= FAILS[${'$'}n]=0 DELAY[${'$'}n]=1
              start_svc "${'$'}n"
            fi
            put_state "${'$'}n"; return 0
          fi
          read_def "${'$'}n" || D_restart=never
          if ((up >= STABLE)); then FAILS[${'$'}n]=0 DELAY[${'$'}n]=1; else FAILS[${'$'}n]=${'$'}((${'$'}{FAILS[${'$'}n]:-0} + 1)); fi
          if [[ ${'$'}D_restart == never ]] || { [[ ${'$'}D_restart == on-failure ]] && ((code == 0)); }; then
            PHASE[${'$'}n]=exited NEXT[${'$'}n]=
            put_state "${'$'}n"; return 0
          fi
          if ((${'$'}{FAILS[${'$'}n]:-0} >= GIVEUP)); then
            PHASE[${'$'}n]=failed NEXT[${'$'}n]=
            mark "${'$'}n" "gave-up"
            put_state "${'$'}n"; return 0
          fi
          d=${'$'}{DELAY[${'$'}n]:-1}
          PHASE[${'$'}n]=backoff NEXT[${'$'}n]=${'$'}((NOW + d)) RESTARTS[${'$'}n]=${'$'}((${'$'}{RESTARTS[${'$'}n]:-0} + 1))
          DELAY[${'$'}n]=${'$'}((d * 2 > MAXDELAY ? MAXDELAY : d * 2))
          mark "${'$'}n" "backoff ${'$'}d"
          put_state "${'$'}n"
          return 0
        }

        forget() {
          local n=${'$'}1
          unset "PID[${'$'}n]" "SINCE[${'$'}n]" "DELAY[${'$'}n]" "NEXT[${'$'}n]" "FAILS[${'$'}n]" "RESTARTS[${'$'}n]" \
            "KICK[${'$'}n]" "PHASE[${'$'}n]" "LEXIT[${'$'}n]" "LEXITAT[${'$'}n]" "STOPBY[${'$'}n]" "AFTER[${'$'}n]" "WRITTEN[${'$'}n]"
        }

        reconcile() {
          local n=${'$'}1 defined=${'$'}2 kicked=0
          reap "${'$'}n"
          if [[ -n ${'$'}{STOPBY[${'$'}n]} && -n ${'$'}{PID[${'$'}n]} ]]; then
            now
            ((NOW >= STOPBY[${'$'}n])) && kill -KILL -- "-${'$'}{PID[${'$'}n]}" 2>/dev/null
          fi
          if [[ ${'$'}defined != 1 ]]; then
            if [[ -n ${'$'}{PID[${'$'}n]} ]]; then AFTER[${'$'}n]=; begin_stop "${'$'}n"; else forget "${'$'}n"; fi
            return
          fi
          kick_of "${'$'}n"
          if [[ -z ${'$'}{KICK[${'$'}n]+x} ]]; then
            KICK[${'$'}n]=${'$'}K
          elif [[ ${'$'}K != "${'$'}{KICK[${'$'}n]}" ]]; then
            KICK[${'$'}n]=${'$'}K kicked=1
          fi
          want_of "${'$'}n"
          if [[ ${'$'}W == off ]]; then
            AFTER[${'$'}n]=
            if [[ -n ${'$'}{PID[${'$'}n]} ]]; then
              begin_stop "${'$'}n"
            else
              PHASE[${'$'}n]=off NEXT[${'$'}n]= FAILS[${'$'}n]=0 DELAY[${'$'}n]=1
              put_state "${'$'}n"
            fi
            return
          fi
          if ((kicked)); then
            if [[ -n ${'$'}{PID[${'$'}n]} ]]; then
              AFTER[${'$'}n]=start
              begin_stop "${'$'}n"
            else
              FAILS[${'$'}n]=0 DELAY[${'$'}n]=1
              start_svc "${'$'}n"
            fi
            return
          fi
          [[ -n ${'$'}{PID[${'$'}n]} ]] && return
          case ${'$'}{PHASE[${'$'}n]} in
            failed|exited) put_state "${'$'}n" ;;
            backoff|port_busy)
              now
              if ((NOW >= ${'$'}{NEXT[${'$'}n]:-0})); then start_svc "${'$'}n"; else put_state "${'$'}n"; fi ;;
            *) FAILS[${'$'}n]=0 DELAY[${'$'}n]=1; start_svc "${'$'}n" ;;
          esac
        }

        rotate() {
          local f size
          for f in "${'$'}LOGS"/*.log; do
            [[ -f ${'$'}f ]] || continue
            size=${'$'}(wc -c <"${'$'}f" 2>/dev/null) || continue
            if ((size > CAP)); then
              cp -f "${'$'}f" "${'$'}f.1" 2>/dev/null && : >"${'$'}f"
            fi
          done
        }

        shutdown() {
          local n p f w
          say "stopping all"
          # Stop is Stop (owner, 2026-10-31): a SIGTERM is the notification's Stop
          # (or Reset, which erases these files anyway), so every service the
          # person kept on is turned off, the same file a person's Stop writes, and
          # stays off when the app opens again. Only the app's own restart of the
          # supervisor (a newer script) leaves the marker `keep` first. A SIGKILL
          # (Android ending the app) never gets here: those services come back.
          if [[ -f ${'$'}SVC/keep ]]; then
            rm -f "${'$'}SVC/keep"
            say "restart: choices kept"
          else
            for f in "${'$'}SVC"/want/*; do
              [[ -f ${'$'}f ]] || continue
              read -r w <"${'$'}f"
              [[ ${'$'}w == on ]] || continue
              printf 'off\n' >"${'$'}f"
              say "turned off ${'$'}{f##*/}"
            done
          fi
          local pids=() dog
          for n in "${'$'}{!PID[@]}"; do
            p=${'$'}{PID[${'$'}n]}
            [[ -n ${'$'}p ]] || continue
            kill -TERM -- "-${'$'}p" 2>/dev/null || kill -TERM "${'$'}p" 2>/dev/null
            pids+=("${'$'}p")
          done
          if ((${'$'}{#pids[@]})); then
            # A watchdog ends what ignores SIGTERM; `wait` (not kill -0, which a
            # zombie passes) says when each has gone.
            ( read -r -t 2 -u 3 _; for p in "${'$'}{pids[@]}"; do kill -KILL -- "-${'$'}p" 2>/dev/null; done ) &
            dog=${'$'}!
            for p in "${'$'}{pids[@]}"; do wait "${'$'}p" 2>/dev/null; done
            kill "${'$'}dog" 2>/dev/null
          fi
          for n in "${'$'}{!PID[@]}"; do
            p=${'$'}{PID[${'$'}n]}
            if [[ -n ${'$'}p ]]; then
              kill -KILL -- "-${'$'}p" 2>/dev/null
              mark "${'$'}n" "stop"
            fi
            PID[${'$'}n]= PHASE[${'$'}n]=off
            put_state "${'$'}n"
          done
          say "stopped"
          exit 0
        }
        trap shutdown TERM INT HUP

        say "oc-svd v2 started"
        ticks=0
        while :; do
          if [[ -f ${'$'}SVC/hold ]]; then HELD=1; else HELD=0; fi
          declare -A SEEN=()
          for f in "${'$'}SVC"/defs/*.svc; do
            [[ -f ${'$'}f ]] || continue
            n=${'$'}{f##*/} n=${'$'}{n%.svc}
            [[ ${'$'}n =~ ${'$'}NAMEPAT ]] || continue
            SEEN[${'$'}n]=1
            reconcile "${'$'}n" 1
          done
          for n in "${'$'}{!PID[@]}" "${'$'}{!PHASE[@]}"; do
            [[ -n ${'$'}{SEEN[${'$'}n]} ]] && continue
            SEEN[${'$'}n]=1
            reconcile "${'$'}n" 0
          done
          unset SEEN
          ticks=${'$'}((ticks + 1))
          if ((ticks % SIZE_EVERY == 0)); then rotate; fi
          read -r -t "${'$'}TICK" -u 3 _
        done
        """
            .trimIndent() + "\n"
    public const val LAUNCH: String = "exec /bin/bash '/root/.oc-builtin/svc/oc-svd'\n"
}
