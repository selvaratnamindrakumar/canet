package com.canet.validator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "captured_message",
       indexes = {
           @Index(name = "idx_cm_hash",     columnList = "hash_value"),
           @Index(name = "idx_cm_sequence", columnList = "sequence_number"),
           @Index(name = "idx_cm_received", columnList = "received_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Monotonic capture-time sequence number supplied by the generator.
     * Consumers can sort on this column to restore original receive order
     * even when worker threads completed out of sequence.
     */
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /**
     * Wall-clock instant stamped by the generator at packet capture time,
     * not at validation or persistence time.
     */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** MD5 hex of the raw UDP payload (32 chars). */
    @Column(name = "hash_value", nullable = false, length = 32)
    private String hashValue;

    /** GGAS identifier — UUID assigned by the generator per packet. */
    @Column(name = "ggas_id", length = 36)
    private String ggasId;

    /** Name column — present in original schema; UUID value used in practice. */
    @Column(name = "name", length = 36)
    private String name;

    @Column(name = "dst_ip", length = 45)
    private String dstIp;

    @Column(name = "dst_port")
    private Integer dstPort;
}
