package com.carebridge.webhooks;

import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.Role;
import com.carebridge.identity.Tenant;
import com.carebridge.identity.TenantRepository;
import com.carebridge.security.AuthenticatedUser;
import com.carebridge.webhooks.dto.RotateWebhookSecretResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookSecretService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final TenantRepository tenantRepository;
  private final WebhookSecretCipher cipher;

  public WebhookSecretService(TenantRepository tenantRepository, WebhookSecretCipher cipher) {
    this.tenantRepository = tenantRepository;
    this.cipher = cipher;
  }

  /** Generate a new plaintext secret and return encrypted form for persistence. */
  public GeneratedSecret generateEncrypted() {
    String plaintext = generatePlaintext();
    return new GeneratedSecret(plaintext, cipher.encryptToBase64(plaintext));
  }

  public String decrypt(String ciphertext) {
    return cipher.decryptFromBase64(ciphertext);
  }

  @Transactional
  public RotateWebhookSecretResponse rotate(AuthenticatedUser principal) {
    if (principal.role() != Role.ORG_ADMIN) {
      throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
    }
    Tenant tenant =
        tenantRepository
            .findById(principal.tenantId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found"));

    GeneratedSecret generated = generateEncrypted();
    tenant.setWebhookSecretCiphertext(generated.ciphertext());
    return new RotateWebhookSecretResponse(generated.plaintext());
  }

  static String generatePlaintext() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record GeneratedSecret(String plaintext, String ciphertext) {}
}
