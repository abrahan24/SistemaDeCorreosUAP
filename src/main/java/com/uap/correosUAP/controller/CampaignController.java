package com.uap.correosUAP.controller;

import com.uap.correosUAP.repository.EmailCampaignRepository;
import com.uap.correosUAP.repository.EmailDeliveryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CampaignController {

    private final EmailCampaignRepository campaignRepository;
    private final EmailDeliveryRepository deliveryRepository;

    public CampaignController(EmailCampaignRepository campaignRepository, EmailDeliveryRepository deliveryRepository) {
        this.campaignRepository = campaignRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @GetMapping("/campaigns")
    public String campaigns(Model model) {
        model.addAttribute("campaigns", campaignRepository.findTop20ByOrderByCreatedAtDesc());
        return "campaigns";
    }

    @GetMapping("/campaigns/{id}")
    public String campaignDetail(@PathVariable Long id, Model model) {
        return campaignRepository.findById(id)
                .map(campaign -> {
                    model.addAttribute("campaign", campaign);
                    model.addAttribute("deliveries", deliveryRepository.findByCampaignIdOrderByCreatedAtAsc(id));
                    return "campaign-detail";
                })
                .orElse("redirect:/campaigns");
    }
}
