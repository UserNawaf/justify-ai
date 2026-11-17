package com.justify.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

    private MockHttpServletResponse run(ApiKeyFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (chain.getRequest() != null) {
            response.setStatus(200);
        }
        return response;
    }

    @Test
    void offWhenNoKeyConfigured() throws Exception {
        assertThat(run(new ApiKeyFilter(""), new MockHttpServletRequest("POST", "/api/v1/justifications"))
                .getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingOrWrongKey() throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter("s3cret");
        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", "/api/v1/justifications");
        wrong.addHeader(ApiKeyFilter.HEADER, "nope");

        assertThat(run(filter, new MockHttpServletRequest("POST", "/api/v1/justifications")).getStatus()).isEqualTo(401);
        MockHttpServletResponse r = run(filter, wrong);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentType()).isEqualTo("application/problem+json");
    }

    @Test
    void acceptsCorrectKey() throws Exception {
        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/api/v1/templates");
        ok.addHeader(ApiKeyFilter.HEADER, "s3cret");

        assertThat(run(new ApiKeyFilter("s3cret"), ok).getStatus()).isEqualTo(200);
    }

    @Test
    void corsPreflightAndNonApiPathsPass() throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter("s3cret");

        assertThat(run(filter, new MockHttpServletRequest("OPTIONS", "/api/v1/justifications")).getStatus()).isEqualTo(200);
        assertThat(run(filter, new MockHttpServletRequest("GET", "/actuator/health")).getStatus()).isEqualTo(200);
        assertThat(run(filter, new MockHttpServletRequest("GET", "/swagger-ui.html")).getStatus()).isEqualTo(200);
    }
}
