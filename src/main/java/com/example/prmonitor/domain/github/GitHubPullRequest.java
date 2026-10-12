package com.example.prmonitor.domain.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequest(
        long id,
        String state,
        boolean draft
) {
}
