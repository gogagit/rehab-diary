package ru.coursework.rehab.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.coursework.rehab.service.BusinessException;
@RestControllerAdvice
public class ApiExceptionHandler {
    private ResponseEntity<ProblemDetail> problem(int code,String text) { return ResponseEntity.status(code).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code),text)); }
    @ExceptionHandler(BusinessException.class) ResponseEntity<ProblemDetail> business(BusinessException e) { return problem(e.status,e.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ProblemDetail> denied() { return problem(403,"Нет доступа к выбранным данным или действию"); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        return problem(400,"Проверьте поля: "+e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+" — "+x.getDefaultMessage()).distinct().collect(java.util.stream.Collectors.joining("; ")));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ProblemDetail> malformed() { return problem(400,"Неверный формат запроса или значения поля"); }
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,org.springframework.dao.PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict() { return problem(409,"Данные уже существуют или изменены другим пользователем. Обновите страницу"); }
}
