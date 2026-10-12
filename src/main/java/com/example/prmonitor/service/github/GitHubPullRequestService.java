package com.example.prmonitor.service.github;

import com.example.prmonitor.api.GitHubPullRequestClient;
import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.domain.github.PullRequestPage;
import com.example.prmonitor.domain.github.PullRequestReport;

import java.util.ArrayList;
import java.util.List;

public final class GitHubPullRequestService {

    private final GitHubPullRequestClient client;
    private final int perPage;

    public GitHubPullRequestService(GitHubPullRequestClient client, int perPage) {
        this.client = client;
        this.perPage = perPage;
    }

    public PullRequestReport scan(String owner, String repo) {
        List<GitHubPullRequest> all = new ArrayList<>();
        int page = 1;
        boolean hasNext;

        // Fetch and collect all available pages.
        do {
            PullRequestPage result = client.fetchOpenPullRequests(owner, repo, page, perPage);
            all.addAll(result.items());
            hasNext = result.hasNext();
            page++;
        } while (hasNext);

        // Include only open PRs that are not drafts.
        List<GitHubPullRequest> eligible = all.stream().filter(pr -> "open".equalsIgnoreCase(pr.state()) && !pr.draft()).toList();

        return new PullRequestReport(all, eligible, page - 1);
    }

}
