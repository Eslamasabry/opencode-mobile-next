"""Streams the capture frames as JPEG bytes on stdout for ffmpeg.

The sandbox ffmpeg build only demuxes `image2pipe` and only decodes MJPEG
(it is the Playwright screencast build), so the PNG frames are re-encoded
here at quality 95 and piped in:

    python3 tool/capture/feed_jpeg.py FRAMES_DIR \
      | ffmpeg -f image2pipe -c:v mjpeg -framerate 30 -i pipe:0 \
          -c:v libvpx -b:v 3M -pix_fmt yuv420p -vf scale=585:1266 \
          video/public/demo.webm
"""
import glob, io, os, sys
from PIL import Image
frames_dir = sys.argv[1]
out = sys.stdout.buffer
for p in sorted(glob.glob(os.path.join(frames_dir, '*.png'))):
    im = Image.open(p).convert('RGB')
    buf = io.BytesIO()
    im.save(buf, format='JPEG', quality=95, subsampling=0)
    out.write(buf.getvalue())
out.flush()
