#!/bin/sh
# run.sh <n> : spike run n (1-5), free model big-pickle
. /root/env.sh
n=$1
case $n in
1) topic="O ciclo da água"; min=10; sl=8; aud=class; lang=pt-BR; dir=ltr; slug=o-ciclo-da-agua; line="Faça uma apresentação sobre o ciclo da água, 10 minutos, para uma aula.";;
2) topic="عروض ورشة إصلاح الهواتف"; min=5; sl=5; aud=work; lang=ar; dir=rtl; slug=offers-ar; line="اصنع عرضاً تقديمياً عن عروض ورشة إصلاح الهواتف، ٥ دقائق، للعمل.";;
3) topic="Mis puntos fuertes para un trabajo de ventas"; min=5; sl=5; aud=work; lang=es; dir=ltr; slug=cv-ventas; line="Haz una presentación sobre los puntos destacados de mi CV para un trabajo de ventas, 5 minutos, para el trabajo.";;
4) topic="Introduction to Python"; min=20; sl=12; aud=class; lang=en; dir=ltr; slug=intro-python; line="Make a presentation about an introduction to Python, 20 minutes, for a class.";;
5) topic="Rencana liburan ke Bali"; min=10; sl=8; aud=friends; lang=id; dir=ltr; slug=bali-trip; line="Buat presentasi tentang rencana liburan ke Bali, 10 menit, untuk teman dan keluarga.";;
esac
mkdir -p /root/projects/presentations; cd /root/projects/presentations
rm -rf $slug
brief="$line
\`\`\`oc-brief
{\"schema\":1,\"topic\":\"$topic\",\"minutes\":$min,\"slides\":$sl,\"audience\":\"$aud\",\"lang\":\"$lang\",\"dir\":\"$dir\",\"theme\":\"clean\",\"slug\":\"$slug\",\"attachments\":[]}
\`\`\`"
out=/root/spk/run$n; mkdir -p /root/spk
( while :; do awk '/MemAvailable/{print $2}' /proc/meminfo >> $out.mem; sleep 2; done ) & mp=$!
a=$(date +%s)
opencode2 run --server http://127.0.0.1:4100 --model opencode/big-pickle --auto --format json --title "spike-$n" "$brief" > $out.s1.json 2> $out.s1.err
b=$(date +%s)
echo "stage1_s=$((b-a))" > $out.time
kill $mp

