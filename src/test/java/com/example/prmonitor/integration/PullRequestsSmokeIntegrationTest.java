
package com.example.prmonitor.integration;

import com.example.prmonitor.api.GitHubPullRequestClient;
import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.service.github.GitHubPullRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PullRequestsSmokeIntegrationTest {

    @Test
    @DisplayName("Verify live GitHub PR pagination and business rules")
    void shouldFetchAndFilterLivePullRequests() {
        var owner = "appwrite";
        var repo = "appwrite";

        var service = new GitHubPullRequestService(new GitHubPullRequestClient("https://api.github.com"), 100);
        var result = service.scan(owner, repo);

        assertThat(result.pagesFetched()).isPositive();
        assertThat(result.allPullRequests()).allSatisfy(pr -> assertThat(pr.state()).isEqualToIgnoringCase("open"));
        assertThat(result.allPullRequests()).extracting(GitHubPullRequest::id).doesNotHaveDuplicates();
        assertThat(result.eligiblePullRequests()).allMatch(pr -> "open".equalsIgnoreCase(pr.state()) && !pr.draft());

        System.out.printf(
                "[LIVE GITHUB] repo=%s/%s pages=%d total=%d eligible=%d%n",
                owner, repo, result.pagesFetched(),
                result.allPullRequests().size(),
                result.eligiblePullRequests().size()
        );

    }

}
