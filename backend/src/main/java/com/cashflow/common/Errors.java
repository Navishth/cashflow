package com.cashflow.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
    public record ErrorBody(Instant timestamp,int status,String message,String path,Map<String,String> fieldErrors) {}
    private ResponseEntity<ErrorBody> response(int status,String message,Map<String,String> fields,HttpServletRequest r) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(),status,message,r.getRequestURI(),fields));
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<ErrorBody> api(ApiException e,HttpServletRequest r) { return response(e.status,e.getMessage(),Map.of(),r); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e,HttpServletRequest r) {
        var fields=new LinkedHashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(f->fields.put(f.getField(),f.getDefaultMessage()));
        return response(400,"Check the highlighted fields.",fields,r);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> malformed(Exception e,HttpServletRequest r) { return response(400,"Invalid request value or JSON format.",Map.of(),r); }
    @ExceptionHandler({DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ErrorBody> conflict(Exception e,HttpServletRequest r) { return response(409,"This record conflicts with an existing or recently changed record. Refresh and retry.",Map.of(),r); }
    @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> unexpected(Exception e,HttpServletRequest r) {
        // Do not log exception messages that can contain SQL parameters, tokens, or financial data.
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed: {}",e.getClass().getSimpleName());
        return response(500,"The request could not be completed. Please try again.",Map.of(),r);
    }
}
