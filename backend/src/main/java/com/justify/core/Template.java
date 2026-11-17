package com.justify.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A request type (firewall rule, access request...) and the fields a good justification must contain. */
public record Template(String id, String name, String description, List<FieldSpec> fields, String nameAr) {

    public Template {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (description == null) {
            description = "";
        }
        fields = List.copyOf(fields);
        if (nameAr != null && nameAr.isBlank()) {
            nameAr = null;
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("template " + id + " has no fields");
        }
    }

    public Template(String id, String name, String description, List<FieldSpec> fields) {
        this(id, name, description, fields, null);
    }

    public String name(Language lang) {
        return lang == Language.AR && nameAr != null ? nameAr : name;
    }

    public Optional<FieldSpec> field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    public boolean hasField(String key) {
        return field(key).isPresent();
    }

    public List<FieldSpec> requiredFields() {
        return fields.stream().filter(FieldSpec::required).toList();
    }
}
