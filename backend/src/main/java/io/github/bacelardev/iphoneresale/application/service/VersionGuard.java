package io.github.bacelardev.iphoneresale.application.service;

public final class VersionGuard {

    private VersionGuard() {
    }

    public static void require(Long actual, long expected) {
        if (actual == null || actual != expected) {
            throw BusinessException.conflict(
                    "CONCURRENT_MODIFICATION",
                    "O registro foi alterado por outra operação. Atualize os dados e tente novamente."
            );
        }
    }
}
