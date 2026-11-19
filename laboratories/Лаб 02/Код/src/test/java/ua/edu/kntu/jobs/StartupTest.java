package ua.edu.kntu.jobs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Minimal LR1 environment check: real HTTP startup, schema and resource, no mocked server. */
public class StartupTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void realServerStartsOnDynamicPortAndServesHealthAndEmptyCatalog() throws Exception {
        var db = temp.getRoot().toPath().resolve("startup.sqlite");
        try (var portal = new JobPortalServer(db).start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create(portal.baseUrl()+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,health.statusCode()); assertTrue(health.body().contains("\"variant\":26"));
            HttpResponse<String> jobs = client.send(HttpRequest.newBuilder(URI.create(portal.baseUrl()+"/api/jobs")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,jobs.statusCode()); assertTrue(jobs.body().contains("\"count\":0"));
            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(portal.baseUrl()+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,page.statusCode()); assertTrue(page.body().contains("resume-form"));
            assertTrue(Files.size(db)>0); assertFalse(portal.baseUrl().endsWith(":0"));
        }
    }
}
