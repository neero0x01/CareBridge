package com.carebridge.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Seam: pure {@link WebhookHmac} (no Spring). */
class WebhookHmacTest {

  private static final String SECRET = "whsec_test_secret_value";
  private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final byte[] BODY =
      "{\"type\":\"lab.result.ready\",\"payload\":{\"patientRef\":\"PAT-001\"}}"
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void signProducesHexOfHmacSha256OverEventIdDotRawBody() throws Exception {
    String expected = independentHmacHex(SECRET, EVENT_ID, BODY);

    String actual = WebhookHmac.signHex(SECRET, EVENT_ID, BODY);

    assertThat(actual).isEqualTo(expected);
    assertThat(actual).matches("[0-9a-f]{64}");
  }

  @Test
  void verifyAcceptsSha256PrefixHeader() throws Exception {
    String hex = independentHmacHex(SECRET, EVENT_ID, BODY);

    assertThat(WebhookHmac.verify(SECRET, EVENT_ID, BODY, "sha256=" + hex)).isTrue();
  }

  @Test
  void verifyRejectsWrongSecret() throws Exception {
    String hex = independentHmacHex(SECRET, EVENT_ID, BODY);

    assertThat(WebhookHmac.verify("other-secret", EVENT_ID, BODY, "sha256=" + hex)).isFalse();
  }

  @Test
  void verifyRejectsTamperedBody() throws Exception {
    String hex = independentHmacHex(SECRET, EVENT_ID, BODY);
    byte[] tampered = "{\"type\":\"lab.result.ready\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);

    assertThat(WebhookHmac.verify(SECRET, EVENT_ID, tampered, "sha256=" + hex)).isFalse();
  }

  @Test
  void verifyRejectsWrongEventId() throws Exception {
    String hex = independentHmacHex(SECRET, EVENT_ID, BODY);

    assertThat(WebhookHmac.verify(SECRET, "00000000-0000-0000-0000-000000000001", BODY, "sha256=" + hex))
        .isFalse();
  }

  @Test
  void verifyRejectsMalformedHeader() {
    assertThat(WebhookHmac.verify(SECRET, EVENT_ID, BODY, "not-a-signature")).isFalse();
    assertThat(WebhookHmac.verify(SECRET, EVENT_ID, BODY, null)).isFalse();
    assertThat(WebhookHmac.verify(SECRET, EVENT_ID, BODY, "")).isFalse();
  }

  /** Independent oracle: same construction as the product formula, not calling WebhookHmac. */
  private static String independentHmacHex(String secret, String eventId, byte[] rawBody)
      throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] message = (eventId + ".").getBytes(StandardCharsets.UTF_8);
    mac.update(message);
    mac.update(rawBody);
    byte[] digest = mac.doFinal();
    StringBuilder sb = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
