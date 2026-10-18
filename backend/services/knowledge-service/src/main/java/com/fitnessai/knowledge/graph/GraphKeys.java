package com.fitnessai.knowledge.graph;

import java.util.Locale;

final class GraphKeys {

    private GraphKeys() {
    }

    /** "Barbell Back-Squat" -> "barbell back squat". Also used on queries so matching is symmetric. */
    static String key(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Graph source tag: "<scope>|<documentId>", e.g. "user:7|doc-12". */
    static String source(String scope, String documentId) {
        return scope + "|" + documentId;
    }
}
