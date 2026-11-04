package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Normalizes ECMA-48 colon SGR for the pinned emulator without consuming UTF-8 or OSC data. */
final class SgrNormalizer {
    private boolean controlString;
    private boolean stringEscape;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    byte[] append(byte[] bytes, int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(count);
        for (int i = 0; i < count; i++) {
            int b = bytes[i] & 255;
            if (controlString) {
                out.write(b);
                if (b == 7 || (stringEscape && b == '\\')) controlString = false;
                stringEscape = b == 27;
                continue;
            }
            if (pending.size() == 0) {
                if (b == 27) pending.write(b); else out.write(b);
                continue;
            }
            if (pending.size() == 1 && b != '[') {
                if (b == ']' || b == 'P' || b == 'X' || b == '^' || b == '_') controlString = true;
                write(out, pending.toByteArray()); pending.reset();
                if (b == 27) pending.write(b); else out.write(b);
                continue;
            }
            pending.write(b);
            if (pending.size() == 2) continue;
            if (b >= 0x40 && b <= 0x7e) {
                byte[] sequence = pending.toByteArray(); pending.reset();
                if (b == 'm') {
                    String params = new String(sequence, 2, sequence.length - 3, StandardCharsets.US_ASCII);
                    String normalized = normalize(params);
                    if (!normalized.isEmpty()) write(out, ("\033[" + normalized + "m").getBytes(StandardCharsets.US_ASCII));
                } else write(out, sequence);
            } else if (pending.size() > 1024 || b < 0x20 || b > 0x3f) {
                write(out, pending.toByteArray()); pending.reset();
            }
        }
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) { out.write(bytes, 0, bytes.length); }

    private String normalize(String parameters) {
        if (parameters.isEmpty()) return "0";
        String[] parts = parameters.split(";", -1);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i];
            String[] sub = value.split(":", -1);
            String code = sub[0];
            if (code.equals("58")) {
                if (sub.length == 1 && i + 1 < parts.length) {
                    String mode = parts[++i];
                    i += mode.equals("5") ? 1 : mode.equals("2") ? 3 : 0;
                }
                continue;
            }
            if (code.equals("59")) continue;
            if (sub.length > 1 && code.equals("4")) {
                result.add(sub[1].equals("0") ? "24" : "4");
            } else if (sub.length > 1 && (code.equals("38") || code.equals("48"))) {
                if (sub[1].equals("5") && sub.length == 3) {
                    result.add(code + ";5;" + sub[2]);
                } else if (sub[1].equals("2") && (sub.length == 5 || sub.length == 6)) {
                    int at = sub.length - 3;
                    result.add(code + ";2;" + sub[at] + ";" + sub[at + 1] + ";" + sub[at + 2]);
                }
            } else if (sub.length == 1) result.add(value);
        }
        return String.join(";", result);
    }
}
