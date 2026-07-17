package com.carebridge.webhooks.dto;

public record InboundWebhookResponse(boolean accepted, boolean alreadyProcessed) {

  public static InboundWebhookResponse ofNew() {
    return new InboundWebhookResponse(true, false);
  }

  public static InboundWebhookResponse ofAlreadyProcessed() {
    return new InboundWebhookResponse(true, true);
  }
}
