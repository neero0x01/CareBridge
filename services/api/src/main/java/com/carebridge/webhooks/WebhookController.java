package com.carebridge.webhooks;

import com.carebridge.security.AuthenticatedUser;
import com.carebridge.webhooks.dto.InboundWebhookResponse;
import com.carebridge.webhooks.dto.RotateWebhookSecretResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

  private final WebhookService webhookService;
  private final WebhookSecretService webhookSecretService;

  public WebhookController(
      WebhookService webhookService, WebhookSecretService webhookSecretService) {
    this.webhookService = webhookService;
    this.webhookSecretService = webhookSecretService;
  }

  /**
   * Public inbound endpoint. Auth is HMAC over raw body + event id, not JWT.
   *
   * <p>202 for newly accepted; 200 if the same {@code event_id} was already processed.
   */
  @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<InboundWebhookResponse> inbound(
      @RequestHeader(value = "X-CareBridge-Tenant", required = false) String tenantSlug,
      @RequestHeader(value = "X-CareBridge-Event-Id", required = false) String eventId,
      @RequestHeader(value = "X-CareBridge-Signature", required = false) String signature,
      @RequestBody byte[] rawBody) {
    InboundWebhookResponse result =
        webhookService.processInbound(tenantSlug, eventId, signature, rawBody);
    HttpStatus status =
        result.alreadyProcessed() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(result);
  }

  @PostMapping("/secret/rotate")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public RotateWebhookSecretResponse rotateSecret(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return webhookSecretService.rotate(principal);
  }
}
