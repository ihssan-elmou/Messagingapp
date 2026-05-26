package org.example.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Hachage mot de passe (SHA-256 + sel) sans dépendance externe. */
public final class PasswordUtils {

    private PasswordUtils() {}

    public static String hash(String password) {
        if (password == null) password = "";
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = sha256((password + bytesToHex(salt)).getBytes(StandardCharsets.UTF_8));
        return bytesToHex(salt) + ":" + bytesToHex(hash);
    }

    public static boolean verify(String password, String stored) {
        if (stored == null || stored.isBlank()) return false;
        if (password == null) password = "";
        int sep = stored.indexOf(':');
        if (sep <= 0) return false;
        String saltHex = stored.substring(0, sep);
        String hashHex = stored.substring(sep + 1);
        byte[] expected = hexToBytes(hashHex);
        if (expected == null) return false;
        byte[] actual = sha256((password + saltHex).getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
