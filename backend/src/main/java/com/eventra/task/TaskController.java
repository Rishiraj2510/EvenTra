package com.eventra.task;

import com.eventra.task.dto.CreateTaskRequest;
import com.eventra.task.dto.TaskResponse;
import com.eventra.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<List<TaskResponse>> listTasks(@RequestParam UUID eventId) {
        return ResponseEntity.ok(taskService.listByEventId(eventId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest req) {
        TaskResponse created = taskService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest req) {
        return ResponseEntity.ok(taskService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
