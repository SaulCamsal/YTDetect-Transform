package com.monitorlatino.ytdetect.api.websub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignatureValidatorTest {

    private HmacSignatureValidator validator;
    private final String secret = "my-super-secret-key";

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator();
    }

    @Test
    @DisplayName("Should validate correct SHA1 signature")
    void shouldValidateCorrectSha1Signature() throws Exception {
        byte[] payload = "<feed>hello</feed>".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeHmac("HmacSHA1", secret, payload);
        String header = "sha1=" + expectedHash;

        assertTrue(validator.isValid(payload, header, secret));
    }

    @Test
    @DisplayName("Should validate correct SHA256 signature")
    void shouldValidateCorrectSha256Signature() throws Exception {
        byte[] payload = "<feed>hello world 256</feed>".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeHmac("HmacSHA256", secret, payload);
        String header = "sha256=" + expectedHash;

        assertTrue(validator.isValid(payload, header, secret));
    }

    @Test
    @DisplayName("Should reject tampered payload")
    void shouldRejectTamperedPayload() throws Exception {
        byte[] payload = "<feed>hello</feed>".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "<feed>tampered</feed>".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeHmac("HmacSHA1", secret, payload);
        String header = "sha1=" + expectedHash;

        assertFalse(validator.isValid(tampered, header, secret));
    }

    @Test
    @DisplayName("Should reject missing or malformed header")
    void shouldRejectMissingOrMalformedHeader() {
        byte[] payload = "<feed>hello</feed>".getBytes(StandardCharsets.UTF_8);

        assertFalse(validator.isValid(payload, null, secret));
        assertFalse(validator.isValid(payload, "", secret));
        assertFalse(validator.isValid(payload, "invalid_no_equals", secret));
        assertFalse(validator.isValid(payload, "md5=123456", secret));
    }

    private String computeHmac(String algorithm, String key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }
}
