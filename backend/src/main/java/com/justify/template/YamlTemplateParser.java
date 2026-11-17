package com.justify.template;

import com.justify.core.FieldSpec;
import com.justify.core.Template;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses and validates a template YAML. Uses SafeConstructor: arbitrary YAML types are refused. */
public final class YamlTemplateParser {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public Template parse(String yamlText, String sourceName) {
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(10);
            root = new Yaml(new SafeConstructor(options)).load(yamlText);
        } catch (YAMLException e) {
            throw new TemplateException(sourceName + ": invalid YAML: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new TemplateException(sourceName + ": top level must be a mapping");
        }
        String id = string(map, "id", sourceName, true);
        if (!ID.matcher(id).matches()) {
            throw new TemplateException(sourceName + ": id '" + id + "' must match " + ID.pattern());
        }
        String name = string(map, "name", sourceName, true);
        String description = string(map, "description", sourceName, false);

        if (!(map.get("fields") instanceof List<?> rawFields) || rawFields.isEmpty()) {
            throw new TemplateException(sourceName + ": 'fields' must be a non-empty list");
        }
        List<FieldSpec> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object raw : rawFields) {
            if (!(raw instanceof Map<?, ?> f)) {
                throw new TemplateException(sourceName + ": each field must be a mapping");
            }
            String key = string(f, "key", sourceName, true);
            if (!KEY.matcher(key).matches()) {
                throw new TemplateException(sourceName + ": field key '" + key + "' must match " + KEY.pattern());
            }
            if (!seen.add(key)) {
                throw new TemplateException(sourceName + ": duplicate field key '" + key + "'");
            }
            Object required = f.get("required");
            if (required != null && !(required instanceof Boolean)) {
                throw new TemplateException(sourceName + ": field '" + key + "': 'required' must be true or false");
            }
            fields.add(new FieldSpec(key, string(f, "label", sourceName, true), Boolean.TRUE.equals(required),
                    string(f, "question", sourceName, false), string(f, "description", sourceName, false),
                    string(f, "label_ar", sourceName, false), string(f, "question_ar", sourceName, false)));
        }
        if (fields.stream().noneMatch(FieldSpec::required)) {
            throw new TemplateException(sourceName + ": at least one field must be required");
        }
        return new Template(id, name, description, fields, string(map, "name_ar", sourceName, false));
    }

    private static String string(Map<?, ?> map, String key, String source, boolean mandatory) {
        Object value = map.get(key);
        if (value == null) {
            if (mandatory) {
                throw new TemplateException(source + ": '" + key + "' is required");
            }
            return null;
        }
        if (!(value instanceof String s)) {
            throw new TemplateException(source + ": '" + key + "' must be text");
        }
        if (mandatory && s.isBlank()) {
            throw new TemplateException(source + ": '" + key + "' must not be blank");
        }
        return s.strip();
    }
}
