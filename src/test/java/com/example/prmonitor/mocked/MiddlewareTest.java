package com.example.prmonitor.mocked;

import com.example.prmonitor.domain.middleware.MiddlewareResponse;
import com.example.prmonitor.domain.middleware.MiddlewareResponse.MiddlewarePullRequest;
import com.example.prmonitor.validation.middleware.MiddlewareValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiddlewareTest {

    private static MiddlewareResponse middlewareResponse;
    private final MiddlewareValidator middlewareValidator = new MiddlewareValidator();

    @BeforeAll
    static void loadFixture() throws IOException {
        var stream = MiddlewareTest.class.getResourceAsStream("/fixtures/middleware-valid.json");
        var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        middlewareResponse = new ObjectMapper().readValue(json, MiddlewareResponse.class);
    }

    @Test
    @DisplayName("Accept the supplied middleware response")
    void shouldAcceptSuppliedPayload() {
        // Verify that the supplied JSON satisfies both business rules:
        // total_open_prs matches the PR array size, and no high-priority PR is a draft.
        // The test passes if validation completes without throwing any exception.
        assertThatCode(() -> middlewareValidator.validateIntegrityAndBusinessRule(middlewareResponse)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Reject a mismatch between total_open_prs and array size")
    void shouldFailWithClearMessageWhenTotalDoesNotMatchArrayLength() {
        // Get actual number of PRs and intentionally increment to create a mismatch
        var actualCount = middlewareResponse.pullRequests().size();
        var invalidCount = actualCount + 1;

        // Create invalid response while keeping the original PR list not touched
        var invalid = new MiddlewareResponse(
                middlewareResponse.productId(),
                invalidCount,
                middlewareResponse.lastUpdated(),
                middlewareResponse.pullRequests()
        );

        // Verify that the validator detects the mismatch and provides a clear error message
        assertThatThrownBy(() ->
                middlewareValidator.validateIntegrityAndBusinessRule(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Integrity violation")
                .hasMessageContaining("total_open_prs=" + invalidCount)
                .hasMessageContaining("contains " + actualCount + " items");
    }


    @Test
    @DisplayName("Reject a high-priority draft")
    void shouldRejectHighPriorityDraft() {
        // Copy existing PRs and add an invalid high-priority draft
        var prs = new ArrayList<>(middlewareResponse.pullRequests());
        var newPrId = prs.stream().mapToLong(MiddlewarePullRequest::id).max().orElse(0) + 1;

        prs.add(new MiddlewarePullRequest(newPrId, "High-priority draft",
                new MiddlewarePullRequest.Author("test-user", "developer"), "OPEN", List.of("high-priority"),
                new MiddlewarePullRequest.Meta(true, 0)
        ));

        // Update the total count to avoid an integrity violation
        var invalid = new MiddlewareResponse(middlewareResponse.productId(), prs.size(), middlewareResponse.lastUpdated(), prs);

        // Verify the business rule violation and error message
        assertThatThrownBy(() -> middlewareValidator.validateIntegrityAndBusinessRule(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PR id=" + newPrId)
                .hasMessageContaining("high-priority")
                .hasMessageContaining("meta.is_draft=true");
    }


}
