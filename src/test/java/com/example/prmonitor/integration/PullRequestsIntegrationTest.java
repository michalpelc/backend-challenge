package com.example.prmonitor.integration;

import com.example.prmonitor.api.GitHubPullRequestClient;
import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.service.github.GitHubPullRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
class PullRequestsIntegrationTest {

    @Test
    @DisplayName("Fetch all GitHub PR pages, validate schema and count only eligible PRs")
    void shouldFetchAllPagesValidateSchemaAndCountOnlyNonDraftOpenPullRequests() {
        var owner = "appwrite";
        var repo = "appwrite";
        var client = new GitHubPullRequestClient("https://api.github.com");

        // Fetch all pages and validate the JSON schema of each response.
        var report = new GitHubPullRequestService(client, 100).scan(owner, repo);

        // Verify pagination, open states and unique PR IDs.
        assertThat(report.pagesFetched()).isPositive();
        assertThat(report.allPullRequests()).allSatisfy(pr -> assertThat(pr.state()).isEqualToIgnoringCase("open"));
        assertThat(report.allPullRequests()).extracting(GitHubPullRequest::id).doesNotHaveDuplicates();

        // Verify that draft PRs are excluded from the eligible count.
        var drafts = report.allPullRequests().stream().filter(GitHubPullRequest::draft).count();

        assertThat(report.eligiblePullRequests()).allSatisfy(pr -> {
            assertThat(pr.state()).isEqualToIgnoringCase("open");
            assertThat(pr.draft()).isFalse();
        });

        assertThat(report.eligiblePullRequests().size()).isEqualTo(report.allPullRequests().size() - drafts);

        // Print test results for CI visibility.
        System.out.printf(
                "[LIVE GITHUB] repo=%s/%s pages=%d total=%d drafts=%d eligible=%d%n",
                owner, repo, report.pagesFetched(),
                report.allPullRequests().size(),
                drafts, report.eligiblePullRequests().size()
        );

    }

}