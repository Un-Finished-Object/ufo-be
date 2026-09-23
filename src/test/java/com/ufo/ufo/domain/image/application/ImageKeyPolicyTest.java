package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.exception.ProfileImagePermissionDeniedException;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageKeyPolicyTest {

    private final ImageKeyPolicy policy = new ImageKeyPolicy(new ImageProperties(
            5, 10_485_760L, List.of("image/jpeg"), "https://cdn.example.com", "defaults/profile.png",
            new ImageProperties.S3("bucket", "ap-northeast-2", 5L, "https://s3.example.com")
    ));

    @Test
    void encodesEachObjectKeySegmentInImageUrl() {
        assertThat(policy.buildImageUrl("profiles/1/my image+.png"))
                .isEqualTo("https://cdn.example.com/profiles/1/my%20image%2B.png");
    }

    @Test
    void rejectsAnotherUsersProfileKey() {
        var user = UserFixture.createUserWithId(1L);

        assertThatThrownBy(() -> policy.validateProfileImageKey(user, "profiles/2/avatar.png"))
                .isInstanceOf(ProfileImagePermissionDeniedException.class);
    }
}
