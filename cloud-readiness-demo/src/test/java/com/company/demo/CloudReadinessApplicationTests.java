package com.company.demo;

import com.company.demo.model.Task;
import com.company.demo.model.TaskStatus;
import com.company.demo.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Cloud Readiness Demo.
 *
 * <p>Tests verify that the cloud-readiness patterns work end-to-end:
 * REST API, health probes, and the info endpoint.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CloudReadinessApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    // ── Context load ──────────────────────────────────────────

    @Test
    void contextLoads() {
        assertNotNull(taskService, "TaskService should be wired");
    }

    // ── Task CRUD ─────────────────────────────────────────────

    @Test
    void listTasks_returnsSeedData() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void createTask_returnsCreated() throws Exception {
        String body = """
                {
                  "title": "Write unit tests",
                  "description": "Cover service and controller layers"
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(emptyString())))
                .andExpect(jsonPath("$.title", is("Write unit tests")))
                .andExpect(jsonPath("$.status", is("TODO")));
    }

    @Test
    void createTask_blankTitle_returnsBadRequest() throws Exception {
        String body = """
                { "title": "" }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTask_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/non-existent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_changesStatus() throws Exception {
        // Create a task first
        Task task = taskService.create(Task.create("To Update", "will change status"));
        String updateBody = """
                {
                  "title": "To Update",
                  "description": "status changed",
                  "status": "DONE"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")));
    }

    // ── Health probes (cloud-readiness) ───────────────────────

    @Test
    void healthEndpoint_isUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void livenessProbe_isUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void readinessProbe_isUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    // ── Info endpoint (cloud-readiness) ───────────────────────

    @Test
    void infoEndpoint_containsCloudFeatures() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cloudReadinessFeatures.healthProbes", is(true)))
                .andExpect(jsonPath("$.cloudReadinessFeatures.dockerLayeredJar", is(true)))
                .andExpect(jsonPath("$.cloudReadinessFeatures.gracefulShutdown", is(true)));
    }
}
