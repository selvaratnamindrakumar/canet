package com.canet.udplistener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "captured_message",
       indexes = @Index(name = "idx_hash", columnList = "hash_value", unique = true))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash_value", nullable = false, unique = true, length = 64)
    private String hashValue;

    @Column(name = "dst_ip", length = 45)
    private String dstIp;

    @Column(name = "dst_port")
    private Integer dstPort;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
