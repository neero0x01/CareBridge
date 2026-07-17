package com.carebridge.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Seam: pure {@link WebhookSecretCipher} (no Spring). */
class WebhookSecretCipherTest {

  private static final byte[] KEY = fixedKey("test-webhook-master-key-32b!!");
  private static final byte[] OTHER_KEY = fixedKey("other-webhook-master-key-32b!");

  @Test
  void roundTripPreservesPlaintext() {
    WebhookSecretCipher cipher = new WebhookSecretCipher(KEY);
    String secret = "whsec_plaintext_once_only";

    String encrypted = cipher.encryptToBase64(secret);

    assertThat(encrypted).isNotBlank();
    assertThat(encrypted).isNotEqualTo(secret);
    assertThat(cipher.decryptFromBase64(encrypted)).isEqualTo(secret);
  }

  @Test
  void eachEncryptionUsesFreshIv() {
    WebhookSecretCipher cipher = new WebhookSecretCipher(KEY);
    String secret = "whsec_same";

    String a = cipher.encryptToBase64(secret);
    String b = cipher.encryptToBase64(secret);

    assertThat(a).isNotEqualTo(b);
    assertThat(cipher.decryptFromBase64(a)).isEqualTo(secret);
    assertThat(cipher.decryptFromBase64(b)).isEqualTo(secret);
  }

  @Test
  void wrongKeyCannotDecrypt() {
    WebhookSecretCipher a = new WebhookSecretCipher(KEY);
    WebhookSecretCipher b = new WebhookSecretCipher(OTHER_KEY);

    String encrypted = a.encryptToBase64("whsec_secret");

    assertThatThrownBy(() -> b.decryptFromBase64(encrypted))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsNon32ByteKey() {
    assertThatThrownBy(() -> new WebhookSecretCipher(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encryptedBlobIsBase64OfIvPlusCiphertext() {
    WebhookSecretCipher cipher = new WebhookSecretCipher(KEY);
    byte[] decoded = Base64.getDecoder().decode(cipher.encryptToBase64("x"));
    // 12-byte IV + at least 16-byte GCM tag
    assertThat(decoded.length).isGreaterThanOrEqualTo(12 + 16);
  }

  private static byte[] fixedKey(String s) {
    byte[] raw = s.getBytes(StandardCharsets.UTF_8);
    return Arrays.copyOf(raw, 32);
  }
}
