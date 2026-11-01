// Regenerates src/test/resources/qr/*.png (deterministic; ZXing core 3.5.4, EC level M, 4-module quiet zone).
// From native/android/camera: java -cp <path>/core-3.5.4.jar tools/GenQrFixtures.java src/test/resources/qr
// Payloads are fixtures only: loopback and the 192.0.2.0/24 documentation range, a fake password.
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class GenQrFixtures {
    static BitMatrix matrix(String text, int size) throws Exception {
        Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class);
        h.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        h.put(EncodeHintType.MARGIN, 4);
        h.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, h);
    }
    static BufferedImage code(String text, int size, boolean inverted) throws Exception {
        BitMatrix m = matrix(text, size);
        BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) {
            boolean dark = m.get(x, y) ^ inverted;
            img.setRGB(x, y, dark ? 0xFF111111 : 0xFFF0F0F0);
        }
        return img;
    }
    static BufferedImage canvas(int w, int h, int grey) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(grey, grey, grey)); g.fillRect(0, 0, w, h); g.dispose();
        return img;
    }
    static void save(BufferedImage img, File dir, String name) throws Exception { ImageIO.write(img, "png", new File(dir, name)); }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]); dir.mkdirs();
        String pairing = "{\"urls\":[\"http://127.0.0.1:49374\"],\"username\":\"opencode\",\"password\":\"fixture-not-a-live-serve-password-000000000\"}";
        String pairingTwo = "{\"urls\":[\"http://127.0.0.1:4097\",\"http://192.0.2.20:4097\"],\"username\":\"opencode\",\"password\":\"fixture-not-a-live-serve-password-000000000\"}";
        save(code(pairing, 360, false), dir, "pairing.png");
        save(code(pairingTwo, 360, false), dir, "pairing-two-addresses.png");
        // A dark terminal prints light modules on a dark background.
        save(code(pairing, 360, true), dir, "pairing-inverted.png");
        // Held sideways.
        BufferedImage src = code(pairing, 360, false);
        BufferedImage rot = canvas(src.getHeight(), src.getWidth(), 240);
        Graphics2D g = rot.createGraphics();
        AffineTransform t = new AffineTransform(); t.translate(src.getHeight(), 0); t.rotate(Math.PI / 2);
        g.drawImage(src, t, null); g.dispose();
        save(rot, dir, "pairing-rotated.png");
        save(code("https://example.com", 300, false), dir, "url.png");
        save(code("WIFI:S:home;T:WPA;P:hunter2;;", 300, false), dir, "wifi.png");
        // A 1280x720 camera frame: the pairing code inside the centre window, a URL code far left outside it.
        BufferedImage scene = canvas(1280, 720, 128);
        Graphics2D s = scene.createGraphics();
        s.drawImage(code(pairing, 300, false), 490, 210, null);
        s.drawImage(code("https://example.com", 200, false), 10, 10, null);
        s.dispose();
        save(scene, dir, "scene-two-codes-1280x720.png");
        // Only a URL code, outside the window: the window sees nothing.
        BufferedImage edge = canvas(1280, 720, 128);
        Graphics2D e = edge.createGraphics(); e.drawImage(code("https://example.com", 200, false), 10, 10, null); e.dispose();
        save(edge, dir, "scene-code-outside-window-1280x720.png");
        save(canvas(640, 480, 128), dir, "blank-640x480.png");
    }
}
