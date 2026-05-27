package com.company.demo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a Task.
 *
 * <p>Kept deliberately simple — the focus is on cloud-readiness
 * patterns rather than domain complexity.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    private String id;

    @NotBlank(message = "Title must not be blank")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    @Size(max = 1000, message = "Description must be 1000 characters or fewer")
    private String description;

    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    /** Factory — pre-populates id and createdAt. */
    public static Task create(String title, String description) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .status(TaskStatus.TODO)
                .createdAt(Instant.now())
                .build();
    }
}
