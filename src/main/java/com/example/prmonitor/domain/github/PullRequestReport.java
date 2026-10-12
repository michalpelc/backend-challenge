package com.example.prmonitor.domain.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestReport(
        List<GitHubPullRequest> allPullRequests,
        List<GitHubPullRequest> eligiblePullRequests,
        int pagesFetched) {
}
