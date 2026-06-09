package com.uap.correosUAP.dto;

public record EmailSendResult(Long campaignId, int total, int sent, int failed) {
}
