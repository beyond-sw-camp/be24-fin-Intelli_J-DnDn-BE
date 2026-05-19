package org.example.dndndocumentupload.common.exception;

import lombok.Getter;
import org.example.dndndocumentupload.common.model.BaseResponseStatus;

@Getter
public class BaseException extends RuntimeException {

    private final BaseResponseStatus status;

    public BaseException(BaseResponseStatus status) {
        super(status.getMessage());
        this.status = status;
    }

    public static BaseException from(BaseResponseStatus status) {
        return new BaseException(status);
    }
}