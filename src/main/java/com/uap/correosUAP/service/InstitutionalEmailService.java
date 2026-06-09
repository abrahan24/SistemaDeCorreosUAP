package com.uap.correosUAP.service;

import com.uap.correosUAP.model.Contact;
import com.uap.correosUAP.repository.ContactRepository;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InstitutionalEmailService {

    private static final String DOMAIN = "@uap.edu.bo";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";

    private final ContactRepository contactRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public InstitutionalEmailService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public String generateUnique(Contact contact) {
        String base = buildBase(contact);
        String candidate = base + DOMAIN;
        int suffix = 2;
        while (isTakenByAnotherContact(candidate, contact)) {
            candidate = base + suffix + DOMAIN;
            suffix++;
        }
        return candidate;
    }

    public void assignGeneratedEmail(Contact contact) {
        contact.setInstitutionalEmail(generateUnique(contact));
        ensureInitialPassword(contact);
    }

    public void ensureInitialPassword(Contact contact) {
        if (contact.getInstitutionalPassword() == null || contact.getInstitutionalPassword().isBlank()) {
            contact.setInstitutionalPassword(generatePassword());
        }
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder();
        for (int index = 0; index < 12; index++) {
            password.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private boolean isTakenByAnotherContact(String candidate, Contact contact) {
        return contactRepository.findByInstitutionalEmailIgnoreCase(candidate)
                .filter(existing -> contact.getId() == null || !existing.getId().equals(contact.getId()))
                .isPresent();
    }

    private String buildBase(Contact contact) {
        String lastName = slug(contact.getLastName());
        String firstName = slug(contact.getFirstName());

        if (!lastName.isBlank() && !firstName.isBlank()) {
            return lastName + "." + firstName;
        }
        if (!firstName.isBlank()) {
            return firstName;
        }
        if (!lastName.isBlank()) {
            return lastName;
        }
        return slug(contact.getEmail() == null ? "usuario" : contact.getEmail().split("@")[0]);
    }

    private String slug(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "")
                .replaceAll("\\.{2,}", ".");
        return normalized.length() > 80 ? normalized.substring(0, 80).replaceAll("\\.+$", "") : normalized;
    }
}
