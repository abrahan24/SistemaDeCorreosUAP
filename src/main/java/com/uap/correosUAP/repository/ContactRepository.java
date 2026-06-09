package com.uap.correosUAP.repository;

import com.uap.correosUAP.model.Contact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByEmailIgnoreCase(String email);

    Optional<Contact> findByInstitutionalEmailIgnoreCase(String institutionalEmail);

    List<Contact> findByActiveTrueOrderByCreatedAtDesc();

    List<Contact> findByActiveTrueAndInstitutionalEmailIsNotNullOrderByCreatedAtDesc();

    List<Contact> findByActiveTrueAndEmailIsNotNullOrderByCreatedAtDesc();

    List<Contact> findByActiveTrueAndEmailIsNotNullAndInstitutionalEmailIsNotNullOrderByCreatedAtDesc();

    List<Contact> findAllByOrderByCreatedAtDesc();

    long countByActiveTrue();

    long countByActiveTrueAndInstitutionalEmailIsNotNull();

    long countByActiveTrueAndEmailIsNotNullAndInstitutionalEmailIsNotNull();
}
