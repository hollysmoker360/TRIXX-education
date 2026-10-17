package com.schoolsphere.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Field-level encryption for the most sensitive child data fields such as national IDs, medical
 * notes, and guardian contacts (spec 3.1), in addition to disk encryption. AES-256-GCM with a
 * random 12-byte nonce per value; the key comes from configuration (a secret manager in
 * production). In an envelope scheme the configured key is the data-encryption key.
 */
@Component
public class FieldCrypto {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static volatile FieldCrypto instance;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCrypto(@Value("${schoolsphere.security.field-key:dev-field-key-change-me-32bytes!}") String keyMaterial) {
        byte[] keyBytes = keyMaterial.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[32];
        for (int i = 0; i < normalized.length; i++) {
            normalized[i] = i < keyBytes.length ? keyBytes[i] : (byte) 0;
        }
        this.key = new SecretKeySpec(normalized, "AES");
        instance = this;
    }

    public static FieldCrypto instance() {
        return instance;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS,
                    java.util.Arrays.copyOfRange(combined, 0, NONCE_LENGTH)));
            byte[] plain = cipher.doFinal(java.util.Arrays.copyOfRange(combined, NONCE_LENGTH, combined.length));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Field decryption failed", e);
        }
    }

    /** JPA converter for encrypted string columns. */
    @Converter
    public static class EncryptedStringConverter implements AttributeConverter<String, String> {

        @Override
        public String convertToDatabaseColumn(String attribute) {
            FieldCrypto crypto = FieldCrypto.instance();
            return crypto == null ? attribute : crypto.encrypt(attribute);
        }

        @Override
        public String convertToEntityAttribute(String dbData) {
            FieldCrypto crypto = FieldCrypto.instance();
            return crypto == null ? dbData : crypto.decrypt(dbData);
        }
    }
}
