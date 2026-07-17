package com.carebridge.webhooks;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 over {@code eventId + "." + rawBody}, hex-encoded.
 *
 * <p>Header form: {@code X-CareBridge-Signature: sha256=<hex>}.
 */
public final class WebhookHmac {

  private static final String PREFIX = "sha256=";

  private WebhookHmac() {}

  public static String signHex(String secret, String eventId, byte[] rawBody) {
    return HexFormat.of().formatHex(digest(secret, eventId, rawBody));
  }

  /**
   * Constant-time verify of a {@code sha256=<hex>} header against the secret and payload.
   *
   * @return true only when the signature matches
   */
  public static boolean verify(String secret, String eventId, byte[] rawBody, String signatureHeader) {
    if (secret == null || eventId == null || rawBody == null || signatureHeader == null) {
      return false;
    }
    String header = signatureHeader.trim();
    if (!header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
      return false;
    }
    String presentedHex = header.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT);
    if (!presentedHex.matches("[0-9a-f]{64}")) {
      return false;
    }
    byte[] expected = digest(secret, eventId, rawBody);
    byte[] presented;
    try {
      presented = HexFormat.of().parseHex(presentedHex);
    } catch (IllegalArgumentException ex) {
      return false;
    }
    return MessageDigest.isEqual(expected, presented);
  }

  private static byte[] digest(String secret, String eventId, byte[] rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(eventId.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) '.');
      mac.update(rawBody);
      return mac.doFinal();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 not available", e);
    }
  }
}
