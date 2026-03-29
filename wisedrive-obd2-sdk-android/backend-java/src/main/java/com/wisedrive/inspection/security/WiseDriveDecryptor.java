package com.wisedrive.inspection.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WiseDrive OBD2 SDK Decryption Module
 * =====================================
 * 
 * Decrypts OBD scan data encrypted by the WiseDrive Android SDK.
 * 
 * Encryption Layers:
 * - Layer 1: AES-256-GCM (data encryption)
 * - Layer 2: RSA-4096-OAEP (key encryption)
 * - Layer 3: HMAC-SHA512 (integrity verification)
 * 
 * Usage:
 * <pre>
 * WiseDriveDecryptor decryptor = new WiseDriveDecryptor(privateKeyPem);
 * JsonNode scanData = decryptor.decrypt(encryptedBase64);
 * String licensePlate = scanData.get("license_plate").asText();
 * </pre>
 * 
 * @author WiseDrive Technologies
 * @version 2.0.0
 */
public class WiseDriveDecryptor {
    
    private static final Logger logger = LoggerFactory.getLogger(WiseDriveDecryptor.class);
    
    // Header constants
    private static final int HEADER_SIZE = 16;
    private static final int RSA_4096_SIZE = 512;  // 4096 bits = 512 bytes
    private static final int RSA_2048_SIZE = 256;  // 2048 bits = 256 bytes
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int HMAC_SIZE = 64;  // SHA-512 = 64 bytes
    
    // Valid magic bytes
    private static final String MAGIC_CLIENT = "WDSC";
    private static final String MAGIC_WISEDRIVE = "WDSW";
    
    private final PrivateKey privateKey;
    private final int rsaKeySize;
    private final ObjectMapper objectMapper;
    private final Integer expectedKeyId;
    
    /**
     * Create decryptor with RSA private key.
     * 
     * @param privateKeyPem PEM-encoded RSA private key (PKCS#8 format)
     * @throws DecryptionException if key loading fails
     */
    public WiseDriveDecryptor(String privateKeyPem) throws DecryptionException {
        this(privateKeyPem, null);
    }
    
