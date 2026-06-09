package com.uap.correosUAP.service;

import com.uap.correosUAP.model.Contact;
import com.uap.correosUAP.repository.ContactRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final InstitutionalEmailService institutionalEmailService;

    public ContactService(ContactRepository contactRepository, InstitutionalEmailService institutionalEmailService) {
        this.contactRepository = contactRepository;
        this.institutionalEmailService = institutionalEmailService;
    }

    @Transactional
    public Contact saveManual(String email, String firstName, String lastName) {
        String normalizedEmail = clean(email) == null ? null : clean(email).toLowerCase(Locale.ROOT);
        String cleanFirstName = clean(firstName);
        String cleanLastName = clean(lastName);

        if (normalizedEmail == null && cleanFirstName == null && cleanLastName == null) {
            throw new IllegalArgumentException("Debe ingresar nombre, apellido o correo.");
        }

        Contact contact = normalizedEmail == null
                ? new Contact(null, cleanFirstName, cleanLastName, "manual")
                : contactRepository.findByEmailIgnoreCase(normalizedEmail)
                    .orElseGet(() -> new Contact(normalizedEmail, cleanFirstName, cleanLastName, "manual"));

        contact.setFirstName(cleanFirstName);
        contact.setLastName(cleanLastName);
        contact.setEmail(normalizedEmail);
        contact.setSource("manual");
        contact.setActive(true);
        institutionalEmailService.assignGeneratedEmail(contact);
        return contactRepository.save(contact);
    }

    @Transactional
    public int regenerateInstitutionalEmails() {
        List<Contact> contacts = contactRepository.findAllByOrderByCreatedAtDesc();
        for (Contact contact : contacts) {
            institutionalEmailService.assignGeneratedEmail(contact);
        }
        contactRepository.saveAll(contacts);
        return contacts.size();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
