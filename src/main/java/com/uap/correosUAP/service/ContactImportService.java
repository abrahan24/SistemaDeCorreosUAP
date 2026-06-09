package com.uap.correosUAP.service;

import com.uap.correosUAP.dto.ContactImportResult;
import com.uap.correosUAP.model.Contact;
import com.uap.correosUAP.repository.ContactRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContactImportService {

    private static final int MAX_ERRORS = 20;

    private final ContactRepository contactRepository;
    private final InstitutionalEmailService institutionalEmailService;

    public ContactImportService(ContactRepository contactRepository, InstitutionalEmailService institutionalEmailService) {
        this.contactRepository = contactRepository;
        this.institutionalEmailService = institutionalEmailService;
    }

    @Transactional
    public ContactImportResult importContacts(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un archivo CSV, XLS o XLSX.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<ContactRow> rows;
        if (filename.endsWith(".csv")) {
            rows = parseCsv(file);
        } else if (filename.endsWith(".xls") || filename.endsWith(".xlsx")) {
            rows = parseExcel(file);
        } else {
            throw new IllegalArgumentException("Formato no soportado. Use CSV, XLS o XLSX.");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (ContactRow row : rows) {
            String email = normalizeEmail(row.email());
            String firstName = clean(row.firstName());
            String lastName = clean(row.lastName());

            if (email != null && !isValidEmail(email)) {
                skipped++;
                addError(errors, "Fila " + row.rowNumber() + ": correo invalido.");
                continue;
            }

            if (email == null && firstName == null && lastName == null) {
                skipped++;
                addError(errors, "Fila " + row.rowNumber() + ": falta correo, nombre o apellido.");
                continue;
            }

            Contact contact = email == null ? null : contactRepository.findByEmailIgnoreCase(email).orElse(null);
            if (contact == null) {
                contact = new Contact(email, firstName, lastName, row.source());
                institutionalEmailService.assignGeneratedEmail(contact);
                contactRepository.save(contact);
                created++;
            } else {
                contact.setFirstName(firstName);
                contact.setLastName(lastName);
                contact.setSource(row.source());
                contact.setActive(true);
                institutionalEmailService.assignGeneratedEmail(contact);
                updated++;
            }
        }

        return new ContactImportResult(created, updated, skipped, errors);
    }

    private List<ContactRow> parseCsv(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        char delimiter = chooseDelimiter(content);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setDelimiter(delimiter)
                .get();

        List<ContactRow> rows = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(content))) {
            for (CSVRecord record : parser) {
                rows.add(new ContactRow(
                        record.getRecordNumber() + 1,
                        value(record, "email", "correo", "correo electronico", "e-mail"),
                        value(record, "nombre", "nombres", "first_name", "firstname", "name"),
                        value(record, "apellido", "apellidos", "last_name", "lastname"),
                        "csv"
                ));
            }
        }
        return rows;
    }

    private List<ContactRow> parseExcel(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = readHeaders(sheet.getRow(0), formatter);
            int emailColumn = firstIndex(headers, "email", "correo", "correo electronico", "e-mail");
            int firstNameColumn = firstIndex(headers, "nombre", "nombres", "first_name", "firstname", "name");
            int lastNameColumn = firstIndex(headers, "apellido", "apellidos", "last_name", "lastname");
            boolean hasKnownHeaders = emailColumn >= 0 || firstNameColumn >= 0 || lastNameColumn >= 0;
            int startRow = hasKnownHeaders ? 1 : 0;

            List<ContactRow> rows = new ArrayList<>();
            for (int index = startRow; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                rows.add(new ContactRow(
                        index + 1,
                        emailColumn >= 0 ? cell(row, emailColumn, formatter) : "",
                        firstNameColumn >= 0 ? cell(row, firstNameColumn, formatter) : "",
                        lastNameColumn >= 0 ? cell(row, lastNameColumn, formatter) : "",
                        "excel"
                ));
            }
            return rows;
        }
    }

    private Map<String, Integer> readHeaders(Row row, DataFormatter formatter) {
        Map<String, Integer> headers = new HashMap<>();
        if (row == null) {
            return headers;
        }
        for (int index = 0; index < row.getLastCellNum(); index++) {
            String value = normalizeHeader(formatter.formatCellValue(row.getCell(index)));
            if (!value.isBlank()) {
                headers.put(value, index);
            }
        }
        return headers;
    }

    private int firstIndex(Map<String, Integer> headers, String... names) {
        for (String name : names) {
            Integer index = headers.get(normalizeHeader(name));
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    private String value(CSVRecord record, String... names) {
        for (String name : names) {
            if (record.isMapped(name)) {
                return record.get(name);
            }
        }
        return "";
    }

    private String cell(Row row, int column, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private char chooseDelimiter(String content) {
        int end = content.indexOf('\n');
        String firstLine = end >= 0 ? content.substring(0, end) : content;
        long semicolons = firstLine.chars().filter(value -> value == ';').count();
        long commas = firstLine.chars().filter(value -> value == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    private String normalizeEmail(String email) {
        String cleaned = clean(email);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
            return email.contains("@") && email.contains(".");
        } catch (AddressException ex) {
            return false;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void addError(List<String> errors, String error) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(error);
        }
    }

    private record ContactRow(long rowNumber, String email, String firstName, String lastName, String source) {
    }
}
