package com.wisedrive.inspection.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisedrive.inspection.security.WiseDriveDecryptor;
import com.wisedrive.inspection.security.WiseDriveDecryptor.DecryptionException;
import com.wisedrive.inspection.security.WiseDriveDecryptor.EncryptedHeader;
import com.wisedrive.inspection.dto.EncryptedOBDPayload;
import com.wisedrive.inspection.dto.OBDScanData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypted OBD Webhook Controller
 * =================================
 * 
 * Receives encrypted OBD scan data from WiseDrive Android SDK.
 * Decrypts using RSA-4096 + AES-256-GCM and processes the data.
 * 
 * Endpoints:
 * - POST /apiv2/webhook/obdreport/wisedrive/encrypted - Receive encrypted data
 * - POST /apiv2/webhook/obdreport/wisedrive - Legacy plain JSON (deprecated)
 */
@RestController
@RequestMapping("/apiv2/webhook/obdreport")
public class EncryptedObdWebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(EncryptedObdWebhookController.class);
    
    // Replay protection - track seen payloads
    private final Set<String> seenPayloads = ConcurrentHashMap.newKeySet();
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000; // 5 minutes
    
    @Value("${wisedrive.encryption.private-key-path:}")
    private String privateKeyPath;
    
    @Value("${wisedrive.encryption.private-key:}")
    private String privateKeyDirect;
    
    private WiseDriveDecryptor decryptor;
    
    @PostConstruct
    public void init() {
        try {
            String privateKey;
            
            if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                // Load from file
                privateKey = new String(Files.readAllBytes(Paths.get(privateKeyPath)));
                logger.info("Loaded private key from file: {}", privateKeyPath);
            } else if (privateKeyDirect != null && !privateKeyDirect.isEmpty()) {
                // Use directly configured key
                privateKey = privateKeyDirect;
                logger.info("Using directly configured private key");
            } else {
                logger.warn("No private key configured! Encrypted endpoint will not work.");
                logger.warn("Set wisedrive.encryption.private-key-path or wisedrive.encryption.private-key");
                return;
            }
            
            decryptor = new WiseDriveDecryptor(privateKey);
            logger.info("WiseDrive decryptor initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize decryptor: {}", e.getMessage());
        }
    }
    
    /**
     * Receive ENCRYPTED OBD scan data from SDK.
     * 
     * Request Body:
     * {
     *   "version": 2,
     *   "keyId": 1,
     *   "timestamp": 1705312800000,
     *   "encryptedData": "V0RTVwACAAAAAQ..."
     * }
     */
    @PostMapping("/wisedrive/encrypted")
    public ResponseEntity<?> receiveEncryptedObd(@RequestBody EncryptedOBDPayload payload) {
        logger.info("Received encrypted OBD report");
        
        // Validate decryptor is initialized
        if (decryptor == null) {
            logger.error("Decryptor not initialized - private key not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Encryption not configured",
                    "message", "Server private key not configured"
                ));
        }
        
        // Validate payload
        if (payload == null || payload.getEncryptedData() == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing encryptedData field"));
        }
        
        // Check encryption version
        if (payload.getVersion() != 2) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Unsupported encryption version",
                    "supportedVersion", 2
                ));
        }
        
        try {
            // Get header info for logging
            EncryptedHeader header = decryptor.getHeaderInfo(payload.getEncryptedData());
            logger.debug("Encrypted payload header: {}", header);
            
            // Check for replay attack
            String payloadHash = String.valueOf(payload.getEncryptedData().hashCode());
            long now = System.currentTimeMillis();
            
            if (payload.getTimestamp() < now - REPLAY_WINDOW_MS) {
                logger.warn("Possible replay attack - timestamp too old: {}", payload.getTimestamp());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Timestamp expired"));
            }
            
            String replayKey = payload.getTimestamp() + ":" + payloadHash;
            if (seenPayloads.contains(replayKey)) {
                logger.warn("Duplicate payload detected - possible replay attack");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Duplicate payload"));
            }
            seenPayloads.add(replayKey);
            
            // Clean old entries periodically
            cleanOldReplayEntries();
            
            // Decrypt the payload
            JsonNode scanData = decryptor.decrypt(payload.getEncryptedData());
            
            logger.info("=== DECRYPTED OBD SCAN DATA ===");
            logger.info("License Plate: {}", scanData.path("license_plate").asText());
            logger.info("Tracking ID: {}", scanData.path("tracking_id").asText());
            logger.info("VIN: {}", scanData.path("vin").asText());
            logger.info("Car Company: {}", scanData.path("car_company").asText());
            logger.info("MIL Status: {}", scanData.path("mil_status").asBoolean());
            logger.info("DTCs Found: {}", scanData.path("code_details").size());
            logger.info("Battery Voltage: {}V", scanData.path("battery_voltage").asDouble());
            logger.info("================================");
            
            // Process the decrypted data (call existing service)
            String trackingId = scanData.path("tracking_id").asText();
            String licensePlate = scanData.path("license_plate").asText();
            
            // TODO: Call your existing OBD processing service
            // obdService.processReport(scanData);
            
            // Return success
            Map<String, Object> response = new HashMap<>();
            response.put("result", "SUCCESS");
            response.put("decrypted", true);
            response.put("trackingId", trackingId);
            response.put("licensePlate", licensePlate);
            response.put("dtcCount", scanData.path("code_details").size());
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (DecryptionException e) {
            logger.error("Decryption failed: {}", e.getMessage());
            
            String errorType = "decryption_failed";
            if (e.getMessage().contains("HMAC")) {
                errorType = "payload_tampered";
            } else if (e.getMessage().contains("RSA")) {
                errorType = "invalid_key";
            }
            
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", errorType,
                    "message", e.getMessage()
                ));
                
        } catch (Exception e) {
            logger.error("Error processing encrypted OBD report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "processing_failed",
                    "message", e.getMessage()
                ));
        }
    }
    
    /**
     * Legacy endpoint for plain JSON (DEPRECATED)
     * This endpoint receives unencrypted data and should be phased out.
     */
    @PostMapping("/wisedrive")
    @Deprecated
    public ResponseEntity<?> receiveLegacyObd(@RequestBody Map<String, Object> payload) {
        logger.warn("DEPRECATED: Legacy unencrypted endpoint called");
        logger.warn("Please upgrade SDK to use encrypted endpoint");
        
        // Log warning about unencrypted data
        String licensePlate = String.valueOf(payload.get("license_plate"));
        String trackingId = String.valueOf(payload.get("tracking_id"));
        
        logger.info("Received UNENCRYPTED report - License: {}, Tracking: {}", licensePlate, trackingId);
        
        // Process anyway for backward compatibility
        // TODO: Forward to existing processing logic
        
        return ResponseEntity.ok(Map.of(
            "result", "SUCCESS",
            "encrypted", false,
            "warning", "Please upgrade to encrypted SDK"
        ));
    }
    
    /**
     * Health check endpoint for encryption status
     */
    @GetMapping("/wisedrive/encrypted/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "WiseDrive OBD Encrypted Webhook");
        status.put("encryptionEnabled", decryptor != null);
        status.put("supportedVersion", 2);
        status.put("algorithm", "RSA-4096 + AES-256-GCM + HMAC-SHA512");
        status.put("timestamp", Instant.now().toString());
        
        if (decryptor == null) {
            status.put("warning", "Private key not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }
        
        return ResponseEntity.ok(status);
    }
    
    private void cleanOldReplayEntries() {
        // Simple cleanup - in production use scheduled task
        if (seenPayloads.size() > 10000) {
            seenPayloads.clear();
            logger.info("Cleared replay protection cache");
        }
    }
}
