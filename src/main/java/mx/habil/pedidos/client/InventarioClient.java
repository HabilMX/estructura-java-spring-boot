package mx.habil.pedidos.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import mx.habil.pedidos.exception.CodigoError;
import mx.habil.pedidos.exception.PedidosException;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * El proveedor de afuera: pregunta a otro sistema cuántas piezas hay de un producto y cuánto
 * cuestan.
 *
 * <p>Es el único lugar que sabe que el inventario se consulta por HTTP. Lleva tiempo límite (se
 * configura en {@code application.yml}) y reintento (lo arma {@code config}). Y traduce las
 * fallas de la red a quejas del catálogo: quien lo llama nunca ve una excepción de HTTP.
 */
@Component
public class InventarioClient {

    private final RestClient http;
    private final RetryTemplate reintento;

    public InventarioClient(RestClient inventarioRestClient, RetryTemplate inventarioReintento) {
        this.http = inventarioRestClient;
        this.reintento = inventarioReintento;
    }

    /**
     * Consulta un producto.
     *
     * @return el producto, o vacío si el proveedor contesta que no existe
     * @throws PedidosException si el proveedor no contesta a tiempo o no se puede hablar con él
     */
    public Optional<Producto> consultar(String sku) {
        try {
            return reintento.execute(() -> pedir(sku));
        } catch (RetryException agotado) {
            throw traducir(agotado.getCause());
        }
    }

    private Optional<Producto> pedir(String sku) {
        try {
            return Optional.ofNullable(http.get()
                    .uri("/productos/{sku}.json", sku)
                    .retrieve()
                    .body(Producto.class));
        } catch (HttpClientErrorException.NotFound noExiste) {
            return Optional.empty();
        }
    }

    private static PedidosException traducir(Throwable causa) {
        if (causa instanceof ResourceAccessException sinRespuesta
                && sinRespuesta.getCause() instanceof HttpTimeoutException) {
            return new PedidosException(CodigoError.PROVEEDOR_NO_RESPONDE,
                    "El inventario no contestó a tiempo", causa);
        }
        if (causa instanceof RestClientException) {
            return new PedidosException(CodigoError.PROVEEDOR_NO_DISPONIBLE,
                    "No se pudo consultar el inventario", causa);
        }
        throw new IllegalStateException("falla inesperada al consultar el inventario", causa);
    }

    /**
     * Lo que contesta el proveedor. Si mañana agrega un campo, se ignora: la lectura no debe
     * romperse por un dato que no usamos.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Producto(String sku, int existencias, long precioCentavos) {
    }
}
