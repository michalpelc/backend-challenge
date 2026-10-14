
package com.example.prmonitor.api;

import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.domain.github.PullRequestPage;
import dev.failsafe.RetryPolicy;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;

import java.util.List;

import static com.example.prmonitor.validation.github.GitHubResponseSchemaValidator.validateGitHubResponseSchema;
import static dev.failsafe.Failsafe.with;
import static io.restassured.RestAssured.given;
import static java.lang.System.err;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.apache.http.HttpStatus.SC_OK;

public final class GitHubPullRequestClient {

    private final String baseUri;

    public GitHubPullRequestClient(String baseUri) {
        this.baseUri = baseUri;
    }

    public PullRequestPage fetchOpenPullRequests(
            String owner, String repo, int page, int perPage) {

        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException(
                    "page must be >= 1; perPage must be between 1 and 100 due to GH API limitations");
        }

        var retryPolicy = RetryPolicy.<Response>builder().handleIf((response, exception) -> response.statusCode() != SC_OK)
                .withMaxAttempts(3)
                .withBackoff(ofMillis(250), ofSeconds(1))
                .withJitter(0.2)
                .onRetry(event -> err.printf("[GITHUB RETRY] repo=%s/%s page=%d " + "attempt=%d HTTP=%d%n", owner, repo, page, event.getAttemptCount(), event.getLastResult().statusCode()))
                .build();

        // Each retry repeats exactly the same owner/repo/page/perPage.
        Response response = with(retryPolicy).get(() ->
                given()
                        .baseUri(baseUri)
                        .queryParam("state", "open")
                        .queryParam("per_page", perPage)
                        .queryParam("page", page)
                        .when()
                        .get("/repos/{owner}/{repo}/pulls", owner, repo)
        );

        if (response.statusCode() != SC_OK) {
            throw new IllegalStateException("GitHub request failed after retries: repo=%s/%s, page=%d, HTTP=%d".formatted(owner, repo, page, response.statusCode()));
        }

        validateGitHubResponseSchema(response);

        var pullRequests = response.as(new TypeRef<List<GitHubPullRequest>>() {
        });
        var linkHeader = response.getHeader("Link");
        var hasNextPage = linkHeader != null && linkHeader.contains("rel=\"next\"");

        return new PullRequestPage(pullRequests, hasNextPage);
    }
}
