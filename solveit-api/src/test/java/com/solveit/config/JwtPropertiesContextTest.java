package com.solveit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesContextTest {

    private static final String VALID_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsValidJwtConfiguration() {
        contextRunner
                .withPropertyValues(
                        "solveit.security.jwt.secret=" + VALID_SECRET,
                        "solveit.security.jwt.issuer=solveit-tests",
                        "solveit.security.jwt.audience=solveit-api",
                        "solveit.security.jwt.access-token-ttl=45m")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.getSecret()).isEqualTo(VALID_SECRET);
                    assertThat(properties.getIssuer()).isEqualTo("solveit-tests");
                    assertThat(properties.getAudience()).isEqualTo("solveit-api");
                    assertThat(properties.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(45));
                });
    }

    @Test
    void rejectsMissingSecret() {
        contextRunner
                .withPropertyValues(validPropertiesWithoutSecret())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMalformedBase64Secret() {
        contextRunner
                .withPropertyValues(validPropertiesWithoutSecret())
                .withPropertyValues("solveit.security.jwt.secret=not-valid-base64!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsSecretShorterThanThirtyTwoDecodedBytes() {
        contextRunner
                .withPropertyValues(validPropertiesWithoutSecret())
                .withPropertyValues("solveit.security.jwt.secret=c2hvcnQ=")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] validPropertiesWithoutSecret() {
        return new String[] {
                "solveit.security.jwt.issuer=solveit-tests",
                "solveit.security.jwt.audience=solveit-api",
                "solveit.security.jwt.access-token-ttl=1h"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfiguration {
    }
}
