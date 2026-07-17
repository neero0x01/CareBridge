package com.carebridge.webhooks;

import com.carebridge.config.CarebridgeProperties;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookConfig {

  @Bean
  WebhookSecretCipher webhookSecretCipher(CarebridgeProperties properties) {
    byte[] key = Base64.getDecoder().decode(properties.getWebhooks().getEncryptionKeyBase64());
    return new WebhookSecretCipher(key);
  }
}
