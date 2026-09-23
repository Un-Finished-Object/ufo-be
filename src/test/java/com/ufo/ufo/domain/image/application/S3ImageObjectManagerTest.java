package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.ufo.ufo.domain.image.config.ImageProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageObjectManagerTest {

    @Mock
    private S3Client s3Client;

    @Test
    void marksNewImageLinkedAndDeletesPreviousImage() {
        var properties = new ImageProperties(
                5, 10_485_760L, List.of("image/jpeg"), "https://cdn.example.com", "defaults/profile.png",
                new ImageProperties.S3("bucket", "ap-northeast-2", 5L, "https://s3.example.com")
        );
        var manager = new S3ImageObjectManager(s3Client, properties);

        manager.markLinked("profiles/1/new.png");
        manager.delete("profiles/1/old.png");

        ArgumentCaptor<PutObjectTaggingRequest> tagCaptor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3Client).putObjectTagging(tagCaptor.capture());
        assertThat(tagCaptor.getValue().key()).isEqualTo("profiles/1/new.png");
        assertThat(tagCaptor.getValue().tagging().tagSet()).anySatisfy(tag -> {
            assertThat(tag.key()).isEqualTo("ufo-upload-status");
            assertThat(tag.value()).isEqualTo("linked");
        });

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().key()).isEqualTo("profiles/1/old.png");
    }
}
