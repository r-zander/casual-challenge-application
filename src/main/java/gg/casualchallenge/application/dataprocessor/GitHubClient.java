package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFileVO;
import gg.casualchallenge.application.model.values.PullRequestVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/*
 * The three migrations of a season start, as one commit on a branch of its own plus a pull request. The Git Data API rather
 * than the contents API, because that one writes a commit per file and the 02 file is four megabytes on its own:
 *
 *     GET  /repos/{repository}/git/ref/heads/{baseBranch}  --> the commit the branch starts from
 *     GET  /repos/{repository}/git/commits/{sha}           --> its tree
 *     POST /repos/{repository}/git/blobs                   --> one per file
 *     POST /repos/{repository}/git/trees                   --> the three files on top of that tree
 *     POST /repos/{repository}/git/commits
 *     POST /repos/{repository}/git/refs
 *     POST /repos/{repository}/pulls
 */
@Service
@Slf4j
public class GitHubClient {

    private static final DateTimeFormatter PREPARED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int CONNECT_TIMEOUT_IN_SECONDS = 30;
    private static final int TIMEOUT_IN_MINUTES = 5;

    private static final String MIGRATION_DIRECTORY = "src/main/resources/db/changelog/migrations";
    private static final String FILE_MODE = "100644"; // a plain file, nothing executable

    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final String repository;
    private final String baseBranch;

    public GitHubClient(
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.github.api-base-url}") String apiBaseUrl,
            @Value("${casual-challenge.season.github.repository}") String repository,
            @Value("${casual-challenge.season.github.base-branch}") String baseBranch
    ) {
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl;
        this.repository = repository;
        this.baseBranch = baseBranch;
    }

    public static String branchName(int seasonNumber) {
        return "feature/season-" + seasonNumber + "-migrations";
    }

    public static String commitMessage(int seasonNumber) {
        return "Added migrations for newest season (" + seasonNumber + ")";
    }

    public static String migrationPath(String fileName) {
        return MIGRATION_DIRECTORY + "/" + fileName;
    }

    public static String pullRequestBody(SeasonDraftVO draft, List<SeasonSqlFileVO> files) {
        StringBuilder body = new StringBuilder();
        body.append("Season ").append(draft.getSeasonNumber()).append(" migrations, straight out of the season wizard.\n\n");
        body.append("- prepared ").append(draft.getPreparedAt().format(PREPARED_AT_FORMAT)).append(" by ").append(draft.getPreparedBy());
        body.append(", committed by ").append(draft.getCommittedBy()).append("\n");
        for (SeasonSqlFileVO file : files) {
            body.append("- ").append(file.getFileName()).append("\n");
        }
        body.append("- the season is live already, these files are only so the database can be rebuilt from the repository alone\n");
        body.append("- merge, then run the deploy workflow\n");

        return body.toString();
    }

    public PullRequestVO openPullRequest(String token, SeasonDraftVO draft, List<SeasonSqlFileVO> files) {
        int seasonNumber = draft.getSeasonNumber();
        String branch = branchName(seasonNumber);

        String baseSha = get("/git/ref/heads/" + baseBranch, token).path("object").path("sha").asText();
        String baseTreeSha = get("/git/commits/" + baseSha, token).path("tree").path("sha").asText();

        ObjectNode tree = objectMapper.createObjectNode();
        tree.put("base_tree", baseTreeSha);
        ArrayNode entries = tree.putArray("tree");
        for (SeasonSqlFileVO file : files) {
            ObjectNode blob = objectMapper.createObjectNode();
            blob.put("content", Base64.getEncoder().encodeToString(file.getContent().getBytes(StandardCharsets.UTF_8)));
            blob.put("encoding", "base64");

            ObjectNode entry = entries.addObject();
            entry.put("path", migrationPath(file.getFileName()));
            entry.put("mode", FILE_MODE);
            entry.put("type", "blob");
            entry.put("sha", post("/git/blobs", token, blob).path("sha").asText());
        }

        ObjectNode commit = objectMapper.createObjectNode();
        commit.put("message", commitMessage(seasonNumber));
        commit.put("tree", post("/git/trees", token, tree).path("sha").asText());
        commit.putArray("parents").add(baseSha);
        String commitSha = post("/git/commits", token, commit).path("sha").asText();

        ObjectNode reference = objectMapper.createObjectNode();
        reference.put("ref", "refs/heads/" + branch);
        reference.put("sha", commitSha);
        post("/git/refs", token, reference);

        ObjectNode pullRequest = objectMapper.createObjectNode();
        pullRequest.put("title", commitMessage(seasonNumber));
        pullRequest.put("head", branch);
        pullRequest.put("base", baseBranch);
        pullRequest.put("body", pullRequestBody(draft, files));

        JsonNode opened;
        try {
            opened = post("/pulls", token, pullRequest);
        } catch (RuntimeException e) {
            // The branch without its pull request would only be in the way of the next attempt
            try {
                deleteBranch(token, branch);
            } catch (RuntimeException cleanUpFailure) {
                log.warn("Couldn't delete branch {} after the pull request failed.", branch, cleanUpFailure);
            }
            throw e;
        }

        log.info("Opened pull request {} with the migrations for season {}.", opened.path("html_url").asText(), seasonNumber);

        return new PullRequestVO(opened.path("number").asInt(), opened.path("html_url").asText(), branch, commitSha);
    }

    /** GitHub closes the pull request along with the branch. */
    public void deleteBranch(String token, String branch) {
        String url = repositoryUrl("/git/refs/heads/" + branch);
        send(HttpRequest.newBuilder(URI.create(url)).DELETE(), token, url);
        log.info("Deleted branch {}.", branch);
    }

    private JsonNode get(String path, String token) {
        String url = repositoryUrl(path);

        return send(HttpRequest.newBuilder(URI.create(url)).GET(), token, url);
    }

    private JsonNode post(String path, String token, ObjectNode body) {
        String url = repositoryUrl(path);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't write the request for '" + url + "'.", e);
        }

        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)), token, url);
    }

    private JsonNode send(HttpRequest.Builder builder, String token, String url) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_IN_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = builder
                .timeout(Duration.ofMinutes(TIMEOUT_IN_MINUTES))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitHub answered " + response.statusCode() + " on '" + url + "': " + errorMessage(response.body()));
            }
            if (response.body() == null || response.body().isEmpty()) return objectMapper.createObjectNode();

            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            // IllegalStateException and not a plain RuntimeException: GitHub being unhappy is a 409 with a readable message, not a 500
            throw new IllegalStateException("Couldn't reach GitHub at '" + url + "'. " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("The call to GitHub at '" + url + "' got interrupted.", e);
        }
    }

    private String errorMessage(String body) {
        if (body == null || body.isEmpty()) return "no message";

        try {
            JsonNode error = objectMapper.readTree(body);
            String message = error.path("message").asText();
            if (message.isEmpty()) return body;

            JsonNode errors = error.path("errors");
            if (errors.isArray() && errors.size() > 0) {
                return message + " (" + errors.get(0).toString() + ")";
            }

            return message;
        } catch (IOException e) {
            return body; // not json, so the body is all we have
        }
    }

    private String repositoryUrl(String path) {
        return apiBaseUrl + "/repos/" + repository + path;
    }
}
