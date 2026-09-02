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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturedMessage {

    // IDENTITY works on SQL Server 2008+.
    // If the column is backed by dbo.OUTBOUND_FILE_SEQ (SQL Server 2012+),
    // the INSERT still succeeds — SQL Server resolves the IDENTITY value from
    // the sequence internally; Hibernate does not call NEXT VALUE FOR directly.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbound_file_id")
    private Long id;

    @Column(name = "outbound_file_hash", nullable = false, length = 32)
    private String fileHash;

    @Column(name = "outbound_file_name", length = 255)
    private String fileName;

    @Column(name = "outbound_file_time", nullable = false)
    private Instant arrivalTime;
}
