package io.github.bacelardev.iphoneresale.web.exception;

import io.github.bacelardev.iphoneresale.application.service.auth.AuthenticationFailedException;
import io.github.bacelardev.iphoneresale.application.service.auth.UnauthenticatedException;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.web.dto.error.ApiErrorResponse;
import io.github.bacelardev.iphoneresale.web.dto.error.FieldErrorResponse;
import io.github.bacelardev.iphoneresale.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> business(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getStatus()).body(error(
                request,
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiErrorResponse> authenticationFailed(
            AuthenticationFailedException exception,
            HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"iphone-resale\"");
        return new ResponseEntity<>(error(request, 401, "AUTHENTICATION_FAILED",
                "Usuário ou senha inválidos.", List.of()), headers, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiErrorResponse> unauthenticated(
            UnauthenticatedException exception,
            HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"iphone-resale\"");
        return new ResponseEntity<>(error(request, 401, "UNAUTHORIZED",
                "Autenticação válida é obrigatória para acessar este recurso.", List.of()),
                headers, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();
        return ResponseEntity.badRequest().body(error(request, 400, "VALIDATION_ERROR",
                "Existem campos inválidos.", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();
        return ResponseEntity.badRequest().body(error(request, 400, "VALIDATION_ERROR",
                "Existem campos inválidos.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(error(request, 400, "MALFORMED_REQUEST",
                "O corpo da requisição é inválido.", List.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> unsupportedMedia(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error(request, 415, "UNSUPPORTED_MEDIA_TYPE",
                        "O tipo de mídia enviado não é aceito.", List.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> uploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error(request, 413, "FILE_TOO_LARGE",
                        "Cada imagem deve possuir no máximo 10 MiB.", List.of()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiErrorResponse> invalidParameter(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(error(request, 400, "VALIDATION_ERROR",
                "Existem parâmetros inválidos.", List.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> concurrentModification(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                request, 409, "CONCURRENT_MODIFICATION",
                "O registro foi alterado por outra operação. Atualize os dados e tente novamente.",
                List.of()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> integrityConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                request, 409, "DATA_CONFLICT",
                "A operação conflita com o estado atual dos dados.", List.of()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(request, 404, "RESOURCE_NOT_FOUND",
                        "O recurso solicitado não foi encontrado.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled error requestId={} type={}",
                RequestIdFilter.requestId(request), exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(request, 500, "INTERNAL_ERROR",
                        "Não foi possível concluir a operação.", List.of()));
    }

    private ApiErrorResponse error(
            HttpServletRequest request,
            int status,
            String code,
            String message,
            List<FieldErrorResponse> fields
    ) {
        return new ApiErrorResponse(
                clock.instant(),
                status,
                code,
                message,
                request.getRequestURI(),
                RequestIdFilter.requestId(request),
                fields
        );
    }
}
