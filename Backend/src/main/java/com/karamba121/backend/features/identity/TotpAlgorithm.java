package com.karamba121.backend.features.identity;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpAlgorithm {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpAlgorithm() {
    }

    public static String encodeBase32(byte[] value) {
        StringBuilder result = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte item : value) {
            buffer = (buffer << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) result.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    public static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = ALPHABET.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Segredo TOTP inválido");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                result[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return result;
    }

    public static String generate(String secret, Instant instant) {
        return generateForStep(secret, instant.getEpochSecond() / 30);
    }

    static String generateForStep(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível calcular o TOTP", exception);
        }
    }
}
