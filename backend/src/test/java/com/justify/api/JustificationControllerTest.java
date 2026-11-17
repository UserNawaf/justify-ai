package com.justify.api;

import com.justify.core.AiUnavailableException;
import com.justify.core.Status;
import com.justify.service.ConflictException;
import com.justify.service.JustificationService;
import com.justify.service.NotFoundException;
import com.justify.service.SubmissionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JustificationController.class)
class JustificationControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JustificationService service;

    private static SubmissionSnapshot snapshot(Status status, boolean created) {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");
        return new SubmissionSnapshot(ID, "firewall-rule", "web", null, status, 1, 3,
                status == Status.NEEDS_INFO ? Map.of("duration", "Is this rule permanent or temporary?") : Map.of(),
                status == Status.NEEDS_INFO ? List.of("duration") : List.of(),
                Map.of("port_protocol", "TCP 443"),
                status == Status.NEEDS_INFO ? null : "Request type: Firewall rule request",
                7.2, 0.833, 6, now, now, null, created);
    }

    @Test
    void submitReturns201WithQuestions() throws Exception {
        when(service.submit(eq("firewall-rule"), any(), any(), any(), any())).thenReturn(snapshot(Status.NEEDS_INFO, true));

        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"firewall-rule\",\"text\":\"open 443 to the bank\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/justifications/" + ID))
                .andExpect(jsonPath("$.status").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.questions.duration").value("Is this rule permanent or temporary?"))
                .andExpect(jsonPath("$.standardizedText").doesNotExist());
    }

    @Test
    void duplicateSubmitReturns200() throws Exception {
        when(service.submit(any(), any(), any(), any(), any())).thenReturn(snapshot(Status.COMPLETE, false));

        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"firewall-rule\",\"text\":\"x\",\"source\":\"jira\",\"externalRef\":\"OPS-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standardizedText").value("Request type: Firewall rule request"));
    }

    @Test
    void invalidInputIs400ProblemJson() throws Exception {
        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"\",\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"firewall-rule\",\"text\":\"x\",\"source\":\"bad source!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/justifications/" + ID + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/justifications/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownAnswerFieldIs400() throws Exception {
        when(service.answer(eq(ID), anyMap(), any())).thenThrow(new IllegalArgumentException("unknown field: colour"));

        mvc.perform(post("/api/v1/justifications/" + ID + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"colour\":\"blue\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("unknown field: colour"));
    }

    @Test
    void languageComesFromQueryThenHeader() throws Exception {
        when(service.get(eq(ID), eq(com.justify.core.Language.AR))).thenReturn(snapshot(Status.NEEDS_INFO, false));

        mvc.perform(get("/api/v1/justifications/" + ID).header("Accept-Language", "ar-SA,ar;q=0.9"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/justifications/" + ID + "?lang=ar").header("Accept-Language", "en"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownSubmissionIs404() throws Exception {
        when(service.get(eq(ID), any())).thenThrow(new NotFoundException("submission not found"));

        mvc.perform(get("/api/v1/justifications/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void closedSubmissionIs409() throws Exception {
        when(service.answer(eq(ID), anyMap(), any())).thenThrow(new ConflictException("submission is already COMPLETE"));

        mvc.perform(post("/api/v1/justifications/" + ID + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"duration\":\"Permanent\"}}"))
                .andExpect(status().isConflict());
    }

    @Test
    void modelDownIs503WithoutInternalDetail() throws Exception {
        when(service.submit(any(), any(), any(), any(), any()))
                .thenThrow(new AiUnavailableException("ollama/llama3.1:8b at http://internal-host:11434 refused"));

        mvc.perform(post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"firewall-rule\",\"text\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("internal-host"))))
                .andExpect(content().string(not(containsString("llama"))));
    }
}
