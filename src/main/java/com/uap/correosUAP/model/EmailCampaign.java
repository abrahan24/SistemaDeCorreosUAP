package com.uap.correosUAP.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class EmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.FAILED;

    @Column(nullable = false)
    private int totalRecipients;

    @Column(nullable = false)
    private int successfulDeliveries;

    @Column(nullable = false)
    private int failedDeliveries;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public EmailCampaign(String subject, String bodyHtml) {
        this.subject = subject;
        this.bodyHtml = bodyHtml;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
