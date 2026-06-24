package com.canet.poc.repository.oracle;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OracleHandsetProjection {
    private String cgi;
    private String manufacturer;
    private String model;
    private String technology;
    private String region;
    private String country;
    private String dataSource;
}
