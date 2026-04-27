package org.example.dndn.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.example.dndn.common.model.BaseResponseStatus.SUCCESS;

@Getter
@AllArgsConstructor
public class BaseResponse<T> {

    private final boolean isSuccess;
    private final int code;
    private final String message;
    private final T data;

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(
                SUCCESS.isSuccess(),
                SUCCESS.getCode(),
                SUCCESS.getMessage(),
                data
        );
    }

    public static BaseResponse<Void> fail(BaseResponseStatus status) {
        return new BaseResponse<>(
                status.isSuccess(),
                status.getCode(),
                status.getMessage(),
                null
        );
    }

    public static <T> BaseResponse<T> fail(BaseResponseStatus status, T data) {
        return new BaseResponse<>(
                status.isSuccess(),
                status.getCode(),
                status.getMessage(),
                data
        );
    }
}