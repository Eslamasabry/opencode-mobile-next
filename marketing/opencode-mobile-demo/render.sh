#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 RAW_CAPTURE_DIR [OUTPUT_MP4]" >&2
  exit 2
fi

raw_dir=$1
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
output=${2:-"$script_dir/opencode-mobile-outreach.mp4"}
edit_dir=$(mktemp -d)
trap 'rm -rf -- "$edit_dir"' EXIT

font_bold=/usr/share/fonts/opentype/fira/FiraSans-Bold.otf
font_regular=/usr/share/fonts/opentype/fira/FiraSans-Regular.otf
icon="$script_dir/../../android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"

for capture in sessions model artifact terminal; do
  if [[ ! -s "$raw_dir/$capture.mp4" ]]; then
    echo "Missing capture: $raw_dir/$capture.mp4" >&2
    exit 1
  fi
done

render_screen() {
  local input=$1
  local duration=$2
  local caption=$3
  local destination=$4
  ffmpeg -hide_banner -loglevel error -y \
    -i "$input" -t "$duration" \
    -vf "setpts=PTS-STARTPTS,scale=-2:1700:flags=lanczos,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0b1110,drawbox=x=150:y=103:w=780:h=1714:color=0x83d0ad@0.28:t=2,drawtext=fontfile='$font_bold':text='$caption':fontcolor=0xf2f7f4:fontsize=44:x=(w-text_w)/2:y=27,drawbox=x=(w-92)/2:y=88:w=92:h=3:color=0x83d0ad@0.92:t=fill,fps=30,format=yuv420p" \
    -an -c:v libx264 -preset medium -crf 20 -movflags +faststart "$destination"
}

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "color=c=0x0b1110:s=1080x1920:r=30:d=3.4" \
  -loop 1 -i "$icon" -t 3.4 \
  -filter_complex "[1:v]scale=250:250:flags=lanczos[logo];[0:v][logo]overlay=(W-w)/2:335,drawtext=fontfile='$font_bold':text='OpenCode, beyond the desk.':fontcolor=0xf2f7f4:fontsize=65:x=(w-text_w)/2:y=690,drawtext=fontfile='$font_regular':text='A community-built Android companion':fontcolor=0xaab8b1:fontsize=36:x=(w-text_w)/2:y=795,drawbox=x=(w-120)/2:y=880:w=120:h=4:color=0x83d0ad@0.95:t=fill,drawtext=fontfile='$font_bold':text='REAL SERVER  •  REAL SESSIONS  •  REAL TOOLS':fontcolor=0x83d0ad:fontsize=25:x=(w-text_w)/2:y=925,fade=t=in:st=0:d=0.35,fade=t=out:st=3.10:d=0.30,format=yuv420p[out]" \
  -map "[out]" -an -c:v libx264 -preset medium -crf 20 -movflags +faststart "$edit_dir/00-title.mp4"

render_screen "$raw_dir/sessions.mp4" 6.50 "Your server. Your sessions." "$edit_dir/01-sessions.mp4"
render_screen "$raw_dir/model.mp4" 5.70 "Models, modes and agents — from OpenCode" "$edit_dir/02-model.mp4"
render_screen "$raw_dir/artifact.mp4" 12.35 "Open. Inspect. Send back." "$edit_dir/03-artifact.mp4"
render_screen "$raw_dir/terminal.mp4" 6.00 "A real terminal when you need it." "$edit_dir/04-terminal.mp4"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "color=c=0x0b1110:s=1080x1920:r=30:d=4.2" \
  -loop 1 -i "$icon" -t 4.2 \
  -filter_complex "[1:v]scale=190:190:flags=lanczos[logo];[0:v][logo]overlay=(W-w)/2:315,drawtext=fontfile='$font_bold':text='Built around OpenCode.':fontcolor=0xf2f7f4:fontsize=62:x=(w-text_w)/2:y=625,drawtext=fontfile='$font_bold':text='Can we build it together?':fontcolor=0x83d0ad:fontsize=55:x=(w-text_w)/2:y=720,drawtext=fontfile='$font_regular':text='github.com/Eslamasabry/opencode-mobile-next':fontcolor=0xf2f7f4:fontsize=35:x=(w-text_w)/2:y=875,drawbox=x=(w-120)/2:y=955:w=120:h=4:color=0x83d0ad@0.95:t=fill,drawtext=fontfile='$font_regular':text='Community prototype — not affiliated with OpenCode':fontcolor=0x7f8c86:fontsize=25:x=(w-text_w)/2:y=1010,fade=t=in:st=0:d=0.30,fade=t=out:st=3.85:d=0.35,format=yuv420p[out]" \
  -map "[out]" -an -c:v libx264 -preset medium -crf 20 -movflags +faststart "$edit_dir/05-end.mp4"

ffmpeg -hide_banner -loglevel error -y \
  -i "$edit_dir/00-title.mp4" \
  -i "$edit_dir/01-sessions.mp4" \
  -i "$edit_dir/02-model.mp4" \
  -i "$edit_dir/03-artifact.mp4" \
  -i "$edit_dir/04-terminal.mp4" \
  -i "$edit_dir/05-end.mp4" \
  -filter_complex "[0:v][1:v][2:v][3:v][4:v][5:v]concat=n=6:v=1:a=0[out]" \
  -map "[out]" -an -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p \
  -movflags +faststart "$output"

echo "Rendered $output"
