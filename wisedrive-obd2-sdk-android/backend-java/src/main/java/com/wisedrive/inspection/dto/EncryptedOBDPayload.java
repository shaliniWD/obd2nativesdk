package com.wisedrive.inspection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for encrypted OBD payload from SDK.
 */
public class EncryptedOBDPayload {
    
    @JsonProperty("version")
    private int version;
    
    @JsonProperty("keyId")
    private int keyId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("encryptedData")
    private String encryptedData;
    
    // Getters and Setters
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
    
    public int getKeyId() {
        return keyId;
    }
    
    public void setKeyId(int keyId) {
        this.keyId = keyId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getEncryptedData() {
        return encryptedData;
    }
    
    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }
    
    @Override
    public String toString() {
        return String.format(
            "EncryptedOBDPayload{version=%d, keyId=%d, timestamp=%d, encryptedData=[%d chars]}",
            version, keyId, timestamp, encryptedData != null ? encryptedData.length() : 0
        );
    }
}
