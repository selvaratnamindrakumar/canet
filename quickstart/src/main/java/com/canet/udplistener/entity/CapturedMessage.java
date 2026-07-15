package com.canet.udplistener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "captured_message",
       indexes = {
           @Index(name = "idx_cm_hash",     columnList = "hash_value", unique = true),
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
     * Monotonically increasing counter stamped in the pcap capture thread
     * before the task is submitted to the processing executor.
     * Allows consumers to detect and restore the original receive order
     * even when worker threads complete out of sequence.
     */
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /**
     * Wall-clock instant stamped in the pcap capture thread, not in the
     * worker thread.  This is the true receive time regardless of how long
     * the message waits in the executor queue.
     */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "hash_value", nullable = false, unique = true, length = 64)
    private String hashValue;

    @Column(name = "ggas_id", length = 36)
    private String ggasId;

    @Column(name = "src_ip", length = 45)
    private String srcIp;

    @Column(name = "dst_ip", length = 45)
    private String dstIp;

    @Column(name = "dst_port")
    private Integer dstPort;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
}
