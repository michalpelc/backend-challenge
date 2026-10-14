package com.example.prmonitor.mocked;

import com.example.prmonitor.api.GitHubPullRequestClient;
import com.example.prmonitor.domain.github.GitHubPullRequest;
import com.example.prmonitor.service.github.GitHubPullRequestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.apache.http.HttpStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubPullRequestHttpTest {

    private MockWebServer server;
    private GitHubPullRequestService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var client = new GitHubPullRequestClient(server.url("/").toString());
        service = new GitHubPullRequestService(client, 2);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCountOnlyOpenNonDraftPRsAcrossPages() throws JsonProcessingException {
        var mapper = new ObjectMapper();

        GitHubPullRequest[] page1 = {
                new GitHubPullRequest(1, 1, "PR1", "open", false),
                new GitHubPullRequest(2, 2, "PR2", "open", true)
        };

        GitHubPullRequest[] page2 = {
                new GitHubPullRequest(3, 3, "PR3", "open", false),
                new GitHubPullRequest(4, 4, "PR4", "closed", false)
        };

        server.enqueue(new MockResponse()
                .setResponseCode(SC_OK)
                .setHeader("Content-Type", "application/json")
                .setHeader("Link", "<" + server.url("/repos/appwrite/appwrite/pulls?page=2") + ">; rel=\"next\"")
                .setBody(mapper.writeValueAsString(page1)));

        server.enqueue(new MockResponse()
                .setResponseCode(SC_OK)
                .setHeader("Content-Type", "application/json")
                .setBody(mapper.writeValueAsString(page2)));

        var result = service.scan("appwrite", "appwrite");

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.allPullRequests()).hasSize(4);
        assertThat(result.eligiblePullRequests())
                .hasSize(2)
                .extracting(GitHubPullRequest::id).containsExactlyInAnyOrder(1L, 3L);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }


    @ParameterizedTest
    @ValueSource(ints = {SC_FORBIDDEN, SC_INTERNAL_SERVER_ERROR})
    void shouldRejectHttpErrors(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("[]"));
        assertThatThrownBy(() -> service.scan("appwrite", "appwrite")).isInstanceOf(AssertionError.class).hasMessageContaining("Expected status code <200> but was <" + status + ">");
    }


    @Test
    void shouldRejectInvalidSchemaOnSecondPage() throws JsonProcessingException {
        var mapper = new ObjectMapper();

        GitHubPullRequest[] page1 = {
                new GitHubPullRequest(1, 1, "PR1", "open", false),
                new GitHubPullRequest(2, 2, "PR2", "open", true)
        };

        // Page with invalid schema
        var page2 = mapper.createArrayNode().add(
                mapper.createObjectNode()
                        .put("id", 3)
                        .put("number", 3)
                        .put("title", "PR3")
                        .put("state", "open")
                        .put("draft", "false")
        );

        server.enqueue(new MockResponse()
                .setResponseCode(SC_OK)
                .setHeader("Content-Type", "application/json")
                .setHeader("Link", "<" + server.url("/repos/appwrite/appwrite/pulls?page=2") + ">; rel=\"next\"")
                .setBody(mapper.writeValueAsString(page1)));

        server.enqueue(new MockResponse()
                .setResponseCode(SC_OK)
                .setHeader("Content-Type", "application/json")
                .setBody(mapper.writeValueAsString(page2)));

        assertThatThrownBy(() -> service.scan("appwrite", "appwrite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("instance type (string)")
                .hasMessageContaining("boolean");

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

}
