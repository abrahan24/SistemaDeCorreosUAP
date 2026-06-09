package com.uap.correosUAP.model;

public enum DeliveryStatus {
    SENT("Enviado"),
    FAILED("Fallido");

    private final String label;

    DeliveryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
