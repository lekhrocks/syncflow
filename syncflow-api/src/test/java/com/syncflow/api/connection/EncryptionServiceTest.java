package com.syncflow.api.connection;

import com.syncflow.api.connection.encryption.EncryptionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionServiceTest {

    private static final String VALID_KEY = "MDEyMzQ1Njc4OWFiY2RlZg==";

    @Test
    void encryptDecryptRoundTrip() {
        var enc = new EncryptionService(VALID_KEY);
        var original = "mySecretPassword123!@#$";
        var encrypted = enc.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        assertEquals(original, enc.decrypt(encrypted));
    }

    @Test
    void differentOutputEachCall() {
        var enc = new EncryptionService(VALID_KEY);
        var a = enc.encrypt("same-password");
        var b = enc.encrypt("same-password");
        assertNotEquals(a, b);
    }

    @Test
    void decryptProducesSameResult() {
        var e1 = new EncryptionService(VALID_KEY);
        var e2 = new EncryptionService(VALID_KEY);
        var enc = e1.encrypt("test-value");
        assertEquals("test-value", e2.decrypt(enc));
    }

    @Test
    void encryptEmptyString() {
        var enc = new EncryptionService(VALID_KEY);
        var encrypted = enc.encrypt("");
        assertEquals("", enc.decrypt(encrypted));
    }

    @Test
    void encryptLargePayload() {
        var enc = new EncryptionService(VALID_KEY);
        var large = "x".repeat(10000);
        var encryptedLarge = enc.encrypt(large);
        assertEquals(large, enc.decrypt(encryptedLarge));
    }

    @Test
    void encryptUnicode() {
        var enc = new EncryptionService(VALID_KEY);
        var original = "héllo wörld 🔐 データ同期";
        var encrypted = enc.encrypt(original);
        assertEquals(original, enc.decrypt(encrypted));
    }

    @Test
    void decryptInvalidCiphertextThrows() {
        var enc = new EncryptionService(VALID_KEY);
        assertThrows(RuntimeException.class, () -> enc.decrypt("invalid-base64!"));
    }

    @Test
    void decryptTamperedCiphertextThrows() {
        var enc = new EncryptionService(VALID_KEY);
        var encrypted = enc.encrypt("secret");
        var tampered = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
        assertThrows(RuntimeException.class, () -> enc.decrypt(tampered));
    }

    @Test
    void differentKeysProduceDifferentCiphertext() {
        var e1 = new EncryptionService(VALID_KEY);
        var e2 = new EncryptionService("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        var enc1 = e1.encrypt("test");
        var enc2 = e2.encrypt("test");
        assertNotEquals(enc1, enc2);
    }

    @Test
    void differentKeyCannotDecrypt() {
        var e1 = new EncryptionService(VALID_KEY);
        var e2 = new EncryptionService("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        var enc1 = e1.encrypt("secret");
        assertThrows(RuntimeException.class, () -> e2.decrypt(enc1));
    }

    @Test
    void specialCharactersInPassword() {
        var enc = new EncryptionService(VALID_KEY);
        var special = "p@ssw0rd!$#%^&*()_+-=[]{}|;':\",./<>?`~";
        var encrypted = enc.encrypt(special);
        assertEquals(special, enc.decrypt(encrypted));
    }

    @Test
    void veryLongPassword() {
        var enc = new EncryptionService(VALID_KEY);
        var longPwd = "a".repeat(1000);
        var encrypted = enc.encrypt(longPwd);
        assertEquals(longPwd, enc.decrypt(encrypted));
    }
}
