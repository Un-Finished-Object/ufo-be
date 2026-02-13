package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Provider;
import com.ufo.ufo.global.security.types.Role;

public final class UserFixture {

    private UserFixture() {
    }

    public static User createUser() {
        return createUser("test@example.com", Role.ROLE_USER);
    }

    public static User createUser(String email, Role role) {
        return User.builder()
                .email(email)
                .nickname("tester")
                .profileImage("https://example.com/profile.png")
                .role(role)
                .provider(Provider.GOOGLE)
                .build();
    }

    public static User createUserWithId(Long id) {
        User user = createUser();
        setId(user, id);
        return user;
    }

    public static void setId(User user, Long id) {
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
