package com.justify.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.core.Extraction;
import com.justify.core.Template;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Strict parsing of the model reply. Tolerates code fences and chatter around the JSON object,
 * rejects anything that is not the expected shape, and drops keys the template does not have (D7).
 */
public final class ExtractionParser {

    private final ObjectMapper mapper;

    public ExtractionParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @throws MalformedReplyException if the reply is not usable
     */
    public Extraction parse(String reply, Template template) {
        if (reply == null || reply.isBlank()) {
            throw new MalformedReplyException("empty reply");
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new MalformedReplyException("no JSON object in reply");
        }
        JsonNode root;
        try {
            root = mapper.readTree(reply.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new MalformedReplyException("invalid JSON: " + e.getOriginalMessage());
        }
        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || !fieldsNode.isObject()) {
            throw new MalformedReplyException("'fields' object missing");
        }
        Map<String, String> fields = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!template.hasField(e.getKey())) {
                continue; // invented key
            }
            JsonNode v = e.getValue();
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isTextual() || v.isNumber() || v.isBoolean()) {
                String s = v.asText().strip();
                if (!s.isEmpty()) {
                    fields.put(e.getKey(), s);
                }
            } else if (v.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : v) {
                    if (item.isValueNode() && !item.isNull()) {
                        if (!sb.isEmpty()) {
                            sb.append(", ");
                        }
                        sb.append(item.asText().strip());
                    }
                }
                if (!sb.isEmpty()) {
                    fields.put(e.getKey(), sb.toString());
                }
            } else {
                throw new MalformedReplyException("field '" + e.getKey() + "' is not a value");
            }
        }
        JsonNode clarityNode = root.get("clarity");
        int clarity;
        if (clarityNode != null && clarityNode.isNumber()) {
            clarity = (int) Math.round(clarityNode.asDouble());
        } else if (clarityNode != null && clarityNode.isTextual() && clarityNode.asText().strip().matches("\\d{1,2}")) {
            clarity = Integer.parseInt(clarityNode.asText().strip());
        } else {
            throw new MalformedReplyException("'clarity' must be a number");
        }
        if (clarity < 0 || clarity > 10) {
            throw new MalformedReplyException("'clarity' out of range: " + clarity);
        }
        return new Extraction(fields, clarity);
    }

    public static class MalformedReplyException extends RuntimeException {
        public MalformedReplyException(String message) {
            super(message);
        }
    }
}
