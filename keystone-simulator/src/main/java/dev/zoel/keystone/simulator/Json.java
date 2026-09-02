package dev.zoel.keystone.simulator;

/**
 * A deliberately tiny JSON helper.
 *
 * No Jackson here on purpose: this module speaks to Keystone over the wire and
 * should carry as little as a real firmware image would. The payloads are three
 * fields wide, and pulling in a full object mapper to handle them would be the
 * kind of dependency that looks free and never is.
 */
final class Json {

    private Json() {
    }

    /** Escapes a Java string into a JSON string literal, quotes included. */
    static String string(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 16).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    static String object(String... keyValuePairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append(string(keyValuePairs[i])).append(':').append(string(keyValuePairs[i + 1]));
        }
        return out.append('}').toString();
    }

    /**
     * Reads one top-level string field. Enough for the three responses this client
     * consumes, and it unescapes the sequences {@link #string} produces.
     */
    static String stringField(String json, String field) {
        String needle = "\"" + field + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            throw new SimulationException("field '" + field + "' missing from the response");
        }
        int valueAt = json.indexOf('"', json.indexOf(':', keyAt + needle.length()) + 1);
        StringBuilder out = new StringBuilder();
        for (int i = valueAt + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        throw new SimulationException("unterminated string for field '" + field + "'");
    }
}
