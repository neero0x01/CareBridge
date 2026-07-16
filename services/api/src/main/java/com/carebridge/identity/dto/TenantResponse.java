package com.carebridge.identity.dto;

import com.carebridge.identity.Tenant;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, Instant createdAt) {

  public static TenantResponse from(Tenant tenant) {
    return new TenantResponse(
        tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getCreatedAt());
  }
}
