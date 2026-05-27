package com.company.demo.controller;

import com.company.demo.exception.TaskNotFoundException;
import com.company.demo.model.Task;
import com.company.demo.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD operations on Tasks.
 *
 * <p>Base path: {@code /api/v1/tasks}</p>
 *
 * <table border="1">
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks</td><td>List all tasks</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks/{id}</td><td>Get task by id</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks</td><td>Create task</td></tr>
 *   <tr><td>PUT</td><td>/api/v1/tasks/{id}</td><td>Update task</td></tr>
 *   <tr><td>DELETE</td><td>/api/v1/tasks/{id}</td><td>Delete task</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<Task>> listAll() {
        return ResponseEntity.ok(taskService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getById(@PathVariable String id) {
        return taskService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody Task task) {
        Task created = taskService.create(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable String id, @Valid @RequestBody Task task) {
        Task updated = taskService.update(id, task);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
