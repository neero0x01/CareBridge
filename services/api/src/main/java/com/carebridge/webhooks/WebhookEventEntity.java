package com.carebridge.webhooks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@IdClass(WebhookEventEntity.Pk.class)
public class WebhookEventEntity {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String type;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "signature_valid", nullable = false)
  private boolean signatureValid;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected WebhookEventEntity() {}

  public WebhookEventEntity(
      UUID id,
      UUID tenantId,
      String type,
      String payloadJson,
      boolean signatureValid,
      Instant processedAt,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.type = type;
    this.payloadJson = payloadJson;
    this.signatureValid = signatureValid;
    this.processedAt = processedAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getType() {
    return type;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public boolean isSignatureValid() {
    return signatureValid;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(Instant processedAt) {
    this.processedAt = processedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Pk implements Serializable {
    private UUID id;
    private UUID tenantId;

    public Pk() {}

    public Pk(UUID id, UUID tenantId) {
      this.id = id;
      this.tenantId = tenantId;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID getTenantId() {
      return tenantId;
    }

    public void setTenantId(UUID tenantId) {
      this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(id, pk.id) && Objects.equals(tenantId, pk.tenantId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, tenantId);
    }
  }
}
