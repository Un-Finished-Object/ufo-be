package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 목록 응답 생성 테스트")
class PatternListItemResponseFactoryTest {

    @Mock
    private ScrapRepository scrapRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private PatternListItemResponseFactory responseFactory;

    @Test
    @DisplayName("로그인 사용자의 찜 여부와 이미지 URL을 반영한다")
    void create_LoggedInUser_ReflectsScrapAndImageUrl() {
        var user = UserFixture.createUserWithId(1L);
        var pattern = PatternFixture.createPatternWithId(10L);
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 10L)).thenReturn(true);
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn.example.com/pattern.png");

        var response = responseFactory.create(pattern, user);

        assertThat(response.my().scrapped()).isTrue();
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/pattern.png");
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("비로그인 사용자의 찜 여부는 false이고 찜 조회를 하지 않는다")
    void create_AnonymousUser_ReturnsUnscrapped() {
        var pattern = PatternFixture.createPatternWithId(10L);
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn.example.com/pattern.png");

        var response = responseFactory.create(pattern, null);

        assertThat(response.my().scrapped()).isFalse();
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/pattern.png");
        verifyNoInteractions(scrapRepository);
    }

    @Test
    @DisplayName("ID가 없는 사용자의 찜 여부는 false이고 찜 조회를 하지 않는다")
    void create_UserWithoutId_ReturnsUnscrapped() {
        var pattern = PatternFixture.createPatternWithId(10L);

        var response = responseFactory.create(pattern, UserFixture.createUser());

        assertThat(response.my().scrapped()).isFalse();
        verifyNoInteractions(scrapRepository);
    }
}
