package com.fitnessai.file.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfUtilitiesTest {

    @Test
    void shortTextIsOneChunk() {
        assertThat(TextChunker.chunk("Squats.\n\nLunges.")).containsExactly("Squats. Lunges.");
        assertThat(TextChunker.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void longTextSplitsWithinLimitAndOverlaps() {
        String sentence = "The squat trains the quadriceps, glutes and adductors effectively. ";
        String text = sentence.repeat(60);  // ~4k chars, one paragraph
        List<String> chunks = TextChunker.chunk(text, 1000, 150);

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(1000 + 150 + 1));
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 60);
        assertThat(chunks.get(1)).contains(tailOfFirst.substring(tailOfFirst.indexOf(' ') + 1));
    }

    @Test
    void unbrokenRunsAreHardSplit() {
        assertThat(TextChunker.chunk("x".repeat(2500), 1000, 0)).hasSize(3);
    }

    @Test
    void typographyIsNormalized() {
        assertThat(PdfRenderer.normalizeTypography("“week 1”…\tgo"))
                .isEqualTo("\"week 1\"...    go");
        assertThat(PdfRenderer.normalizeTypography("a\u0007b")).isEqualTo("ab");
    }

    @Test
    void arabicIsShapedThenReordered() {
        String salam = "سلام";  // سلام: seen, lam, alef, meem
        String shaped = PdfRenderer.shape(salam);
        // contextual forms + the lam-alef ligature, all in the presentation-forms block
        assertThat(shaped.codePoints()).allMatch(cp -> cp >= 0xFB50 && cp <= 0xFEFF);
        assertThat(shaped).hasSizeLessThan(salam.length());  // lam+alef collapse into one glyph

        assertThat(PdfRenderer.isRtl(salam)).isTrue();
        assertThat(PdfRenderer.isRtl("squat " + salam)).isFalse();  // base direction from the first strong char
        // visual order for an RTL line is the reverse of reading order
        assertThat(PdfRenderer.visual(shaped, true)).isEqualTo(new StringBuilder(shaped).reverse().toString());
        // Latin runs inside an RTL line keep their own left-to-right order
        assertThat(PdfRenderer.visual(shaped + " 3x8", true)).startsWith("3x8 ");
        assertThat(PdfRenderer.visual("Squat 3x8", false)).isEqualTo("Squat 3x8");
    }

    @Test
    void rendersArabicAndMixedTextWithEmbeddedFont() throws Exception {
        byte[] pdf = new PdfRenderer().render("خطة التمارين الأسبوعية", List.of(
                new PdfRenderer.Section("اليوم الأول", "- تمرين القرفصاء 3x8\n- الضغط على الصدر 4x6"),
                new PdfRenderer.Section("Day 2 — Mixed", "Squat then تمرين الضغط, rest 90s."),
                new PdfRenderer.Section("Unsupported", "Strong 💪 finish")));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<String> fonts = new ArrayList<>();
            for (var name : doc.getPage(0).getResources().getFontNames()) {
                PDFont f = doc.getPage(0).getResources().getFont(name);
                fonts.add(f.getName());
            }
            assertThat(fonts).anyMatch(n -> n.contains("DejaVuSans"));

            // Extraction undoes shaping and reordering, so text round-trips exactly —
            // which is what makes uploaded Arabic PDFs searchable in chat.
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertThat(text)
                    .contains("خطة التمارين الأسبوعية")
                    .contains("اليوم الأول")
                    .contains("3x8 تمرين القرفصاء")
                    .contains("Squat then تمرين الضغط, rest 90s.")
                    .contains("Day 2 — Mixed")
                    .contains("Strong ? finish");

            // Set -Dpdf.preview.dir=<dir> to write a PNG for eyeballing the layout.
            String preview = System.getProperty("pdf.preview.dir");
            if (preview != null) {
                ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png",
                        new File(preview, "arabic-preview.png"));
            }
        }
    }

    @Test
    void rendersMultiPageDocuments() throws Exception {
        String longBody = ("- Superset A: 3 sets of 12 reps, rest 60 seconds between rounds\n").repeat(120);
        byte[] pdf = new PdfRenderer().render("Big plan", List.of(new PdfRenderer.Section("All", longBody)));
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
    }
}
