package com.canet.poc.repository.dynamo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Map;

/**
 * DynamoDB item — single flat document keyed on CGI.
 * All device + location + enrichment fields are top-level attributes,
 * matching Option 1 from the POC spec.
 *
 * Table design: single table, PK = cgi, no sort key needed for primary lookup.
 * GSIs added for secondary access patterns (technology, region).
 */
@DynamoDbBean
public class HandsetItem {

    private String cgi;
    private String manufacturer;
    private String model;
    private String technology;
    private String imei;
    private String operatingSystem;
    private Double latitude;
    private Double longitude;
    private String cellId;
    private String lac;
    private String mcc;
    private String mnc;
    private String country;
    private String region;
    private String signalStrength;
    private String networkType;
    private String isp;
    private String subscriberTier;
    private Map<String, String> additionalAttributes;
    private Long lastUpdatedEpochMs;
    private String dataSource;

    @DynamoDbPartitionKey
    public String getCgi()                        { return cgi; }
    public void setCgi(String v)                  { this.cgi = v; }

    public String getManufacturer()               { return manufacturer; }
    public void setManufacturer(String v)         { this.manufacturer = v; }

    public String getModel()                      { return model; }
    public void setModel(String v)                { this.model = v; }

    public String getTechnology()                 { return technology; }
    public void setTechnology(String v)           { this.technology = v; }

    public String getImei()                       { return imei; }
    public void setImei(String v)                 { this.imei = v; }

    public String getOperatingSystem()            { return operatingSystem; }
    public void setOperatingSystem(String v)      { this.operatingSystem = v; }

    public Double getLatitude()                   { return latitude; }
    public void setLatitude(Double v)             { this.latitude = v; }

    public Double getLongitude()                  { return longitude; }
    public void setLongitude(Double v)            { this.longitude = v; }

    public String getCellId()                     { return cellId; }
    public void setCellId(String v)               { this.cellId = v; }

    public String getLac()                        { return lac; }
    public void setLac(String v)                  { this.lac = v; }

    public String getMcc()                        { return mcc; }
    public void setMcc(String v)                  { this.mcc = v; }

    public String getMnc()                        { return mnc; }
    public void setMnc(String v)                  { this.mnc = v; }

    public String getCountry()                    { return country; }
    public void setCountry(String v)              { this.country = v; }

    public String getRegion()                     { return region; }
    public void setRegion(String v)               { this.region = v; }

    public String getSignalStrength()             { return signalStrength; }
    public void setSignalStrength(String v)       { this.signalStrength = v; }

    public String getNetworkType()                { return networkType; }
    public void setNetworkType(String v)          { this.networkType = v; }

    public String getIsp()                        { return isp; }
    public void setIsp(String v)                  { this.isp = v; }

    public String getSubscriberTier()             { return subscriberTier; }
    public void setSubscriberTier(String v)       { this.subscriberTier = v; }

    public Map<String, String> getAdditionalAttributes()        { return additionalAttributes; }
    public void setAdditionalAttributes(Map<String, String> v)  { this.additionalAttributes = v; }

    public Long getLastUpdatedEpochMs()           { return lastUpdatedEpochMs; }
    public void setLastUpdatedEpochMs(Long v)     { this.lastUpdatedEpochMs = v; }

    public String getDataSource()                 { return dataSource; }
    public void setDataSource(String v)           { this.dataSource = v; }
}
