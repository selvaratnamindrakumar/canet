package com.canet.keypoc.repository.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CellEntityId implements Serializable {
    private String cgi;
    private String family;
    private String qualifier;
    private Long ts;
}
