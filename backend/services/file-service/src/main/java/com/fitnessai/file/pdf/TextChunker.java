package com.fitnessai.file.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted text into overlapping chunks for embedding. Prefers to
 * break on paragraph, then sentence, then word boundaries — the same
 * strategy as the Python version's RecursiveCharacterTextSplitter.
 */
public final class TextChunker {

    public static final int CHUNK_SIZE = 1000;
    public static final int OVERLAP = 150;

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, CHUNK_SIZE, OVERLAP);
    }

    static List<String> chunk(String text, int size, int overlap) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.replace("\r", "").split("\\n\\s*\\n")) {
            String p = paragraph.replaceAll("\\s+", " ").strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= size) {
                pieces.add(p);
            } else {
                splitLong(p, size, pieces);
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + 1 + piece.length() > size) {
                chunks.add(current.toString());
                current = new StringBuilder(tail(current.toString(), overlap));
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(piece);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static void splitLong(String paragraph, int size, List<String> out) {
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (sentence.length() > size) {
                flush(current, out);
                for (int i = 0; i < sentence.length(); i += size) {
                    out.add(sentence.substring(i, Math.min(sentence.length(), i + size)));
                }
                continue;
            }
            if (!current.isEmpty() && current.length() + 1 + sentence.length() > size) {
                flush(current, out);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        flush(current, out);
    }

    private static void flush(StringBuilder sb, List<String> out) {
        if (!sb.isEmpty()) {
            out.add(sb.toString());
            sb.setLength(0);
        }
    }

    /** Last ~overlap chars of a chunk, starting on a word boundary. */
    private static String tail(String s, int overlap) {
        if (s.length() <= overlap) {
            return s;
        }
        String t = s.substring(s.length() - overlap);
        int space = t.indexOf(' ');
        return space >= 0 ? t.substring(space + 1) : t;
    }
}
