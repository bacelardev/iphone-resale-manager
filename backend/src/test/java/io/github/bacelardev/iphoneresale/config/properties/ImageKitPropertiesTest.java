package io.github.bacelardev.iphoneresale.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageKitPropertiesTest {

    @Test
    void validatesProductionInputsAndRedactsPrivateKey() {
        ImageKitProperties properties = new ImageKitProperties(
                "private-sensitive-value", "https://ik.imagekit.io/account/", "delarte");

        assertThat(properties.requiredUrlEndpoint()).isEqualTo("https://ik.imagekit.io/account");
        assertThat(properties.normalizedFolder()).isEqualTo("/delarte");
        assertThat(properties.toString()).doesNotContain("private-sensitive-value");
    }

    @Test
    void rejectsMissingKeyAndNonHttpsEndpoint() {
        assertThatThrownBy(() -> new ImageKitProperties("", "https://example.test", null)
                .requiredPrivateKey()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageKitProperties("private", "http://example.test", null)
                .requiredUrlEndpoint()).isInstanceOf(IllegalArgumentException.class);
    }
}
