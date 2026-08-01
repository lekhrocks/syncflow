package com.syncflow.api.connection.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public EncryptionService(@Value("${syncflow.encryption.key}") String base64Key) {
        var decoded = Base64.getDecoder().decode(base64Key);
        this.key = new SecretKeySpec(decoded, "AES");
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String plainText) {
        try {
            var iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            var cipherText = cipher.doFinal(plainText.getBytes());
            var combined = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            var combined = Base64.getDecoder().decode(encryptedText);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data");
            }
            var iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            var cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
