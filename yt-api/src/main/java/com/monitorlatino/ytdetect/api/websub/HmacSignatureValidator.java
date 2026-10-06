package com.monitorlatino.ytdetect.api.websub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HmacSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureValidator.class);

    public boolean isValid(byte[] payload, String signatureHeader, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            log.warn("Missing signature header or secret");
            return false;
        }

        String[] parts = signatureHeader.split("=", 2);
        if (parts.length != 2) {
            log.warn("Invalid signature format: {}", signatureHeader);
            return false;
        }

        String algorithm = parts[0].trim().toLowerCase();
        String expectedHash = parts[1].trim();

        String macAlgorithm = switch (algorithm) {
            case "sha1" -> "HmacSHA1";
            case "sha256" -> "HmacSHA256";
            default -> null;
        };

        if (macAlgorithm == null) {
            log.warn("Unsupported HMAC algorithm: {}", algorithm);
            return false;
        }

        try {
            Mac mac = Mac.getInstance(macAlgorithm);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm);
            mac.init(secretKey);
            byte[] calculatedBytes = mac.doFinal(payload);
            String calculatedHex = HexFormat.of().formatHex(calculatedBytes);

            return MessageDigest.isEqual(
                    calculatedHex.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error computing HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }
}
