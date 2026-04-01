package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageFileMetadataMismatchException extends ApiException {
    public ImageFileMetadataMismatchException() {
        super(HttpStatus.BAD_REQUEST, "fileCount와 files 개수가 일치하지 않습니다.");
    }
}
