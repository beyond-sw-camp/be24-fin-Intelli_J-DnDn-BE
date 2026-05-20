package org.example.dndncore.common.exception;

import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.common.model.BaseResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<Void>> handleBaseException(BaseException e) {
        BaseResponseStatus status = e.getStatus();

        return ResponseEntity
                .status(statusCodeMapper(status.getCode()))
                .body(BaseResponse.fail(status));
    }

    private HttpStatus statusCodeMapper(int errorCode) {
        if (errorCode >= 5000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (errorCode >= 3000) {
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.OK;
    }
}