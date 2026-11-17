package mx.habil.pedidos.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Quien atiende las quejas: convierte cualquier excepción en una respuesta Problem Details.
 *
 * <p>Hereda de {@code ResponseEntityExceptionHandler}, que ya sabe contestar los errores de
 * Spring MVC (una ruta que no existe, un JSON mal escrito, un método no permitido) y las
 * {@code PedidosException}. Aquí solo se agregan tres cosas: el detalle de una comanda mal
 * llenada, la bitácora de todo error y la red para lo que nadie esperaba.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Una comanda mal llenada: 422, con la lista de campos que fallaron. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> violaciones = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage())))
                .sorted((a, b) -> a.get("campo").compareTo(b.get("campo")))
                .toList();
        ProblemDetail problema = CodigoError.SOLICITUD_INVALIDA.toProblemDetail(
                "La solicitud tiene %d campo(s) inválido(s)".formatted(violaciones.size()));
        problema.setProperty("violations", violaciones);
        return handleExceptionInternal(ex, problema, headers, CodigoError.SOLICITUD_INVALIDA.estado(), request);
    }

    /** Lo que nadie esperaba: 500, sin contarle al cliente qué pasó por dentro. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> inesperado(Exception ex, WebRequest request) {
        ProblemDetail problema = CodigoError.ERROR_INTERNO.toProblemDetail(
                "Inténtalo de nuevo; si se repite, repórtalo con el X-Request-ID de esta respuesta");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), CodigoError.ERROR_INTERNO.estado(), request);
    }

    /** Todo error pasa por aquí antes de salir: es el único lugar donde se escribe en la bitácora. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String detalle = ex.getClass().getSimpleName();
        if (body instanceof ProblemDetail problema && problema.getDetail() != null) {
            detalle = problema.getDetail();
        } else if (ex instanceof ErrorResponse respuesta && respuesta.getBody().getDetail() != null) {
            detalle = respuesta.getBody().getDetail();
        }
        if (statusCode.is5xxServerError()) {
            log.error("respuesta {}: {}", statusCode.value(), detalle, ex);
        } else {
            log.warn("respuesta {}: {}", statusCode.value(), detalle);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
