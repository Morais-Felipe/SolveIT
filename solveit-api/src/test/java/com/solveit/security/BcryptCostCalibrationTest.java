package com.solveit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BcryptCostCalibrationTest {

    private static final String SAMPLE_PASSWORD = "senha segura para calibracao";

    @Test
    void measuresSupportedStrengthsWithoutAFragileTimingLimit() {
        for (int strength : new int[] {10, 11, 12}) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);

            long encodeStartedAt = System.nanoTime();
            String hash = encoder.encode(SAMPLE_PASSWORD);
            long encodeMilliseconds = elapsedMilliseconds(encodeStartedAt);

            long matchStartedAt = System.nanoTime();
            boolean matches = encoder.matches(SAMPLE_PASSWORD, hash);
            long matchMilliseconds = elapsedMilliseconds(matchStartedAt);

            System.out.printf(
                    Locale.ROOT,
                    "BCrypt strength %d: encode=%d ms, match=%d ms%n",
                    strength,
                    encodeMilliseconds,
                    matchMilliseconds);

            assertThat(hash).startsWith("$2");
            assertThat(matches).isTrue();
        }
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
