#!/bin/sh
# helper: ui.sh dump -> prints clickable/text nodes; ui.sh shot name
A=$HOME/Android/Sdk/platform-tools/adb; E="$A -s emulator-5554"
case $1 in
dump) $E shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $E exec-out cat /sdcard/u.xml | python3 -I -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    g=lambda k:(re.search(k+r'=\"([^\"]*)\"',n) or [0,''])[1]; t=g(' text'); d=g('content-desc'); b=g('bounds')
    if t or d: print(b,'|',(t or d)[:100].replace('\n',' / '))
";;
shot) $E exec-out screencap -p > "$2";;
tap) $E shell input tap $2 $3;;
esac
