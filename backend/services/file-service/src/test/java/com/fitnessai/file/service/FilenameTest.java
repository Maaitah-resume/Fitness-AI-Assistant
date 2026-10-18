package com.fitnessai.file.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameTest {

    @Test
    void sanitizesForStorageAndHeaders() {
        assertThat(FileService.sanitizeFilename("My Plan (v2)!.PDF", "x")).isEqualTo("My Plan v2.pdf");
        assertThat(FileService.sanitizeFilename("../../etc/passwd", "x")).isEqualTo("....etcpasswd.pdf");
        assertThat(FileService.sanitizeFilename("..", "document")).isEqualTo("document.pdf");
        assertThat(FileService.sanitizeFilename("\"; rm -rf\r\n.pdf", "x")).isEqualTo("rm -rf.pdf");
        assertThat(FileService.sanitizeFilename(null, "document")).isEqualTo("document.pdf");
        assertThat(FileService.sanitizeFilename("a".repeat(300), "x")).hasSize(124);
        // Arabic (with diacritics) survives; path and quote characters still don't
        assertThat(FileService.sanitizeFilename("خطّتي الأسبوعية!", "x")).isEqualTo("خطّتي الأسبوعية.pdf");
        assertThat(FileService.sanitizeFilename("خطة/../\"x\".pdf", "x")).isEqualTo("خطة..x.pdf");
    }
}
