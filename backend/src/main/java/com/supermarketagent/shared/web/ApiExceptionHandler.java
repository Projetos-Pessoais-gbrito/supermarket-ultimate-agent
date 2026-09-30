package com.supermarketagent.shared.web;

import com.supermarketagent.auth.InvalidRefreshTokenException;
import com.supermarketagent.user.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps domain errors to RFC 9457 problem details; Spring MVC errors are handled by the base class. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail emailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail invalidRefreshToken(InvalidRefreshTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid e-mail or password");
    }
}
