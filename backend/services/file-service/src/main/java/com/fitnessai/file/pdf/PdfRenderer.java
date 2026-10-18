package com.fitnessai.file.pdf;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a titled, sectioned plain-text document (what the chat's
 * generateDocument tool produces) to an A4 PDF: wrapped text, bullet lines,
 * automatic page breaks, page numbers.
 *
 * Supports Latin and Arabic, including mixed lines. PDFBox draws glyphs
 * exactly as given, so Arabic needs two steps first: shaping (letters take
 * initial/medial/final forms; lam-alef ligatures) and bidi reordering into
 * visual order. Both are done with ICU. Text is wrapped after shaping (widths
 * depend on the shaped forms) but before reordering (wrapping must follow
 * reading order). Right-to-left paragraphs are right-aligned.
 *
 * Font: bundled DejaVu Sans (Latin + Arabic, incl. presentation forms),
 * embedded as a subset. Characters it lacks (e.g. emoji, CJK) become '?'.
 */
@Component
public class PdfRenderer {

    public record Section(String heading, String body) {
    }

    private static final byte[] REGULAR_TTF = resource("/fonts/DejaVuSans.ttf");
    private static final byte[] BOLD_TTF = resource("/fonts/DejaVuSans-Bold.ttf");

    private static final float MARGIN = 56;
    private static final float TITLE_SIZE = 20;
    private static final float HEADING_SIZE = 14;
    private static final float BODY_SIZE = 11;
    private static final float LEADING = 1.45f;
    private static final float BULLET_INDENT = 16;
    private static final String BULLET = "•";

