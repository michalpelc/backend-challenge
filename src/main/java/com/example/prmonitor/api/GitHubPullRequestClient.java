package com.example.prmonitor.api;

import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.domain.github.PullRequestPage;
import com.example.prmonitor.validation.github.GitHubResponseSchemaValidator;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.config.HttpClientConfig.httpClientConfig;
import static io.restassured.config.RestAssuredConfig.config;

public final class GitHubPullRequestClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final String baseUri;

    public GitHubPullRequestClient(String baseUri) {
        this.baseUri = baseUri;
    }

    public PullRequestPage fetchOpenPullRequests(String owner, String repo, int page, int perPage) {

        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException(
                    "page must be >= 1; perPage must be between 1 and 100");
        }

        var context = "GitHub request [repo=%s/%s, page=%d]".formatted(owner, repo, page);

        Response response;

        try {
            response = given()
                    // Bound connection setup and waiting for response data.
                    .config(config().httpClient(httpClientConfig()
                            .setParam("http.connection.timeout", CONNECT_TIMEOUT_MS)
                            .setParam("http.socket.timeout", READ_TIMEOUT_MS)))
                    .baseUri(baseUri)
                    .queryParam("state", "open")
                    .queryParam("per_page", perPage)
                    .queryParam("page", page)
                    .when()
                    .get("/repos/{owner}/{repo}/pulls", owner, repo);

        } catch (Exception exception) {
            // A transport failure may occur before any HTTP response is available.
            throw new IllegalStateException("%s HTTP=unavailable: %s: %s".formatted(context, exception.getClass().getSimpleName(), exception.getMessage()), exception);
        }

        try {
            // Reject HTTP errors even when the response body matches the schema.
            response.then().statusCode(200);
            GitHubResponseSchemaValidator.validateGitHubResponseSchema(response);
            var pullRequests = response.as(new TypeRef<List<GitHubPullRequest>>() {});
            var linkHeader = response.getHeader("Link");
            var hasNextPage = linkHeader != null && linkHeader.contains("rel=\"next\"");

            return new PullRequestPage(pullRequests, hasNextPage);

        } catch (AssertionError | RuntimeException exception) {
            // Add request context while preserving the detailed original failure.
            throw new IllegalStateException("%s HTTP=%d: response validation or mapping failed".formatted(context, response.statusCode()), exception);
        }
    }

}