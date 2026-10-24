package dev.beacon.api.errors;

import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.documents.PostingCaptureException;
import dev.beacon.domain.DomainException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, List<String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(
                                FieldError::getDefaultMessage,
                                Collectors.toList()
                        )
                ));

        ProblemDetail problem = ex.getBody();
        problem.setTitle("One or more validation errors occurred");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ProblemDetail handleRequestValidation(RequestValidationException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("One or more validation errors occurred");
        problem.setProperty("errors", Map.of(ex.getField(), List.of(ex.getMessage())));
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrency(OptimisticLockingFailureException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This application was changed by someone else. Reload it and try again.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(PostingCaptureException.class)
    public ProblemDetail handleCapture(PostingCaptureException ex) {
        // 502 when the page or browser failed (try again, or print it by hand); 422 when the request can't work.
        var status = ex.isUpstream() ? HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(422);
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Could not save the posting");
        return problem;
    }

    @ExceptionHandler(CoverLetterException.class)
    public ProblemDetail handleCoverLetter(CoverLetterException ex) {
        var status = ex.isUpstream() ? HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(422);
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Could not prepare the cover letter");
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

}