    public byte[] render(String title, List<Section> sections) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);
            info.setCreator("Fitness AI Assistant");
            info.setCreationDate(Calendar.getInstance());

            Fonts fonts = new Fonts(
                    PDType0Font.load(doc, new ByteArrayInputStream(REGULAR_TTF), true),
                    PDType0Font.load(doc, new ByteArrayInputStream(BOLD_TTF), true));
            Writer w = new Writer(doc, fonts);
            w.paragraph(title, fonts.bold, TITLE_SIZE);
            w.space(BODY_SIZE);
            for (Section s : sections) {
                if (s.heading() != null && !s.heading().isBlank()) {
                    w.space(BODY_SIZE * 0.6f);
                    w.paragraph(s.heading().strip(), fonts.bold, HEADING_SIZE);
                    w.space(BODY_SIZE * 0.3f);
                }
                if (s.body() != null) {
                    for (String line : s.body().replace("\r", "").split("\n")) {
                        String l = line.strip();
                        if (l.isEmpty()) {
                            w.space(BODY_SIZE * 0.6f);
                        } else if (l.startsWith("- ") || l.startsWith("* ") || l.startsWith(BULLET + " ")) {
                            w.bullet(l.substring(2).strip(), fonts.regular, BODY_SIZE);
                        } else {
                            w.paragraph(l, fonts.regular, BODY_SIZE);
                        }
                    }
                }
            }
            w.finish();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Text preparation ────────────────────────────────────────────────────

    /** Typographic punctuation the model likes to emit, mapped to plain forms; control chars dropped. */
    static String normalizeTypography(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace("…", "...").replace(' ', ' ').replace("\t", "    ")
                .replaceAll("\\p{Cc}", "");
    }

    static boolean isRtl(String text) {
        return Bidi.getBaseDirection(text) == Bidi.RTL;
    }

    /** Arabic letters -> contextual presentation forms, in logical (reading) order. */
    static String shape(String text) {
        try {
            return new ArabicShaping(ArabicShaping.LETTERS_SHAPE | ArabicShaping.LENGTH_GROW_SHRINK
                    | ArabicShaping.TEXT_DIRECTION_LOGICAL).shape(text);
        } catch (ArabicShapingException e) {
            return text;
        }
    }

    /** One wrapped line, from logical to visual (left-to-right drawing) order. */
    static String visual(String logicalLine, boolean rtl) {
        Bidi bidi = new Bidi(logicalLine, rtl ? Bidi.RTL : Bidi.LTR);
        return bidi.writeReordered(Bidi.DO_MIRRORING);
    }

    private record Fonts(PDFont regular, PDFont bold) {
    }

    /** Laid-out paragraph: visual-order lines plus their widths and direction. */
    private record Laid(List<String> lines, List<Float> widths, boolean rtl) {
    }

    // ── Page writer ─────────────────────────────────────────────────────────

    private static final class Writer {
        private final PDDocument doc;
        private final Fonts fonts;
        private final float left = MARGIN;
        private final float right = PDRectangle.A4.getWidth() - MARGIN;
        private final Map<PDFont, Map<Integer, Boolean>> encodable = new HashMap<>();
        private PDPageContentStream stream;
        private float y;
        private int pageNo;

        Writer(PDDocument doc, Fonts fonts) throws IOException {
            this.doc = doc;
            this.fonts = fonts;
            newPage();
        }

        void paragraph(String text, PDFont font, float size) throws IOException {
            Laid laid = layout(text, font, size, right - left);
            for (int i = 0; i < laid.lines().size(); i++) {
                ensureRoom(size * LEADING);
                float x = laid.rtl() ? right - laid.widths().get(i) : left;
                write(laid.lines().get(i), font, size, x);
            }
        }

        void bullet(String text, PDFont font, float size) throws IOException {
            Laid laid = layout(text, font, size, right - left - BULLET_INDENT);
            float bulletWidth = width(BULLET, font, size);
            for (int i = 0; i < laid.lines().size(); i++) {
                ensureRoom(size * LEADING);
                float textX = laid.rtl()
                        ? right - BULLET_INDENT - laid.widths().get(i)
                        : left + BULLET_INDENT;
                if (i == 0) {
                    float bulletX = laid.rtl() ? right - bulletWidth - 2 : left + 2;
                    write(BULLET, font, size, bulletX);
                    y += size * LEADING;  // bullet and first line share a baseline
                }
                write(laid.lines().get(i), font, size, textX);
            }
        }

        void space(float points) {
            y -= points;
        }

        void finish() throws IOException {
            footer();
            stream.close();
        }

        /**
         * normalize -> detect direction -> shape -> replace unrenderable
         * glyphs -> wrap (logical order) -> reorder each line to visual.
         */
        private Laid layout(String text, PDFont font, float size, float maxWidth) throws IOException {
            String logical = normalizeTypography(text);
            boolean rtl = isRtl(logical);
            String shaped = renderable(shape(logical), font);

            List<String> logicalLines = wrap(shaped, font, size, maxWidth);
            List<String> lines = new ArrayList<>(logicalLines.size());
            List<Float> widths = new ArrayList<>(logicalLines.size());
            for (String l : logicalLines) {
                String v = visual(l, rtl);
                lines.add(v);
                widths.add(width(v, font, size));
            }
            return new Laid(lines, widths, rtl);
        }

        private String renderable(String text, PDFont font) {
            Map<Integer, Boolean> cache = encodable.computeIfAbsent(font, f -> new HashMap<>());
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                boolean ok = cache.computeIfAbsent(cp, c -> canEncode(font, c));
                sb.append(ok ? Character.toString(cp) : "?");
            });
            return sb.toString();
        }

        private static boolean canEncode(PDFont font, int cp) {
            try {
                font.encode(Character.toString(cp));
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        }

        private void write(String line, PDFont font, float size, float x) throws IOException {
            y -= size * LEADING;
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(line);
            stream.endText();
        }

        private void ensureRoom(float needed) throws IOException {
            if (y - needed < MARGIN + 20) {
                footer();
                stream.close();
                newPage();
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
            pageNo++;
        }

        private void footer() throws IOException {
            String label = "Page " + pageNo;
            float x = right - width(label, fonts.regular, 9);
            stream.beginText();
            stream.setFont(fonts.regular, 9);
            stream.newLineAtOffset(x, MARGIN / 2);
            stream.showText(label);
            stream.endText();
        }

        /** Greedy word wrap on logical-order text; words wider than a line are hard-broken. */
        private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String w = word;
                while (width(w, font, size) > maxWidth && w.length() > 1) {
                    int cut = w.length() - 1;
                    while (cut > 1 && width(w.substring(0, cut), font, size) > maxWidth) {
                        cut--;
                    }
                    lines.add(w.substring(0, cut));
                    w = w.substring(cut);
                }
                line.append(w);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static float width(String s, PDFont font, float size) throws IOException {
            return font.getStringWidth(s) / 1000 * size;
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = PdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled font " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
