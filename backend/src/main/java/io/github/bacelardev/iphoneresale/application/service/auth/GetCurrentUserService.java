package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentUserService {

    private final CurrentUserIdProvider currentUserIdProvider;
    private final AuthUserStore userStore;

    public GetCurrentUserService(CurrentUserIdProvider currentUserIdProvider, AuthUserStore userStore) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userStore = userStore;
    }

    @Transactional(readOnly = true)
    public AuthUserData getCurrentUser() {
        return currentUserIdProvider.currentUserId()
                .flatMap(userStore::findActiveById)
                .orElseThrow(UnauthenticatedException::new);
    }
}
