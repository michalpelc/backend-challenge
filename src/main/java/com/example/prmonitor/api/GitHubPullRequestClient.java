package com.example.prmonitor.api;

import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.domain.github.PullRequestPage;
import com.example.prmonitor.validation.github.GitHubResponseSchemaValidator;
import io.restassured.common.mapper.TypeRef;

import java.util.List;

import static io.restassured.RestAssured.given;

public final class GitHubPullRequestClient {

    private final String baseUri;

    public GitHubPullRequestClient(String baseUri) {
        this.baseUri = baseUri;
    }

    public PullRequestPage fetchOpenPullRequests(String owner, String repo, int page, int perPage) {
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters, must be 1-100");
        }

        // Fetch open PRs from the requested page.
        var response = given()
                .baseUri(baseUri)
                .queryParam("state", "open")
                .queryParam("per_page", perPage)
                .queryParam("page", page)
                .when()
                .get("/repos/{owner}/{repo}/pulls", owner, repo);

        // Validate HTTP response and JSON Schema before mapping.
        GitHubResponseSchemaValidator.validateGitHubResponseSchema(response);

        // Deserialize the JSON array directly into Java records.
        var pullRequests = response.as(new TypeRef<List<GitHubPullRequest>>() {
        });

        // Check whether GitHub provides another page.
        var linkHeader = response.getHeader("Link");
        var hasNextPage = linkHeader != null && linkHeader.contains("rel=\"next\"");

        return new PullRequestPage(pullRequests, hasNextPage);
    }

}
