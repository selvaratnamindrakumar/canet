package com.canet.validator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "OUTBOUND_FILE", schema = "dbo",
       indexes = {
           @Index(name = "idx_of_hash", columnList = "outbound_file_hash")
       })
@SequenceGenerator(
        name       = "outbound_file_seq",
        sequenceName = "dbo.OUTBOUND_FILE_SEQ",
        allocationSize = 1
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbound_file_seq")
    @Column(name = "outbound_file_id")
    private Long id;

    @Column(name = "outbound_file_hash", nullable = false, length = 32)
    private String fileHash;

    @Column(name = "outbound_file_name", length = 255)
    private String fileName;

    @Column(name = "outbound_file_time", nullable = false)
    private Instant arrivalTime;
}
