package mx.habil.pedidos.exception;

import org.springframework.web.ErrorResponseException;

/**
 * Una queja con su código del catálogo.
 *
 * <p>Hereda de {@code ErrorResponseException}: Spring ya sabe convertirla en una respuesta
 * Problem Details, con el estado HTTP que dice el código. Quien la lanza solo elige el código y
 * escribe el detalle.
 */
public class PedidosException extends ErrorResponseException {

    private final transient CodigoError codigo;

    public PedidosException(CodigoError codigo, String detalle) {
        this(codigo, detalle, null);
    }

    public PedidosException(CodigoError codigo, String detalle, Throwable causa) {
        super(codigo.estado(), codigo.toProblemDetail(detalle), causa);
        this.codigo = codigo;
    }

    public CodigoError codigo() {
        return codigo;
    }
}
