package com.wisedrive.inspection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for decrypted OBD scan data.
 * Maps to the JSON structure sent by the SDK.
 */
public class OBDScanData {
    
    @JsonProperty("license_plate")
    private String licensePlate;
    
    @JsonProperty("tracking_id")
    private String trackingId;
    
    @JsonProperty("report_url")
    private String reportUrl;
    
    @JsonProperty("car_company")
    private String carCompany;
    
    @JsonProperty("status")
    private Integer status;
    
    @JsonProperty("time")
    private String time;
    
    @JsonProperty("mechanic_name")
    private String mechanicName;
    
    @JsonProperty("mechanic_email")
    private String mechanicEmail;
    
    @JsonProperty("vin")
    private String vin;
    
    @JsonProperty("mil_status")
    private Boolean milStatus;
    
    @JsonProperty("scan_ended")
    private String scanEnded;
    
    @JsonProperty("faulty_modules")
    private List<String> faultyModules;
    
    @JsonProperty("non_faulty_modules")
    private List<String> nonFaultyModules;
    
    @JsonProperty("code_details")
    private List<DTCDetail> codeDetails;
    
    @JsonProperty("battery_voltage")
    private Double batteryVoltage;
    
    // Nested class for DTC details
    public static class DTCDetail {
        @JsonProperty("dtc")
        private String dtc;
        
        @JsonProperty("meaning")
        private String meaning;
        
        @JsonProperty("module")
        private String module;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("descriptions")
        private List<String> descriptions;
        
        @JsonProperty("causes")
        private List<String> causes;
        
        @JsonProperty("symptoms")
        private List<String> symptoms;
        
        @JsonProperty("solutions")
        private List<String> solutions;
        
        // Getters and Setters
        public String getDtc() { return dtc; }
        public void setDtc(String dtc) { this.dtc = dtc; }
        public String getMeaning() { return meaning; }
        public void setMeaning(String meaning) { this.meaning = meaning; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getDescriptions() { return descriptions; }
        public void setDescriptions(List<String> descriptions) { this.descriptions = descriptions; }
        public List<String> getCauses() { return causes; }
        public void setCauses(List<String> causes) { this.causes = causes; }
        public List<String> getSymptoms() { return symptoms; }
        public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }
        public List<String> getSolutions() { return solutions; }
        public void setSolutions(List<String> solutions) { this.solutions = solutions; }
    }
    
    // Getters and Setters
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }
    public String getCarCompany() { return carCompany; }
    public void setCarCompany(String carCompany) { this.carCompany = carCompany; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getMechanicName() { return mechanicName; }
    public void setMechanicName(String mechanicName) { this.mechanicName = mechanicName; }
    public String getMechanicEmail() { return mechanicEmail; }
    public void setMechanicEmail(String mechanicEmail) { this.mechanicEmail = mechanicEmail; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    public Boolean getMilStatus() { return milStatus; }
    public void setMilStatus(Boolean milStatus) { this.milStatus = milStatus; }
    public String getScanEnded() { return scanEnded; }
    public void setScanEnded(String scanEnded) { this.scanEnded = scanEnded; }
    public List<String> getFaultyModules() { return faultyModules; }
    public void setFaultyModules(List<String> faultyModules) { this.faultyModules = faultyModules; }
    public List<String> getNonFaultyModules() { return nonFaultyModules; }
    public void setNonFaultyModules(List<String> nonFaultyModules) { this.nonFaultyModules = nonFaultyModules; }
    public List<DTCDetail> getCodeDetails() { return codeDetails; }
    public void setCodeDetails(List<DTCDetail> codeDetails) { this.codeDetails = codeDetails; }
    public Double getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(Double batteryVoltage) { this.batteryVoltage = batteryVoltage; }
}
