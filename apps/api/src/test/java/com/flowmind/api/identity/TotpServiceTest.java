package com.flowmind.api.identity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    private final TotpService service=new TotpService();

    @Test void matchesRfc6238Sha1VectorAtEightDigitsTruncatedToConfiguredSix() {
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(service.generate(secret,59/30)).isEqualTo("287082");
        assertThat(service.verify(secret,"287082",Instant.ofEpochSecond(59))).isTrue();
    }

    @Test void acceptsAdjacentTimeStepAndRejectsMalformedCodes() {
        String secret=service.generateSecret();
        Instant now=Instant.ofEpochSecond(1_700_000_000L);
        assertThat(service.verify(secret,service.generate(secret,now.getEpochSecond()/30-1),now)).isTrue();
        assertThat(service.verify(secret,"12345",now)).isFalse();
    }
}
