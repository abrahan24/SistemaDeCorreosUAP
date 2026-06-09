package com.uap.correosUAP.controller;

import com.uap.correosUAP.repository.ContactRepository;
import com.uap.correosUAP.repository.EmailCampaignRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ContactRepository contactRepository;
    private final EmailCampaignRepository campaignRepository;

    public HomeController(ContactRepository contactRepository, EmailCampaignRepository campaignRepository) {
        this.contactRepository = contactRepository;
        this.campaignRepository = campaignRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("totalContacts", contactRepository.count());
        model.addAttribute("activeContacts", contactRepository.countByActiveTrue());
        model.addAttribute("campaigns", campaignRepository.findTop20ByOrderByCreatedAtDesc());
        return "index";
    }
}
