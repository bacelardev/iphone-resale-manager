package io.github.bacelardev.iphoneresale.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256AccessTokenHasherTest {

    @Test
    void hashesTokenWithLowercaseSha256Hex() {
        String hash = new Sha256AccessTokenHasher().hash("abc");

        assertThat(hash).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
}
