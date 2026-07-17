package com.carebridge.webhooks;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM encrypt/decrypt for webhook secrets at rest. Ciphertext format (Base64): 12-byte IV ||
 * ciphertext+tag.
 */
public final class WebhookSecretCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey key;

  public WebhookSecretCipher(byte[] keyBytes) {
    if (keyBytes == null || keyBytes.length != 32) {
      throw new IllegalArgumentException("Webhook encryption key must be 32 bytes (AES-256)");
    }
    this.key = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
  }

  public String encryptToBase64(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
      buf.put(iv);
      buf.put(ciphertext);
      return Base64.getEncoder().encodeToString(buf.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt webhook secret", e);
    }
  }

  public String decryptFromBase64(String encoded) {
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      if (all.length <= IV_LENGTH) {
        throw new IllegalArgumentException("Ciphertext too short");
      }
      byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
      byte[] ciphertext = Arrays.copyOfRange(all, IV_LENGTH, all.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] plain = cipher.doFinal(ciphertext);
      return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to decrypt webhook secret", e);
    }
  }
}
