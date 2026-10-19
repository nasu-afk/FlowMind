package com.flowmind.api.credential;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCryptoTest {
    private final String key=Base64.getEncoder().encodeToString(new byte[32]);
    @Test void roundTripsWithTenantContext(){var crypto=new EnvelopeCrypto(key,"test-v1");byte[] plaintext="secret".getBytes(StandardCharsets.UTF_8);var encrypted=crypto.encrypt(plaintext,"workspace-a");assertThat(new String(crypto.decrypt(encrypted,"workspace-a"),StandardCharsets.UTF_8)).isEqualTo("secret");assertThat(encrypted.ciphertext()).isNotEqualTo(plaintext);}
    @Test void refusesCrossTenantDecryption(){var crypto=new EnvelopeCrypto(key,"test-v1");var encrypted=crypto.encrypt("secret".getBytes(StandardCharsets.UTF_8),"workspace-a");assertThatThrownBy(()->crypto.decrypt(encrypted,"workspace-b")).isInstanceOf(IllegalStateException.class);}
}
