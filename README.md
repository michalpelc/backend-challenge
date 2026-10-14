# GitHub Pull Request Monitor — Backend QA Challenge

A small, focused **Java 17 API-test suite** that checks live GitHub pull requests and validates the business rules of a supplied middleware JSON response.

The project implements the two tasks in [`qa-engineer-backend-challenge.pdf`](qa-engineer-backend-challenge.pdf):

| Challenge requirement | How it is addressed |
| --- | --- |
| Retrieve all pages of open PRs for `appwrite/appwrite` | REST Assured client + pagination service |
| Validate the GitHub response structure | JSON Schema validation for **each** successfully retrieved page |
| Exclude draft PRs and non-open PRs from the final count | Explicit business-rule filter after fetching the pages |
| Check middleware `total_open_prs` against the array size | A separate middleware validator |
| Reject a draft PR labelled `high-priority` | A targeted business-rule check and a negative test |
| Make test outcomes reviewable | JUnit/AssertJ failures, Maven Surefire results, Allure, and GitHub Actions |

**Technology:** Java 17 · Maven · REST Assured · Failsafe · JSON Schema Validator · Jackson · JUnit 5 · AssertJ · OkHttp MockWebServer · Allure.

## Quick start

### 1. Install the prerequisites

For local execution, install:

1. **JDK 17** — for example, [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17).
2. **Apache Maven** — version 3.9+ recommended ([installation guide](https://maven.apache.org/install.html)).
3. **Git** — or download the repository as a ZIP from GitHub.

No IDE is required. IntelliJ IDEA and VS Code are optional conveniences. No GitHub account or access token is needed for the tests as currently written. The first Maven run needs internet access to download its dependencies; the live GitHub test also needs access to `https://api.github.com`.

Open **Terminal** and check your installation:

```bash
java -version
mvn -version
```

Confirm that **both** Java and the Java installation reported by Maven use version **17**. If they differ, fix `JAVA_HOME` and `PATH` before running the tests.

### 2. Get the project

```bash
git clone https://github.com/michalpelc/backend-challenge.git
cd backend-challenge
```

Without Git: use **Code → Download ZIP** on the [repository page](https://github.com/michalpelc/backend-challenge), extract it, and open a terminal in the directory that contains `pom.xml`.

**All commands below must be run from the directory containing `pom.xml`.**

### 3. Run the deterministic tests first (recommended)

These tests do **not** contact the GitHub API. They use the supplied local JSON fixture and a temporary mock HTTP server that the tests start and stop themselves.

```bash
mvn clean test "-Dtest=MiddlewareTest,GitHubPullRequestHttpTest"
```

This is the recommended first command for reviewers. It checks both middleware rules, pagination, response-schema failures, and HTTP error handling without relying on an external service.

To run just the supplied middleware scenario:

```bash
mvn test "-Dtest=MiddlewareTest"
```

To run just the mocked HTTP/pagination tests:

```bash
mvn test "-Dtest=GitHubPullRequestHttpTest"
```

A passing build ends with **`BUILD SUCCESS`**. Maven also shows the number of tests executed and any failures. The first run can spend some time fetching dependencies.

### 4. Run the live GitHub test (internet required)

```bash
mvn test "-Dtest=PullRequestsSmokeIntegrationTest"
```

This test calls the **real** GitHub endpoint, so it may fail because of network restrictions, rate limiting, or changes in live data. Its success output includes a summary in this form:

```text
[LIVE GITHUB] repo=appwrite/appwrite pages=... total=... eligible=...
```

- `pages`: GitHub API pages fetched.
- `total`: PR entries returned by those pages, including drafts.
- `eligible`: entries whose state is open and whose draft flag is false.

The counts are calculated from the response and are **not hard-coded**.

### 5. Run everything

```bash
mvn clean test
```

**Important:** in the current repository, this runs **all** `*Test` classes, including the live GitHub test.

## What each test checks

| Test class / case | Scope | Expected behaviour |
| --- | --- | --- |
| `MiddlewareTest.shouldAcceptSuppliedPayload` | Fixture only | The provided middleware response is valid. |
| `MiddlewareTest.shouldFailWithClearMessageWhenTotalDoesNotMatchArrayLength` | Fixture only | Incorrect `total_open_prs` fails with a meaningful message. |
| `MiddlewareTest.shouldRejectHighPriorityDraft` | Fixture only | A high-priority draft fails, independently of the count rule. |
| `GitHubPullRequestHttpTest.shouldCountOnlyOpenNonDraftPRsAcrossPages` | Local MockWebServer | Two pages are fetched; closed and draft entries are excluded. |
| `GitHubPullRequestHttpTest.shouldRejectHttpErrors` | Local MockWebServer | HTTP 403 and HTTP 500 each fail after the configured retry attempts. |
| `GitHubPullRequestHttpTest.shouldRejectInvalidSchemaOnSecondPage` | Local MockWebServer | An invalid `draft` data type on page two fails schema validation. |
| `PullRequestsSmokeIntegrationTest.shouldFetchAndFilterLivePullRequests` | Real GitHub API | Fetched PRs are open, IDs are unique, eligible PRs are non-draft, and a result summary is printed. |

The parameterized HTTP error case runs twice (once for 403, once for 500), so the current code represents **eight individual test invocations**: three middleware, four mocked HTTP, and one live test.

**Terminology:** "mocked" means the test provides controlled data; it does not mean the HTTP client is replaced with a stub. `GitHubPullRequestHttpTest` uses a real local HTTP connection to OkHttp `MockWebServer`, exercising the client, schema validation, parsing, pagination, and filtering together.

## How the implementation works

### Part 1 — live GitHub API

The target is:

```http
GET https://api.github.com/repos/appwrite/appwrite/pulls?state=open&per_page=100&page=1
```

The integration test creates `GitHubPullRequestService` with `GitHubPullRequestClient` and a page size of `100`. The service loops through pages until the response no longer advertises a `next` link.

```text
PullRequestsSmokeIntegrationTest
            |
            v
GitHubPullRequestService.scan(owner, repo)
            |
            v
GitHubPullRequestClient.fetchOpenPullRequests(page)
            |
            +--> GET /repos/{owner}/{repo}/pulls
            +--> retry a non-200 response (up to 3 HTTP attempts per page)
            +--> require HTTP 200
            +--> validate JSON Schema
            +--> deserialize into GitHubPullRequest records
            +--> inspect the Link header for rel="next"
            |
            v
Collect all pages and filter: state == "open" && draft == false
            |
            v
PullRequestReport(allPullRequests, eligiblePullRequests, pagesFetched)
            |
            v
JUnit / AssertJ assertions
```

**Pagination:** GitHub's response can contain a `Link` header such as:

```http
Link: <https://api.github.com/repos/appwrite/appwrite/pulls?state=open&per_page=100&page=2>; rel="next"
```

When `rel="next"` is present, the service requests the next **page number**. It does not follow the URL from the header literally; this is a deliberate simplification for GitHub's conventional numbered pagination. Checking only the first page or stopping whenever a page contains fewer than 100 records could miss data, so the code relies on the pagination link instead.

**Validation order matters:** the client requires HTTP **200** before checking the body schema and mapping records. A server error containing valid-looking JSON (for example, HTTP 500 with body `[]`) must **not** be counted as zero PRs.

The schema file, [`src/main/resources/schemas/github-pulls.schema.json`](src/main/resources/schemas/github-pulls.schema.json), requires an array of PR objects with positive integer `id` and `number`, string `title` and `state`, and boolean `draft`. It permits additional GitHub fields. The Java record uses only what is relevant to this task, although it also retains `number` and `title`.

The final count comes from `eligiblePullRequests().size()`, using a case-insensitive open-state comparison and excluding drafts. GitHub may change while pages are being collected, so this is **not an atomic snapshot**.

### Current retry behaviour

The client uses **Failsafe** with `withMaxAttempts(3)`: **one initial attempt plus up to two retries for each page**. The configured backoff grows from 250 ms toward 1 second with 20% jitter, and retries print a `[GITHUB RETRY]` diagnostic to standard error.

### Part 2 — supplied middleware JSON

The project reads [`src/test/resources/fixtures/middleware-valid.json`](src/test/resources/fixtures/middleware-valid.json) with Jackson. **It does not call a real middleware endpoint**; the exercise explicitly supplies a response to validate.

Two rules are enforced by `MiddlewareValidator`:

1. **Integrity:** `total_open_prs == pull_requests.size()`.
2. **Business rule:** if a PR's `labels` contain the exact string `high-priority`, then `meta.is_draft` must be `false`.

The tests cover a valid payload and a failure for each rule. When the high-priority draft test adds an invalid PR, it also updates `total_open_prs` so the integrity check still passes and the test isolates the intended rule.

## Architecture and project layout

```text
backend-challenge/
├── .github/workflows/
│   ├── qa-tests.yml                  
│   └── qodana_code_quality.yml        
├── src/
│   ├── main/
│   │   ├── java/com/example/prmonitor/
│   │   │   ├── api/GitHubPullRequestClient.java
│   │   │   ├── service/github/GitHubPullRequestService.java
│   │   │   ├── domain/github/       
│   │   │   ├── domain/middleware/MiddlewareResponse.java
│   │   │   ├── validation/github/GitHubResponseSchemaValidator.java
│   │   │   └── validation/middleware/MiddlewareValidator.java
│   │   └── resources/schemas/github-pulls.schema.json
│   └── test/
│       ├── java/com/example/prmonitor/
│       │   ├── integration/PullRequestsSmokeIntegrationTest.java
│       │   └── mocked/
│       │       ├── GitHubPullRequestHttpTest.java
│       │       └── MiddlewareTest.java
│       └── resources/fixtures/middleware-valid.json
├── pom.xml
├── qodana.yaml
```

**Who owns what:**

| Component | Responsibility |
| --- | --- |
| `GitHubPullRequestClient` | Make one GitHub HTTP request, retry responses, check the HTTP status, validate the schema, parse the result, and inspect pagination headers. |
| `GitHubPullRequestService` | Traverse pages, aggregate results, and apply the eligible-PR business filter. |
| `GitHubResponseSchemaValidator` | Check the minimum required shape and types of GitHub response data. |
| `MiddlewareValidator` | Enforce the two independent rules of the supplied middleware payload. |
| `domain/github` and `domain/middleware` | Small immutable Java records representing the data passed between layers. |
| Tests | Set up controlled cases, invoke the production code, and make assertions about observable behaviour. |

The client accepts a base URI, so it can target either GitHub or the local mock server. The service accepts that client and a page size. Keeping those choices explicit makes the code reusable without adding a dependency-injection framework.

## Reports and CI

### Local reports

After a test run, standard JUnit/Surefire results are located at:

```text
target/surefire-reports/
```

Allure raw test results are generated under:

```text
target/allure-results/
```

Generate an HTML Allure report after running tests:

```bash
mvn allure:report
```

The HTML report is normally created under `target/site/allure-maven-plugin/`. You can also run:

```bash
mvn allure:serve
```

### GitHub Actions

[`.github/workflows/qa-tests.yml`](.github/workflows/qa-tests.yml) runs on pushes, pull requests, and manual dispatch. It installs Temurin Java 17, caches Maven dependencies, executes `mvn -B clean test`, generates an Allure report when result files exist, and uploads Surefire and Allure artifacts. A separate Qodana workflow performs static-analysis checks.

## Design decisions and trade-offs

- **Small, explicit layers rather than a large test framework.** Separate HTTP, pagination/business logic, data models, and test assertions, but keep the number of abstractions low.
- **Real HTTP in deterministic tests.** MockWebServer gives controlled responses and lets the tests verify the actual HTTP request path, headers, status handling, schema checks, and pagination without the instability of the public API.
- **JSON Schema at the API boundary.** Catch incompatible data before mapping. Validate the fields this challenge actually needs instead of copying GitHub's entire response contract.
- **Treat `Link` as the pagination signal.** This is more reliable for the endpoint than assuming a particular page count, but the implementation currently only detects `rel="next"` and increments the page number.
- **Retry at the request level.** The existing policy retries the **same page** rather than restarting the whole scan. That keeps retries local, although the policy needs more precise error classification.
- **Avoid asserting a fixed live count.** GitHub is mutable; static counts would turn ordinary changes to PRs into false failures.
- **No direct coupling between tasks 1 and 2.** The middleware validation operates on the provided response, not on results previously fetched from GitHub, because the brief defines them as separate scenarios.

## Known limitations

1. **Pagination has no safety guard.** Concurrent changes to GitHub PRs can affect completeness across pages.
2. **Unauthenticated API access only.** `GITHUB_TOKEN` is not read. GitHub's unauthenticated rate limits apply.
3. **Middleware input** is assumed structurally valid.