package com.justify.api;

import com.justify.service.JustificationService;
import com.justify.service.Stats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats")
public class StatsController {

    private final JustificationService service;

    public StatsController(JustificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Quick numbers. Full analytics: Power BI.")
    public Stats stats() {
        return service.stats();
    }
}
