package com.flowmind.api.trigger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public class WebhookSignatureVerifier {
    private final Clock clock;
    private final Duration tolerance;
    public WebhookSignatureVerifier(Clock clock, Duration tolerance) { this.clock = clock; this.tolerance = tolerance; }

    public boolean verify(byte[] body, String timestamp, String suppliedSignature, byte[] secret) {
        try {
            long epochSeconds = Long.parseLong(timestamp);
            if (Duration.between(Instant.ofEpochSecond(epochSeconds), clock.instant()).abs().compareTo(tolerance) > 0) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            byte[] expected = mac.doFinal(body);
            byte[] actual = HexFormat.of().parseHex(suppliedSignature.replaceFirst("^sha256=", ""));
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception invalid) {
            return false;
        }
    }
}

