package vn.techflow.manager.tiktok;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {
    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${techflow.token-encryption-key:}") String secret) {
        if (secret != null && !secret.isBlank() && secret.length() < 32) {
            throw new IllegalArgumentException("TECHFLOW_TOKEN_ENCRYPTION_KEY must contain at least 32 characters");
        }
        try {
            this.key = secret == null || secret.isBlank() ? null : new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể khởi tạo mã hóa token", exception);
        }
    }

    public boolean configured() { return key != null; }

    public String encrypt(String plaintext) {
        if (!configured()) throw new IllegalStateException("Chưa cấu hình TECHFLOW_TOKEN_ENCRYPTION_KEY");
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể mã hóa token TikTok", exception);
        }
    }

    public String decrypt(String encoded) {
        if (!configured()) throw new IllegalStateException("Chưa cấu hình TECHFLOW_TOKEN_ENCRYPTION_KEY");
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encoded);
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("Token mã hóa không hợp lệ");
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể giải mã token TikTok", exception);
        }
    }
}
