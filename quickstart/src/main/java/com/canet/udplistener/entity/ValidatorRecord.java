package com.canet.udplistener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Separate table written by the generator and read by the validator endpoints.
 *
 * Keeping this table separate from captured_message means Diosma's high-frequency
 * EXISTS queries never contend with the generator's INSERT workload.
 *
 * The composite unique index on (hash_value, dst_ip, dst_port) prevents RACS
 * from treating identical content sent to different destinations as duplicates,
 * and also prevents the same content from passing twice on the same destination.
 */
@Entity
@Table(name = "validator_record",
       indexes = {
           @Index(name = "idx_vr_hash",      columnList = "hash_value"),
           @Index(name = "idx_vr_composite", columnList = "hash_value,dst_ip,dst_port", unique = true),
           @Index(name = "idx_vr_sequence",  columnList = "sequence_number"),
           @Index(name = "idx_vr_received",  columnList = "received_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Capture-time sequence number — same value stored in captured_message. */
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /** Capture-time timestamp — same value stored in captured_message. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "hash_value", nullable = false, length = 64)
    private String hashValue;

    @Column(name = "ggas_id", length = 36)
    private String ggasId;

    @Column(name = "src_ip", length = 45)
    private String srcIp;

    @Column(name = "dst_ip", length = 45)
    private String dstIp;

    @Column(name = "dst_port")
    private Integer dstPort;
}
