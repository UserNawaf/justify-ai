package com.justify.firewall;

import com.justify.api.AnswersRequest;
import com.justify.core.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firewall-requests")
@Tag(name = "Firewall request portal")
public class FirewallRequestController {

    private final FirewallRequestService service;

    public FirewallRequestController(FirewallRequestService service) {
        this.service = service;
    }

    public record DecisionRequest(
            @Schema(example = "APPROVED") @NotNull RequestStatus decision,
            @Schema(description = "Network engineer's name (from SSO later)", example = "Sara (Network team)")
            @NotBlank @Size(max = 120) String engineer,
            @Schema(description = "Required when rejecting", example = "Use the existing proxy instead")
            @Size(max = 1000) String note) {
    }

    public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {
        static <T> PageView<T> of(Page<T> p) {
            return new PageView<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    private static Language lang(String lang, String acceptLanguage) {
        return Language.parse(lang != null && !lang.isBlank() ? lang : acceptLanguage);
    }

    @PostMapping
    @Operation(summary = "Submit a firewall request",
            description = "Technical fields are validated (400 with `errors` per field). The justification is checked "
                    + "by AI; open questions come back in `justification.questions`.")
    public ResponseEntity<FirewallRequestView> create(@Valid @RequestBody FirewallForm form,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        FirewallRequestView v = service.create(form, lang(lang, acceptLanguage));
        return ResponseEntity.created(URI.create("/api/v1/firewall-requests/" + v.id())).body(v);
    }

    @GetMapping
    @Operation(summary = "List requests, newest first",
            description = "Filters: status, employeeId (\"my requests\"), q (ticket, requester, host or IP).")
    public PageView<FirewallRequestView.Summary> list(@RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String employeeId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageView.of(service.search(status, employeeId, q, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One request with its justification")
    public FirewallRequestView get(@PathVariable UUID id, @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return service.get(id, lang(lang, acceptLanguage));
    }

    @GetMapping("/ticket/{ticketNo}")
    @Operation(summary = "Find a request by ticket number, e.g. FW-2026-00001")
    public FirewallRequestView byTicket(@PathVariable String ticketNo, @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return service.getByTicket(ticketNo, lang(lang, acceptLanguage));
    }

    @PostMapping("/{id}/answers")
    @Operation(summary = "Requester answers the justification questions")
    public FirewallRequestView answer(@PathVariable UUID id, @Valid @RequestBody AnswersRequest request,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return service.answer(id, request.answers(), lang(lang, acceptLanguage));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Network engineer approves or rejects (only PENDING_REVIEW)")
    public FirewallRequestView decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return service.decide(id, request.decision(), request.engineer(), request.note(), lang(lang, acceptLanguage));
    }
}
