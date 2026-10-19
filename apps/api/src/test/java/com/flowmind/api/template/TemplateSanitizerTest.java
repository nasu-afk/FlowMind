package com.flowmind.api.template;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateSanitizerTest {
    private final TemplateSanitizer sanitizer=new TemplateSanitizer();
    @Test void acceptsCredentialReferencesOmittedFromTemplate(){assertThatCode(()->sanitizer.requireCredentialFree(Map.of("nodes",List.of(Map.of("type","slack","configuration",Map.of("channel","alerts")))))).doesNotThrowAnyException();}
    @Test void rejectsNestedSecrets(){assertThatThrownBy(()->sanitizer.requireCredentialFree(Map.of("nodes",List.of(Map.of("configuration",Map.of("apiKey","leak")))))).isInstanceOf(IllegalArgumentException.class);}
}
