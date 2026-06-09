package com.uap.correosUAP.dto;

import java.util.List;

public record ContactImportResult(int created, int updated, int skipped, List<String> errors) {
}
