package com.example.prmonitor.domain.middleware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiddlewareResponse(
        @JsonProperty("product_id") String productId,
        @JsonProperty("total_open_prs") int totalOpenPrs,
        @JsonProperty("last_updated") String lastUpdated,
        @JsonProperty("pull_requests") List<MiddlewarePullRequest> pullRequests) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MiddlewarePullRequest(
            long id,
            String title,
            Author author,
            String status,
            List<String> labels,
            Meta meta) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Author(
                String username,
                String role) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Meta(
                @JsonProperty("is_draft") boolean draft,
                @JsonProperty("review_comments") int reviewComments) {
        }
    }
}
