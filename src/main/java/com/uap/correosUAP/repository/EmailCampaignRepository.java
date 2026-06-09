package com.uap.correosUAP.repository;

import com.uap.correosUAP.model.EmailCampaign;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    List<EmailCampaign> findTop20ByOrderByCreatedAtDesc();
}
