package com.uap.correosUAP.controller;

import com.uap.correosUAP.dto.ContactImportResult;
import com.uap.correosUAP.dto.EmailSendResult;
import com.uap.correosUAP.model.Contact;
import com.uap.correosUAP.repository.ContactRepository;
import com.uap.correosUAP.service.ContactService;
import com.uap.correosUAP.service.ContactImportService;
import com.uap.correosUAP.service.EmailService;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ContactController {

    private final ContactRepository contactRepository;
    private final ContactImportService contactImportService;
    private final ContactService contactService;
    private final EmailService emailService;

    public ContactController(
            ContactRepository contactRepository,
            ContactImportService contactImportService,
            ContactService contactService,
            EmailService emailService
    ) {
        this.contactRepository = contactRepository;
        this.contactImportService = contactImportService;
        this.contactService = contactService;
        this.emailService = emailService;
    }

    @GetMapping("/contacts")
    public String contacts(Model model) {
        model.addAttribute("contacts", contactRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activeContacts", contactRepository.countByActiveTrue());
        model.addAttribute("credentialRecipients",
                contactRepository.countByActiveTrueAndEmailIsNotNullAndInstitutionalEmailIsNotNull());
        return "contacts";
    }

    @PostMapping("/contacts/import")
    public String importContacts(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            ContactImportResult result = contactImportService.importContacts(file);
            redirectAttributes.addFlashAttribute("success",
                    "Importacion completada: " + result.created() + " nuevos, "
                            + result.updated() + " actualizados, " + result.skipped() + " omitidos.");
            redirectAttributes.addFlashAttribute("importErrors", result.errors());
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/contacts";
    }

    @PostMapping("/contacts")
    public String createContact(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            RedirectAttributes redirectAttributes
    ) {
        if (!isBlank(email) && !looksLikeEmail(email)) {
            redirectAttributes.addFlashAttribute("error", "El correo personal no es valido.");
            return "redirect:/contacts";
        }
        try {
            Contact contact = contactService.saveManual(email, firstName, lastName);
            redirectAttributes.addFlashAttribute("success",
                    "Contacto guardado con correo UAP " + contact.getInstitutionalEmail() + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/contacts";
    }

    @PostMapping("/contacts/generate-uap")
    public String regenerateInstitutionalEmails(RedirectAttributes redirectAttributes) {
        int total = contactService.regenerateInstitutionalEmails();
        redirectAttributes.addFlashAttribute("success", "Se generaron correos UAP para " + total + " contactos.");
        return "redirect:/contacts";
    }

    @PostMapping("/contacts/send-credentials")
    public String sendCredentialsMassive(RedirectAttributes redirectAttributes) {
        try {
            EmailSendResult result = emailService.sendInstitutionalCredentialsMassive();
            redirectAttributes.addFlashAttribute("success",
                    "Credenciales enviadas: " + result.sent() + " enviados, "
                            + result.failed() + " fallidos de " + result.total() + ".");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudieron enviar credenciales: " + ex.getMessage());
        }
        return "redirect:/contacts";
    }

    @PostMapping("/contacts/{id}/send-credentials")
    public String sendCredentials(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            EmailSendResult result = emailService.sendInstitutionalCredentials(id);
            redirectAttributes.addFlashAttribute("success",
                    "Credenciales enviadas. Campana #" + result.campaignId() + ".");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudieron enviar credenciales: " + ex.getMessage());
        }
        return "redirect:/contacts";
    }

    @PostMapping("/contacts/{id}/toggle")
    public String toggleContact(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Contact contact = contactRepository.findById(id).orElse(null);
        if (contact == null) {
            redirectAttributes.addFlashAttribute("error", "Contacto no encontrado.");
            return "redirect:/contacts";
        }
        contact.setActive(!contact.isActive());
        contactRepository.save(contact);
        redirectAttributes.addFlashAttribute("success", "Estado actualizado para " + contact.getDisplayName() + ".");
        return "redirect:/contacts";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private boolean looksLikeEmail(String value) {
        String email = value == null ? "" : value.trim();
        return email.contains("@") && email.contains(".");
    }
}
