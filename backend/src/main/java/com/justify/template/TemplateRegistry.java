package com.justify.template;

import com.justify.core.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Bundled templates ({@code classpath:templates/*.yml}) plus an optional external folder, loaded once at start-up.
 * External templates override bundled ones with the same id (D8).
 */
public class TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistry.class);

    private final Map<String, Template> templates;

    public TemplateRegistry(Map<String, Template> templates) {
        this.templates = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(templates));
    }

    public static TemplateRegistry load(YamlTemplateParser parser, String externalDir) {
        Map<String, Template> byId = new LinkedHashMap<>();
        try {
            Resource[] bundled = new PathMatchingResourcePatternResolver().getResources("classpath*:templates/*.yml");
            for (Resource r : bundled) {
                try (InputStream in = r.getInputStream()) {
                    Template t = parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), r.getFilename());
                    byId.put(t.id(), t);
                }
            }
        } catch (IOException e) {
            throw new TemplateException("cannot read bundled templates", e);
        }
        if (externalDir != null && !externalDir.isBlank()) {
            Path dir = Path.of(externalDir);
            if (!Files.isDirectory(dir)) {
                throw new TemplateException("TEMPLATES_DIR is not a directory: " + dir);
            }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new TemplateException("cannot list " + dir, e);
            }
            for (Path p : files) {
                try {
                    Template t = parser.parse(Files.readString(p), p.getFileName().toString());
                    if (byId.containsKey(t.id())) {
                        log.info("External template {} overrides bundled one", t.id());
                    }
                    byId.put(t.id(), t);
                } catch (IOException e) {
                    throw new TemplateException("cannot read " + p, e);
                }
            }
        }
        if (byId.isEmpty()) {
            throw new TemplateException("no templates found");
        }
        log.info("Loaded templates: {}", byId.keySet());
        return new TemplateRegistry(byId);
    }

    public Optional<Template> find(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<Template> all() {
        return templates.values();
    }
}
