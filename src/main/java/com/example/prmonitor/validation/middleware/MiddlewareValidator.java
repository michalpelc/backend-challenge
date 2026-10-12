package com.example.prmonitor.validation.middleware;

import com.example.prmonitor.domain.middleware.MiddlewareResponse;

public final class MiddlewareValidator {

    public void validateIntegrityAndBusinessRule(MiddlewareResponse response) {

        // Validates that total_open_prs matches the number of pull requests and that no high-priority PR is marked as draft,
        // providing a clear error message on failure.

        var actual = response.pullRequests().size();

        if (response.totalOpenPrs() != actual) {
            throw new IllegalStateException("Integrity violation for product %s: total_open_prs=%d but pull_requests contains %d items".formatted(response.productId(), response.totalOpenPrs(), actual));
        }

        response.pullRequests().stream()
                .filter(pr -> pr.labels() != null
                        && pr.labels().contains("high-priority")
                        && pr.meta().draft())
                .findFirst()
                .ifPresent(pr -> {
                    throw new IllegalStateException(
                            "Business rule violation: PR id=%d ('%s') has label 'high-priority' but meta.is_draft=true".formatted(pr.id(), pr.title()));
                });
    }

}
