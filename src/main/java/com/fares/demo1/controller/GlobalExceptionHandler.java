package com.fares.demo1.controller;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps a couple of expected failure cases to a proper HTTP status instead of the
 * default 500, so a client can tell "you did something reasonable but it didn't work"
 * from "the server broke".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A lost optimistic-locking race - two writers touching the same row at once, see
     * {@code MonitorEventEntity.version} - becomes 409 so the client knows to re-fetch
     * and retry rather than treating it as a server error.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConflict(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This record was updated by someone else - reload and try again.");
    }

    /** An admin action (ack/resolve/...) referenced an id that doesn't exist. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** A request carried a value that's syntactically fine but out of a sane range. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
