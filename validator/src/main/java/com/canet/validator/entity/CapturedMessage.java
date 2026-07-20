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
           @Index(name = "idx_cm_hash", columnList = "file_hash")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturedMessage {

    /** Auto-generated primary key — MySQL assigns this on insert. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MD5 hex of the raw UDP payload (32 chars).
     *  Not unique — different payloads can legitimately produce the same hash. */
    @Column(name = "file_hash", nullable = false, length = 32)
    private String fileHash;

    /** UUID assigned by the generator per captured packet. */
    @Column(name = "uuid", length = 36)
    private String uuid;

    /** Packet arrival time supplied by the generator — when the packet was
     *  captured at the network interface, not when this row was written. */
    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;
}
