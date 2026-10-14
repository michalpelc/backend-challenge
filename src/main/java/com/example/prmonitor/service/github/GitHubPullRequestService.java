package com.example.prmonitor.service.github;

import com.example.prmonitor.api.GitHubPullRequestClient;
import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.domain.github.PullRequestReport;

import java.util.ArrayList;

public final class GitHubPullRequestService {

    private final GitHubPullRequestClient client;
    private final int perPage;

    public GitHubPullRequestService(GitHubPullRequestClient client, int perPage) {
        this.client = client;
        this.perPage = perPage;
    }

    public PullRequestReport scan(String owner, String repo) {
        var all = new ArrayList<GitHubPullRequest>();
        var page = 1;

        while (true) {
            var result = client.fetchOpenPullRequests(owner, repo, page, perPage);
            all.addAll(result.items());
            if (!result.hasNext()) {
                break;
            }
            page++;
        }

        var eligible = all.stream().filter(pr -> "open".equalsIgnoreCase(pr.state()) && !pr.draft()).toList();

        return new PullRequestReport(all, eligible, page);
        // At this point the results are collected into single list and complete dataset is returned
    }

}
