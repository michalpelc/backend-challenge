# QA Engineer Backend Technical Challenge

Java 17 test suite for live GitHub pull requests and the supplied middleware JSON. Stack: Maven, REST Assured, JSON
Schema Validator, Jackson, JUnit 5, AssertJ and Allure.

## Requirements and execution

Use JDK 17 and Maven (verified with 3.9.9). Initial dependency downloads require network access. The live test requires
access to `api.github.com`.

Run commands from the directory containing `pom.xml`:

```bash
# Part 2 only: three tests using a local JSON fixture; no API calls
mvn clean test -Dtest=MiddlewareTest

# Part 1 only: one test against the public GitHub API
mvn clean test -Dtest=PullRequestsIntegrationTest

# All four tests, including the live GitHub request
mvn clean test
```

## Architecture

Java packages below are relative to `src/main/java/com/example/prmonitor/`.

| Component                                         | Responsibility                                                                                                             |
|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `api/GitHubPullRequestClient`                     | Request one GitHub page with timeouts, check HTTP 200, invoke schema validation, map JSON and detect a next-page relation. |
| `service/github/GitHubPullRequestService`         | Collect pages and select open, non-draft PRs.                                                                              |
| `validation/github/GitHubResponseSchemaValidator` | Validate a response body against the JSON Schema resource.                                                                 |
| `validation/middleware/MiddlewareValidator`       | Check the two middleware business rules.                                                                                   |
| `domain/github/`                                  | Records for a PR, a page and the scan report.                                                                              |
| `domain/middleware/MiddlewareResponse`            | Middleware payload with nested PR, author and metadata records.                                                            |

JUnit assertions live under `src/test/java/com/example/prmonitor/`. These responsibilities are separated for reuse. The
client accepts a base URI, while the service accepts a client and page size; repository coordinates are method
parameters.

## Part 1: live GitHub API

Target: `GET https://api.github.com/repos/appwrite/appwrite/pulls`.

The client requests `state=open`, `per_page=100` and sequential page numbers, starting at 1. Every response must have
HTTP status 200 before its body is validated against the schema and deserialized. A non-200 response fails even if its
JSON body matches the schema.

The service collects pages while `Link` contains `rel="next"`; it increments the page number rather than following the
header's URL.

`src/main/resources/schemas/github-pulls.schema.json` requires an array of objects with positive integer `id` and
`number`, string `title` and `state`, and boolean `draft`. Extra fields are allowed. The record maps only `id`, `state`
and `draft`.

The final count is `eligiblePullRequests().size()`, selecting case-insensitive `open` and `draft=false`.
`integration/PullRequestsIntegrationTest` checks fetched pages, open states, unique IDs and the eligible count against
total minus drafts. It prints those counts on success. Totals are not hard-coded; an empty dataset is valid.

### Timeouts and failure diagnostics

The client configures a **5-second connection timeout** and a **10-second socket read timeout**. These bound connection
establishment and waiting for response data; they are not a deadline for the complete multi-page scan.

HTTP, schema and mapping failures include the repository, page and received status, for example:

```text
GitHub request [repo=appwrite/appwrite, page=2] HTTP=500: response validation or mapping failed
```

Transport failures report `HTTP=unavailable` when no complete response reaches the client. The original exception is
retained as the cause, preserving status assertion details, schema errors or the network failure in the test output.

## Part 2: middleware JSON

Jackson loads the supplied payload from `src/test/resources/fixtures/middleware-valid.json`; no middleware HTTP endpoint
is called.

The validator checks:

- `total_open_prs` equals the number of items in `pull_requests`.
- A PR labelled exactly `high-priority` must not have `meta.is_draft=true`.

`mocked/MiddlewareTest` checks the valid payload, an incorrect total and an added high-priority draft. The last scenario
updates the total to isolate the business rule. Negative tests assert the exception and its message.

Part 2 counts every array item. Its two specified rules do not reject an ordinary draft or require checking each PR's
status.

## Reports and CI

- JUnit XML and text reports: `target/surefire-reports/` for both current test classes.
- Allure raw results: `target/allure-results/`.
- HTML: `mvn allure:report`, normally under `target/site/allure-maven-plugin/`.
- Local viewer: `mvn allure:serve`. Initial generation may download tooling. Do not run `clean` before generating
  reports from existing results.

Allure records names, outcomes and failures, without custom steps or attachments.

`.github/workflows/qa-tests.yml` runs `mvn clean test` on pushes/PRs/manual dispatches, including live with the current
configuration. Manual dispatch also runs the `verify -Plive` job. Report generation and upload use `if: always()`. A
separate Qodana workflow exists.

## Assumptions and limitations

- Requests are unauthenticated and rate-limited; `GITHUB_TOKEN` is not read.
- GitHub data can change between pages, so the scan is not a snapshot. Only the live test checks duplicate IDs after
  collection. Deterministic pagination regression tests are not included.
- No explicit response content-type assertion or application-level retry policy is configured.
- Middleware input is assumed structurally valid. Missing/null fields are not comprehensively checked; absent primitive
  values can become Java defaults.
