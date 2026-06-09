package com.uap.correosUAP.controller;

import com.uap.correosUAP.dto.EmailSendResult;
import com.uap.correosUAP.model.EmailTarget;
import com.uap.correosUAP.repository.ContactRepository;
import com.uap.correosUAP.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmailController {

    private final EmailService emailService;
    private final ContactRepository contactRepository;

    public EmailController(EmailService emailService, ContactRepository contactRepository) {
        this.emailService = emailService;
        this.contactRepository = contactRepository;
    }

    @GetMapping("/emails")
    public String emails(Model model) {
        model.addAttribute("activeContacts", contactRepository.countByActiveTrue());
        model.addAttribute("activeInstitutionalContacts", contactRepository.countByActiveTrueAndInstitutionalEmailIsNotNull());
        model.addAttribute("defaultBody", """
                <p>Hola {{nombre}},</p>
                <p>Escribimos para compartir informacion importante.</p>
                <p>Saludos,<br>UAP</p>
                """);
        return "emails";
    }

    @PostMapping("/emails/send-individual")
    public String sendIndividual(
            @RequestParam String email,
            @RequestParam(required = false) String name,
            @RequestParam String subject,
            @RequestParam String bodyHtml,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(email) || !looksLikeEmail(email) || isBlank(subject) || isBlank(bodyHtml)) {
            redirectAttributes.addFlashAttribute("error", "Revise correo, asunto y cuerpo del correo.");
            return "redirect:/emails";
        }
        try {
            EmailSendResult result = emailService.sendIndividual(email.trim(), name, subject.trim(), bodyHtml);
            addSendFlash(result, redirectAttributes);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo enviar el correo: " + ex.getMessage());
        }
        return "redirect:/emails";
    }

    @PostMapping("/emails/send-massive")
    public String sendMassive(
            @RequestParam String subject,
            @RequestParam String bodyHtml,
            @RequestParam(defaultValue = "INSTITUTIONAL") EmailTarget target,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(subject) || isBlank(bodyHtml)) {
            redirectAttributes.addFlashAttribute("error", "Debe ingresar asunto y cuerpo del correo.");
            return "redirect:/emails";
        }
        try {
            EmailSendResult result = emailService.sendMassive(subject.trim(), bodyHtml, target);
            addSendFlash(result, redirectAttributes);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo ejecutar el envio masivo: " + ex.getMessage());
        }
        return "redirect:/emails";
    }

    private void addSendFlash(EmailSendResult result, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("success",
                "Campana #" + result.campaignId() + ": " + result.sent()
                        + " enviados, " + result.failed() + " fallidos de " + result.total() + ".");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private boolean looksLikeEmail(String value) {
        String email = value == null ? "" : value.trim();
        return email.contains("@") && email.contains(".");
    }
}
