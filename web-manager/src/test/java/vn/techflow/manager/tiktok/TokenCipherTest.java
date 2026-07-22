package vn.techflow.manager.tiktok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsWithoutStoringPlaintext() {
        TokenCipher cipher = new TokenCipher("a-long-random-test-secret-that-is-not-production");

        String first = cipher.encrypt("access-token-value");
        String second = cipher.encrypt("access-token-value");

        assertNotEquals("access-token-value", first);
        assertNotEquals(first, second);
        assertEquals("access-token-value", cipher.decrypt(first));
        assertEquals("access-token-value", cipher.decrypt(second));
    }

    @Test
    void rejectsCiphertextEncryptedWithAnotherKey() {
        TokenCipher first = new TokenCipher("first-long-random-test-secret-1234567890");
        TokenCipher second = new TokenCipher("second-long-random-test-secret-1234567890");
        String encrypted = first.encrypt("refresh-token");

        assertThrows(IllegalStateException.class, () -> second.decrypt(encrypted));
    }

    @Test
    void rejectsWeakConfiguredKey() {
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher("too-short"));
    }
}
