package mx.habil.pedidos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import mx.habil.pedidos.exception.CodigoError;
import mx.habil.pedidos.exception.PedidosException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * El proveedor se prueba contra un servidor HTTP de verdad, en esta misma máquina: lo que se
 * quiere ver es qué pasa cuando tarda, cuando no existe y cuando no contesta.
 */
class InventarioClientTest {

    private static final String CAFE = """
            {"sku": "CAFE-250", "existencias": 40, "precio_centavos": 18950, "bodega": "norte"}
            """;

    private ProveedorDePrueba proveedor;

    @BeforeEach
    void abrir() {
        proveedor = new ProveedorDePrueba();
    }

    @AfterEach
    void cerrar() {
        proveedor.close();
    }

    /** Un cliente armado como lo arma {@code config}, con tiempos cortos para que la prueba no tarde. */
    private InventarioClient cliente(String url, int reintentos) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build());
        fabrica.setReadTimeout(Duration.ofMillis(300));
        RestClient http = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .configureMessageConverters(c -> c.withJsonConverter(
                        new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(
                                tools.jackson.databind.json.JsonMapper.builder()
                                        .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                                        .build())))
                .build();
        RetryTemplate reintento = new RetryTemplate(RetryPolicy.builder()
                .includes(ResourceAccessException.class)
                .maxRetries(reintentos)
                .delay(Duration.ofMillis(10))
                .build());
        return new InventarioClient(http, reintento);
    }

    @Test
    void leeElProductoEIgnoraLosCamposQueNoConoce() {
        proveedor.contestar(200, CAFE);

        assertThat(cliente(proveedor.url(), 2).consultar("CAFE-250"))
                .contains(new InventarioClient.Producto("CAFE-250", 40, 18950));
        assertThat(proveedor.rutas()).containsExactly("/productos/CAFE-250.json");
    }

    @Test
    void unNoExisteDelProveedorEsVacioYNoSeReintenta() {
        proveedor.contestar(404, "");

        assertThat(cliente(proveedor.url(), 2).consultar("NO-EXISTE")).isEmpty();
        assertThat(proveedor.rutas()).hasSize(1);
    }

    @Test
    void siTardaDeMasSeReintentaYLaSegundaSale() {
        proveedor.tardar(Duration.ofMillis(900), 200, CAFE).contestar(200, CAFE);

        assertThat(cliente(proveedor.url(), 2).consultar("CAFE-250")).isPresent();
        assertThat(proveedor.rutas()).hasSize(2);
    }

    @Test
    void siNuncaContestaATiempoEsProveedorNoResponde() {
        proveedor.tardar(Duration.ofMillis(900), 200, CAFE)
                .tardar(Duration.ofMillis(900), 200, CAFE)
                .tardar(Duration.ofMillis(900), 200, CAFE);
        InventarioClient cliente = cliente(proveedor.url(), 2);

        assertThatThrownBy(() -> cliente.consultar("CAFE-250"))
                .isInstanceOfSatisfying(PedidosException.class, queja -> {
                    assertThat(queja.codigo()).isEqualTo(CodigoError.PROVEEDOR_NO_RESPONDE);
                    assertThat(queja.getStatusCode().value()).isEqualTo(504);
                });
        // Una llamada y dos reintentos: tres en total, ni una más.
        assertThat(proveedor.rutas()).hasSize(3);
    }

    @Test
    void siNoHayNadieEnEsaDireccionEsProveedorNoDisponible() throws Exception {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }
        InventarioClient cliente = cliente("http://127.0.0.1:" + puertoLibre, 1);

        assertThatThrownBy(() -> cliente.consultar("CAFE-250"))
                .isInstanceOfSatisfying(PedidosException.class, queja -> {
                    assertThat(queja.codigo()).isEqualTo(CodigoError.PROVEEDOR_NO_DISPONIBLE);
                    assertThat(queja.getStatusCode().value()).isEqualTo(503);
                });
    }

    @Test
    void unErrorDelProveedorNoSeReintentaYEsProveedorNoDisponible() {
        proveedor.contestar(500, "");
        InventarioClient cliente = cliente(proveedor.url(), 2);

        assertThatThrownBy(() -> cliente.consultar("CAFE-250"))
                .isInstanceOfSatisfying(PedidosException.class,
                        queja -> assertThat(queja.codigo()).isEqualTo(CodigoError.PROVEEDOR_NO_DISPONIBLE));
        assertThat(proveedor.rutas()).hasSize(1);
    }
}
