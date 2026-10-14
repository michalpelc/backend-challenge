package com.example.prmonitor.domain.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequest(
        long id,
        long number,
        String title,
        String state,
        boolean draft
) {
}
