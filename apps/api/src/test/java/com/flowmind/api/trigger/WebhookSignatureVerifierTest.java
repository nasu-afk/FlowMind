package com.flowmind.api.trigger;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {
    @Test void verifiesSignatureAndRejectsReplayOutsideTolerance() throws Exception {
        var now = Instant.parse("2026-10-16T00:00:00Z");
        var verifier = new WebhookSignatureVerifier(Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5));
        byte[] body = "{\"event\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        byte[] secret = "test-secret".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(now.getEpochSecond());
        assertThat(verifier.verify(body, timestamp, sign(timestamp, body, secret), secret)).isTrue();
        assertThat(verifier.verify(body, Long.toString(now.minus(Duration.ofMinutes(6)).getEpochSecond()), sign(timestamp, body, secret), secret)).isFalse();
    }
    private String sign(String timestamp, byte[] body, byte[] secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8)); mac.update((byte) '.'); return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
