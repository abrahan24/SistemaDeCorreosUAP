package com.uap.correosUAP.model;

public enum CampaignStatus {
    SENT("Enviado"),
    PARTIAL("Parcial"),
    FAILED("Fallido");

    private final String label;

    CampaignStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
