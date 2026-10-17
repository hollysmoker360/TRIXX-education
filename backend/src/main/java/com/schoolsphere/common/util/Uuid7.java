package com.schoolsphere.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUIDv7 generator: 48-bit big-endian millisecond timestamp, version and variant bits,
 * remaining bits random. Time-ordered identifiers keep indexes efficient and help sync ordering
 * (spec 3.1).
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID generate() {
        long timestamp = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L; // version 7
        msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);

        long lsb = 0L;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (random[i] & 0xFFL);
        }
        lsb &= 0x3FFFFFFFFFFFFFFFL;
        lsb |= 0x8000000000000000L; // variant 2

        return new UUID(msb, lsb);
    }
}
