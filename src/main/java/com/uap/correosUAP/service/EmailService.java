package com.uap.correosUAP.service;

import com.uap.correosUAP.dto.EmailSendResult;
import com.uap.correosUAP.model.CampaignStatus;
import com.uap.correosUAP.model.Contact;
import com.uap.correosUAP.model.DeliveryStatus;
import com.uap.correosUAP.model.EmailCampaign;
import com.uap.correosUAP.model.EmailDelivery;
import com.uap.correosUAP.model.EmailTarget;
import com.uap.correosUAP.repository.ContactRepository;
import com.uap.correosUAP.repository.EmailCampaignRepository;
import com.uap.correosUAP.repository.EmailDeliveryRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ContactRepository contactRepository;
    private final EmailCampaignRepository campaignRepository;
    private final EmailDeliveryRepository deliveryRepository;
    private final InstitutionalEmailService institutionalEmailService;
    private final String from;
    private final String logoUrl;

    public EmailService(
            JavaMailSender mailSender,
            ContactRepository contactRepository,
            EmailCampaignRepository campaignRepository,
            EmailDeliveryRepository deliveryRepository,
            InstitutionalEmailService institutionalEmailService,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.logo-url:https://uap.edu.bo/wp-content/uploads/UAP-250px-1.png}") String logoUrl
    ) {
        this.mailSender = mailSender;
        this.contactRepository = contactRepository;
        this.campaignRepository = campaignRepository;
        this.deliveryRepository = deliveryRepository;
        this.institutionalEmailService = institutionalEmailService;
        this.from = from;
        this.logoUrl = logoUrl;
    }

    @Transactional
    public EmailSendResult sendIndividual(String email, String name, String subject, String bodyHtml) {
        Contact contact = contactRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> contactRepository.save(new Contact(email.toLowerCase(Locale.ROOT), name, null, "manual")));
        contact.setActive(true);
        if (contact.getInstitutionalEmail() == null || contact.getInstitutionalEmail().isBlank()) {
            institutionalEmailService.assignGeneratedEmail(contact);
        }

        EmailCampaign campaign = new EmailCampaign(subject, bodyHtml);
        campaign.setTotalRecipients(1);
        campaignRepository.save(campaign);

        EmailDelivery delivery = sendToContact(campaign, contact, subject, bodyHtml);
        int sent = delivery.getStatus() == DeliveryStatus.SENT ? 1 : 0;
        int failed = delivery.getStatus() == DeliveryStatus.FAILED ? 1 : 0;

        campaign.setSuccessfulDeliveries(sent);
        campaign.setFailedDeliveries(failed);
        campaign.setStatus(sent == 1 ? CampaignStatus.SENT : CampaignStatus.FAILED);
        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        return new EmailSendResult(campaign.getId(), 1, sent, failed);
    }

    @Transactional
    public EmailSendResult sendMassive(String subject, String bodyHtml, EmailTarget target) {
        List<Contact> contacts = target == EmailTarget.PERSONAL
                ? contactRepository.findByActiveTrueAndEmailIsNotNullOrderByCreatedAtDesc()
                : contactRepository.findByActiveTrueAndInstitutionalEmailIsNotNullOrderByCreatedAtDesc();
        if (contacts.isEmpty()) {
            throw new IllegalStateException("No hay contactos activos con el tipo de correo seleccionado.");
        }

        EmailCampaign campaign = new EmailCampaign(subject, bodyHtml);
        campaign.setTotalRecipients(contacts.size());
        campaignRepository.save(campaign);

        int sent = 0;
        int failed = 0;
        for (Contact contact : contacts) {
            String recipientEmail = target == EmailTarget.PERSONAL ? contact.getEmail() : contact.getInstitutionalEmail();
            EmailDelivery delivery = sendToContact(campaign, contact, recipientEmail, subject, bodyHtml);
            if (delivery.getStatus() == DeliveryStatus.SENT) {
                sent++;
            } else {
                failed++;
            }
        }

        campaign.setSuccessfulDeliveries(sent);
        campaign.setFailedDeliveries(failed);
        campaign.setStatus(resolveCampaignStatus(sent, failed));
        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        return new EmailSendResult(campaign.getId(), contacts.size(), sent, failed);
    }

    @Transactional
    public EmailSendResult sendInstitutionalCredentials(Long contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contacto no encontrado."));
        validateCredentialRecipient(contact);
        institutionalEmailService.ensureInitialPassword(contact);
        contactRepository.save(contact);

        String subject = "Credenciales de tu correo institucional UAP";
        String bodyHtml = credentialBody(contact);
        EmailCampaign campaign = new EmailCampaign(subject, bodyHtml);
        campaign.setTotalRecipients(1);
        campaignRepository.save(campaign);

        EmailDelivery delivery = sendToContact(campaign, contact, contact.getEmail(), subject, bodyHtml);
        int sent = delivery.getStatus() == DeliveryStatus.SENT ? 1 : 0;
        int failed = delivery.getStatus() == DeliveryStatus.FAILED ? 1 : 0;
        if (delivery.getStatus() == DeliveryStatus.SENT) {
            contact.setCredentialsSentAt(delivery.getSentAt());
            contactRepository.save(contact);
        }
        campaign.setSuccessfulDeliveries(sent);
        campaign.setFailedDeliveries(failed);
        campaign.setStatus(sent == 1 ? CampaignStatus.SENT : CampaignStatus.FAILED);
        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        return new EmailSendResult(campaign.getId(), 1, sent, failed);
    }

    @Transactional
    public EmailSendResult sendInstitutionalCredentialsMassive() {
        List<Contact> contacts = contactRepository
                .findByActiveTrueAndEmailIsNotNullAndInstitutionalEmailIsNotNullOrderByCreatedAtDesc();
        if (contacts.isEmpty()) {
            throw new IllegalStateException("No hay contactos activos con correo personal y correo UAP generado.");
        }

        String subject = "Credenciales de tu correo institucional UAP";
        String bodyHtml = credentialBody(null);
        EmailCampaign campaign = new EmailCampaign(subject, bodyHtml);
        campaign.setTotalRecipients(contacts.size());
        campaignRepository.save(campaign);

        int sent = 0;
        int failed = 0;
        for (Contact contact : contacts) {
            institutionalEmailService.ensureInitialPassword(contact);
            contactRepository.save(contact);
            EmailDelivery delivery = sendToContact(
                    campaign,
                    contact,
                    contact.getEmail(),
                    subject,
                    credentialBody(contact)
            );
            if (delivery.getStatus() == DeliveryStatus.SENT) {
                contact.setCredentialsSentAt(delivery.getSentAt());
                contactRepository.save(contact);
                sent++;
            } else {
                failed++;
            }
        }

        campaign.setSuccessfulDeliveries(sent);
        campaign.setFailedDeliveries(failed);
        campaign.setStatus(resolveCampaignStatus(sent, failed));
        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        return new EmailSendResult(campaign.getId(), contacts.size(), sent, failed);
    }

    private EmailDelivery sendToContact(EmailCampaign campaign, Contact contact, String subject, String bodyHtml) {
        return sendToContact(campaign, contact, contact.getEmail(), subject, bodyHtml);
    }

    private EmailDelivery sendToContact(
            EmailCampaign campaign,
            Contact contact,
            String recipientEmail,
            String subject,
            String bodyHtml
    ) {
        EmailDelivery delivery = new EmailDelivery(campaign, contact, recipientEmail);
        try {
            sendMessage(contact, recipientEmail, subject, bodyHtml);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(limit(ex.getMessage(), 600));
        }
        return deliveryRepository.save(delivery);
    }

    private void sendMessage(Contact contact, String recipientEmail, String subject, String bodyHtml) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setText(renderBody(bodyHtml, contact, recipientEmail), true);
        mailSender.send(message);
    }

    private String renderBody(String bodyHtml, Contact contact, String recipientEmail) {
        return bodyHtml
                .replace("{{nombre}}", safe(contact.getFirstName()))
                .replace("{{apellido}}", safe(contact.getLastName()))
                .replace("{{email}}", safe(recipientEmail))
                .replace("{{correo}}", safe(recipientEmail))
                .replace("{{correoPersonal}}", safe(contact.getEmail()))
                .replace("{{correoUap}}", safe(contact.getInstitutionalEmail()))
                .replace("{{nombreCompleto}}", safe(contact.getDisplayName()));
    }

    private void validateCredentialRecipient(Contact contact) {
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            throw new IllegalStateException("El contacto no tiene correo personal para recibir sus credenciales.");
        }
        if (contact.getInstitutionalEmail() == null || contact.getInstitutionalEmail().isBlank()) {
            throw new IllegalStateException("El contacto no tiene correo institucional generado.");
        }
    }

    private String credentialBody(Contact contact) {
        if (contact == null) {
            return "Credenciales de correo institucional UAP";
        }

        String name = escapeHtml(contact.getDisplayName());
        String institutionalEmail = escapeHtml(contact.getInstitutionalEmail());
        String password = escapeHtml(contact.getInstitutionalPassword());
        String logo = escapeHtml(logoUrl);

        return """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;background:#eef2f7;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <div style="display:none;max-height:0;overflow:hidden;">Tu correo institucional UAP ya esta listo.</div>
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#eef2f7;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border:1px solid #d8dee8;border-radius:12px;overflow:hidden;">
                          <tr>
                            <td style="background:#0b2347;border-top:8px solid #c1121f;padding:24px 28px;color:#ffffff;">
                              <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="width:86px;vertical-align:middle;">
                                    <div style="background:#ffffff;border-radius:12px;padding:8px;width:70px;height:70px;text-align:center;">
                                      <img src="__LOGO_URL__" width="54" height="100" alt="Logo UAP" style="display:block;border:0;margin:0 auto;max-width:60px;max-height:70px;">
                                    </div>
                                  </td>
                                  <td style="vertical-align:middle;">
                                    <div style="font-size:13px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#f6d7d7;">Universidad Amazonica de Pando</div>
                                    <div style="font-size:25px;font-weight:700;line-height:1.25;margin-top:8px;color:#ffffff;">Credenciales de correo institucional</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="height:5px;background:#c1121f;font-size:0;line-height:0;">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <p style="font-size:16px;line-height:1.55;margin:0 0 18px;">Hola <strong>__NAME__</strong>,</p>
                              <p style="font-size:15px;line-height:1.6;margin:0 0 22px;">Tu cuenta institucional fue generada correctamente. Usa estos datos para ingresar por primera vez.</p>
                              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="border-collapse:separate;border-spacing:0 10px;">
                                <tr>
                                  <td style="background:#f7f9fc;border-left:5px solid #0b2347;border-top:1px solid #d8dee8;border-right:1px solid #d8dee8;border-bottom:1px solid #d8dee8;border-radius:8px;padding:14px 16px;">
                                    <div style="font-size:12px;color:#687387;font-weight:700;text-transform:uppercase;">Correo institucional</div>
                                    <div style="font-size:18px;font-weight:700;color:#0b2347;margin-top:6px;">__INSTITUTIONAL_EMAIL__</div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="background:#fff8f8;border-left:5px solid #c1121f;border-top:1px solid #e8caca;border-right:1px solid #e8caca;border-bottom:1px solid #e8caca;border-radius:8px;padding:14px 16px;">
                                    <div style="font-size:12px;color:#687387;font-weight:700;text-transform:uppercase;">Clave inicial</div>
                                    <div style="font-size:18px;font-weight:700;color:#c1121f;margin-top:6px;letter-spacing:.04em;">__PASSWORD__</div>
                                  </td>
                                </tr>
                              </table>
                              <div style="background:#f8fafc;border:1px solid #cbd5e1;border-radius:8px;color:#334155;font-size:14px;line-height:1.5;margin-top:18px;padding:14px 16px;">
                                Por seguridad, cambia esta clave despues del primer ingreso y no la compartas con otras personas.
                              </div>
                              <p style="font-size:14px;line-height:1.6;color:#687387;margin:22px 0 0;">Si tienes problemas para ingresar, comunicate con el area encargada de soporte academico.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#0b2347;color:#dce6f3;font-size:12px;line-height:1.5;padding:16px 28px;border-bottom:5px solid #c1121f;">
                              Este mensaje fue generado automaticamente por el sistema de Correos UAP.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("__NAME__", name)
                .replace("__LOGO_URL__", logo)
                .replace("__INSTITUTIONAL_EMAIL__", institutionalEmail)
                .replace("__PASSWORD__", password);
    }

    private String escapeHtml(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private CampaignStatus resolveCampaignStatus(int sent, int failed) {
        if (sent > 0 && failed == 0) {
            return CampaignStatus.SENT;
        }
        if (sent > 0) {
            return CampaignStatus.PARTIAL;
        }
        return CampaignStatus.FAILED;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