    /**
     * Create decryptor with RSA private key and expected key ID.
     * 
     * @param privateKeyPem PEM-encoded RSA private key
     * @param expectedKeyId Expected key ID for validation (optional)
     * @throws DecryptionException if key loading fails
     */
    public WiseDriveDecryptor(String privateKeyPem, Integer expectedKeyId) throws DecryptionException {
        this.expectedKeyId = expectedKeyId;
        this.objectMapper = new ObjectMapper();
        
        try {
            // Parse PEM key
            String keyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(keySpec);
            
            // Determine key size
            int keyBitLength = ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus().bitLength();
            if (keyBitLength >= 4000) {
                this.rsaKeySize = RSA_4096_SIZE;
            } else if (keyBitLength >= 2000) {
                this.rsaKeySize = RSA_2048_SIZE;
            } else {
                throw new DecryptionException("Unsupported RSA key size: " + keyBitLength + " bits");
            }
            
            logger.info("WiseDriveDecryptor initialized with RSA-{} key", keyBitLength);
            
        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new DecryptionException("Failed to load private key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decrypt encrypted OBD scan data.
     * 
     * @param encryptedBase64 Base64-encoded encrypted payload from SDK
     * @return Decrypted JSON data as JsonNode
     * @throws DecryptionException if decryption fails
     */
    public JsonNode decrypt(String encryptedBase64) throws DecryptionException {
        try {
            // 1. Decode Base64
            byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);
            
            // 2. Validate minimum size
            int minSize = HEADER_SIZE + rsaKeySize + GCM_IV_SIZE + 16 + HMAC_SIZE;
            if (encryptedData.length < minSize) {
                throw new DecryptionException("Payload too small: " + encryptedData.length + " bytes (minimum: " + minSize + ")");
            }
            
            // 3. Parse header
            EncryptedHeader header = parseHeader(encryptedData);
            logger.debug("Parsed header: magic={}, version={}, keyId={}", header.magic, header.version, header.keyId);
            
            // 4. Validate key ID if specified
            if (expectedKeyId != null && header.keyId != expectedKeyId) {
                throw new DecryptionException("Key ID mismatch: expected " + expectedKeyId + ", got " + header.keyId);
            }
            
            // 5. Extract components
            int offset = HEADER_SIZE;
            byte[] encryptedAesKey = Arrays.copyOfRange(encryptedData, offset, offset + rsaKeySize);
            offset += rsaKeySize;
            
            byte[] iv = Arrays.copyOfRange(encryptedData, offset, offset + GCM_IV_SIZE);
            offset += GCM_IV_SIZE;
            
            byte[] ciphertextWithTag = Arrays.copyOfRange(encryptedData, offset, encryptedData.length - HMAC_SIZE);
            byte[] hmacSignature = Arrays.copyOfRange(encryptedData, encryptedData.length - HMAC_SIZE, encryptedData.length);
            
            // 6. Decrypt AES key with RSA
            byte[] aesKey = decryptAesKey(encryptedAesKey);
            
            // 7. Derive HMAC key and verify
            byte[] hmacKey = deriveHmacKey(aesKey);
            byte[] dataToVerify = Arrays.copyOfRange(encryptedData, 0, encryptedData.length - HMAC_SIZE);
            verifyHmac(dataToVerify, hmacSignature, hmacKey);
            
            // 8. Decrypt data with AES-GCM
            byte[] plaintext = decryptAesGcm(ciphertextWithTag, aesKey, iv);
            
            // 9. Parse JSON
            String jsonString = new String(plaintext, StandardCharsets.UTF_8);
            return objectMapper.readTree(jsonString);
            
        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new DecryptionException("Decryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decrypt and return as specific class type.
     * 
     * @param encryptedBase64 Base64-encoded encrypted payload
     * @param valueType Class to deserialize to
     * @return Deserialized object
     * @throws DecryptionException if decryption fails
     */
    public <T> T decrypt(String encryptedBase64, Class<T> valueType) throws DecryptionException {
        try {
            JsonNode jsonNode = decrypt(encryptedBase64);
            return objectMapper.treeToValue(jsonNode, valueType);
        } catch (Exception e) {
            throw new DecryptionException("Failed to deserialize decrypted data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get header information without decrypting.
     * Useful for routing or logging.
     * 
     * @param encryptedBase64 Base64-encoded encrypted payload
     * @return Parsed header
     * @throws DecryptionException if header parsing fails
     */
    public EncryptedHeader getHeaderInfo(String encryptedBase64) throws DecryptionException {
        try {
            byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);
            return parseHeader(encryptedData);
        } catch (Exception e) {
            throw new DecryptionException("Failed to parse header: " + e.getMessage(), e);
        }
    }
    
    // ========================================================================
    // Private helper methods
    // ========================================================================
    
    private EncryptedHeader parseHeader(byte[] data) throws DecryptionException {
        if (data.length < HEADER_SIZE) {
            throw new DecryptionException("Data too small for header");
        }
        
        String magic = new String(Arrays.copyOfRange(data, 0, 4), StandardCharsets.UTF_8);
        if (!MAGIC_CLIENT.equals(magic) && !MAGIC_WISEDRIVE.equals(magic)) {
            throw new DecryptionException("Invalid magic bytes: " + magic);
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(4);
        
        int version = buffer.getShort() & 0xFFFF;
        int keyId = buffer.getInt();
        
        // Timestamp is 6 bytes
        byte[] timestampBytes = new byte[8];
        buffer.get(timestampBytes, 2, 6);
        long timestamp = ByteBuffer.wrap(timestampBytes).getLong();
        
        return new EncryptedHeader(magic, version, keyId, timestamp);
    }
    
    private byte[] decryptAesKey(byte[] encryptedKey) throws DecryptionException {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedKey);
        } catch (Exception e) {
            throw new DecryptionException("RSA decryption failed (wrong key?): " + e.getMessage(), e);
        }
    }
    
    private byte[] deriveHmacKey(byte[] aesKey) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(aesKey);
        md.update("HMAC_KEY_DERIVATION".getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }
    
    private void verifyHmac(byte[] data, byte[] signature, byte[] key) throws DecryptionException {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key, "HmacSHA512"));
            byte[] expected = mac.doFinal(data);
            
            if (!MessageDigest.isEqual(expected, signature)) {
                throw new DecryptionException("HMAC verification failed - payload has been tampered!");
            }
        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new DecryptionException("HMAC verification error: " + e.getMessage(), e);
        }
    }
    
    private byte[] decryptAesGcm(byte[] ciphertextWithTag, byte[] key, byte[] iv) throws DecryptionException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
            return cipher.doFinal(ciphertextWithTag);
        } catch (Exception e) {
            throw new DecryptionException("AES-GCM decryption failed: " + e.getMessage(), e);
        }
    }
    
    // ========================================================================
    // Inner classes
    // ========================================================================
    
    /**
     * Parsed header from encrypted payload.
     */
    public static class EncryptedHeader {
        public final String magic;
        public final int version;
        public final int keyId;
        public final long timestamp;
        
        public EncryptedHeader(String magic, int version, int keyId, long timestamp) {
            this.magic = magic;
            this.version = version;
            this.keyId = keyId;
            this.timestamp = timestamp;
        }
        
        public boolean isClientPayload() {
            return MAGIC_CLIENT.equals(magic);
        }
        
        public boolean isWiseDrivePayload() {
            return MAGIC_WISEDRIVE.equals(magic);
        }
        
        @Override
        public String toString() {
            return String.format("EncryptedHeader{magic='%s', version=%d, keyId=%d, timestamp=%d}",
                magic, version, keyId, timestamp);
        }
    }
    
    /**
     * Exception thrown when decryption fails.
     */
    public static class DecryptionException extends Exception {
        public DecryptionException(String message) {
            super(message);
        }
        
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
