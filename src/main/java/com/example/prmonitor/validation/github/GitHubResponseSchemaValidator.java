package com.example.prmonitor.validation.github;

import io.restassured.response.Response;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

public final class GitHubResponseSchemaValidator {

// Validates the GitHub API response against github-pulls.schema.json using a minimal contract
// focused on relevant fields, nested objects, arrays, and data types rather than the entire API response.

    public static void validateGitHubResponseSchema(Response response) {
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-pulls.schema.json"));
    }

}
