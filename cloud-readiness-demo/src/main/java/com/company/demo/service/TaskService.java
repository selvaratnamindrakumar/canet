package com.company.demo.service;

import com.company.demo.exception.TaskNotFoundException;
import com.company.demo.model.Task;
import com.company.demo.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Task service.
 *
 * <p><strong>Cloud Note:</strong> In a production deployment this store
 * would be replaced with an Amazon DynamoDB or RDS repository so the
 * application remains stateless and can scale horizontally without data loss.
 * The service interface stays identical — only the repository bean changes.</p>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // ConcurrentHashMap → thread-safe in-process store (swap for DynamoDB in prod)
    private final Map<String, Task> store = new ConcurrentHashMap<>();

    public TaskService() {
        // Seed with demo data so the API is immediately usable
        Task t1 = Task.create("Containerise application", "Package the Spring Boot app using Docker multi-stage build");
        Task t2 = Task.create("Push image to ECR", "Tag and push Docker image to Amazon Elastic Container Registry");
        Task t3 = Task.create("Deploy to ECS Fargate", "Create ECS task definition and run on Fargate cluster");
        t3.setStatus(TaskStatus.IN_PROGRESS);
        store.put(t1.getId(), t1);
        store.put(t2.getId(), t2);
        store.put(t3.getId(), t3);
        log.info("TaskService initialised with {} seed tasks", store.size());
    }

    public List<Task> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Task> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Task create(Task task) {
        Task created = Task.create(task.getTitle(), task.getDescription());
        store.put(created.getId(), created);
        log.info("Task created: id={} title={}", created.getId(), created.getTitle());
        return created;
    }

    public Task update(String id, Task incoming) {
        Task existing = store.get(id);
        if (existing == null) {
            throw new TaskNotFoundException(id);
        }
        existing.setTitle(incoming.getTitle());
        existing.setDescription(incoming.getDescription());
        existing.setStatus(incoming.getStatus());
        existing.setUpdatedAt(Instant.now());
        store.put(id, existing);
        log.info("Task updated: id={} status={}", id, existing.getStatus());
        return existing;
    }

    public void delete(String id) {
        if (!store.containsKey(id)) {
            throw new TaskNotFoundException(id);
        }
        store.remove(id);
        log.info("Task deleted: id={}", id);
    }

    public int count() {
        return store.size();
    }
}
