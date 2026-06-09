package com.uap.correosUAP.repository;

import com.uap.correosUAP.model.EmailDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, Long> {

    List<EmailDelivery> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);
}
