package com.canet.app.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonFlattener {

    /**
     * Recursively flattens nested Map values using {@code separator} between key segments.
     * Null values and empty strings are omitted from the result.
     * Example: {b:{c:2,d:3}} with separator "-" → {b-c:2, b-d:3}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> flatten(Map<String, Object> source, String separator) {
        Map<String, Object> out = new LinkedHashMap<>();
        flattenInto("", source, separator, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flattenInto(String prefix, Map<String, Object> map,
                                     String separator, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + separator + e.getKey();
            Object val = e.getValue();
            if (val instanceof Map) {
                flattenInto(key, (Map<String, Object>) val, separator, out);
            } else if (val != null && !val.toString().isEmpty()) {
                out.put(key, val);
            }
        }
    }
}
