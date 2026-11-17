package com.justify.api;

import com.justify.core.Template;
import com.justify.service.JustificationService;
import com.justify.template.TemplateRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates")
public class TemplateController {

    private final TemplateRegistry registry;
    private final JustificationService service;

    public TemplateController(TemplateRegistry registry, JustificationService service) {
        this.registry = registry;
        this.service = service;
    }

    public record FieldView(String key, String label, boolean required, String question, String description) {
    }

    public record TemplateView(String id, String name, String description, List<FieldView> fields) {
        static TemplateView of(Template t) {
            return new TemplateView(t.id(), t.name(), t.description(), t.fields().stream()
                    .map(f -> new FieldView(f.key(), f.label(), f.required(), f.question(), f.description()))
                    .toList());
        }
    }

    @GetMapping
    @Operation(summary = "Available request types")
    public List<TemplateView> list() {
        return registry.all().stream().map(TemplateView::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One request type and its fields")
    public TemplateView get(@PathVariable String id) {
        return TemplateView.of(service.template(id));
    }
}
