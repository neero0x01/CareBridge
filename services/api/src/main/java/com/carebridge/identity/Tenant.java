package com.carebridge.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true, length = 100)
  private String slug;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** AES-GCM ciphertext (Base64) of the per-tenant webhook HMAC secret. */
  @Column(name = "webhook_secret_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String webhookSecretCiphertext;

  protected Tenant() {}

  public Tenant(
      UUID id, String name, String slug, Instant createdAt, String webhookSecretCiphertext) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.createdAt = createdAt;
    this.webhookSecretCiphertext = webhookSecretCiphertext;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getWebhookSecretCiphertext() {
    return webhookSecretCiphertext;
  }

  public void setWebhookSecretCiphertext(String webhookSecretCiphertext) {
    this.webhookSecretCiphertext = webhookSecretCiphertext;
  }
}
