package com.justify.api;

import com.justify.service.JustificationService;
import com.justify.service.SubmissionSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/justifications")
@Tag(name = "Justifications")
public class JustificationController {

    private final JustificationService service;

    public JustificationController(JustificationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a free-text justification",
            description = "Returns NEEDS_INFO with questions, or COMPLETE with the standardised text. "
                    + "201 when created, 200 when source+externalRef already existed.")
    public ResponseEntity<JustificationResponse> submit(@Valid @RequestBody SubmitRequest request) {
        SubmissionSnapshot s = service.submit(request.templateId(), request.text(), request.source(),
                request.externalRef());
        JustificationResponse body = JustificationResponse.of(s);
        if (s.created()) {
            return ResponseEntity.created(URI.create("/api/v1/justifications/" + s.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/answers")
    @Operation(summary = "Answer the questions of the current round")
    public JustificationResponse answer(@PathVariable UUID id, @Valid @RequestBody AnswersRequest request) {
        return JustificationResponse.of(service.answer(id, request.answers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Current state of a submission")
    public JustificationResponse get(@PathVariable UUID id) {
        return JustificationResponse.of(service.get(id));
    }

}
