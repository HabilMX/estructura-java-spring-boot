package mx.habil.pedidos.exception;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * El libro de quejas: el catálogo de todo lo que esta API puede contestar cuando algo sale mal.
 *
 * <p>Cada queja tiene un código fijo y su estado HTTP. Y aquí está el único lugar donde se arma
 * el cuerpo del error, con la forma de Problem Details (RFC 9457).
 */
public enum CodigoError {

    SOLICITUD_INVALIDA(HttpStatus.UNPROCESSABLE_CONTENT, "La solicitud no es válida"),
    PEDIDO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "El pedido no existe"),
    PRODUCTO_NO_EXISTE(HttpStatus.UNPROCESSABLE_CONTENT, "El producto no existe"),
    SIN_EXISTENCIAS(HttpStatus.CONFLICT, "No hay existencias suficientes"),
    PROVEEDOR_NO_RESPONDE(HttpStatus.GATEWAY_TIMEOUT, "El proveedor no contestó a tiempo"),
    PROVEEDOR_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo consultar al proveedor"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");

    /** La dirección base de los tipos de error. Cámbiala por el dominio de tu producto. */
    static final String BASE = "https://pedidos.example/errors/";

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() {
        return estado;
    }

    /** El punto único donde se arma un error. Nadie más construye un {@code ProblemDetail}. */
    public ProblemDetail toProblemDetail(String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE + name().toLowerCase(Locale.ROOT)));
        problema.setTitle(titulo);
        problema.setProperty("error_code", name());
        return problema;
    }
}
