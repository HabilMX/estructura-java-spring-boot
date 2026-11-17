package mx.habil.pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import mx.habil.pedidos.client.ProveedorDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La aplicación completa, de punta a punta: se levanta en un puerto libre, con su base en
 * memoria, y se le habla por HTTP como le hablaría cualquiera. El único de mentiras es el
 * proveedor de inventario.
 *
 * <p>Es la prueba que ve lo que las demás no: que las piezas encajan y que las llaves de
 * {@code application.yml} de verdad hacen lo que dicen.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PedidosApplicationTest {

    private static final ProveedorDePrueba PROVEEDOR = new ProveedorDePrueba();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CAFE = """
            {"sku": "CAFE-250", "existencias": 40, "precio_centavos": 18950}
            """;

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("pedidos.inventario.url", PROVEEDOR::url);
        registro.add("pedidos.inventario.espera", () -> "10ms");
        // Tiempo límite corto. Se cambia la variable que lee application.yml, no la llave de
        // Spring: así, si alguien borra el renglón del tiempo límite, la prueba del 504 se pone roja.
        registro.add("HTTP_READ_TIMEOUT", () -> "300ms");
    }

    @BeforeEach
    void limpiar() {
        PROVEEDOR.olvidar();
    }

    @AfterAll
    static void cerrar() {
        PROVEEDOR.close();
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(20));
        if (cuerpo == null) {
            peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            peticion.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unPedidoSeCreaSeGuardaYSePuedeConsultar() throws Exception {
        PROVEEDOR.contestar(200, CAFE);

        HttpResponse<String> creado = pedir("POST", "/v1/pedidos", """
                {"correo": "ana@tienda.mx", "lineas": [{"sku": "CAFE-250", "cantidad": 2}]}
                """);

        assertThat(creado.statusCode()).isEqualTo(201);
        JsonNode ticket = JSON.readTree(creado.body());
        assertThat(ticket.get("estado").asString()).isEqualTo("RECIBIDO");
        assertThat(ticket.get("total_centavos").asLong()).isEqualTo(37900);
        assertThat(ticket.has("correo")).isFalse();
        String ubicacion = creado.headers().firstValue("Location").orElseThrow();
        assertThat(ubicacion).isEqualTo("/v1/pedidos/" + ticket.get("id").asString());
        assertThat(creado.headers().firstValue("X-Request-ID")).isPresent();

        HttpResponse<String> consultado = pedir("GET", ubicacion, null);

        assertThat(consultado.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(consultado.body())).isEqualTo(ticket);
    }

    @Test
    void siElProveedorContestaEnCamelCaseNoSaleUnPedidoDeCeroPesos() throws Exception {
        PROVEEDOR.contestar(200, """
                {"sku": "CAFE-250", "existencias": 40, "precioCentavos": 18950}
                """);

        HttpResponse<String> respuesta = pedir("POST", "/v1/pedidos", """
                {"correo": "ana@tienda.mx", "lineas": [{"sku": "CAFE-250", "cantidad": 2}]}
                """);

        // Esta aplicación lee precio_centavos; el proveedor mandó precioCentavos. El precio no
        // llega, y la lectura falla en vez de dejarlo en cero: no se crea un pedido gratis.
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(respuesta.body()).get("error_code").asString()).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
        assertThat(PROVEEDOR.rutas()).hasSize(1);
    }

    @Test
    void siElProveedorNoConoceElProductoEs422() throws Exception {
        HttpResponse<String> respuesta = pedir("POST", "/v1/pedidos", """
                {"correo": "ana@tienda.mx", "lineas": [{"sku": "NO-EXISTE", "cantidad": 1}]}
                """);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(JSON.readTree(respuesta.body()).get("error_code").asString()).isEqualTo("PRODUCTO_NO_EXISTE");
    }

    @Test
    void siElProveedorTardaMasQueElTiempoLimiteEs504TrasTresIntentos() throws Exception {
        PROVEEDOR.tardar(Duration.ofSeconds(1), 200, CAFE)
                .tardar(Duration.ofSeconds(1), 200, CAFE)
                .tardar(Duration.ofSeconds(1), 200, CAFE);

        HttpResponse<String> respuesta = pedir("POST", "/v1/pedidos", """
                {"correo": "ana@tienda.mx", "lineas": [{"sku": "CAFE-250", "cantidad": 2}]}
                """);

        assertThat(respuesta.statusCode()).isEqualTo(504);
        JsonNode problema = JSON.readTree(respuesta.body());
        assertThat(problema.get("error_code").asString()).isEqualTo("PROVEEDOR_NO_RESPONDE");
        assertThat(problema.get("type").asString()).isEqualTo("https://pedidos.example/errors/proveedor_no_responde");
        assertThat(PROVEEDOR.rutas()).hasSize(3);
    }

    @Test
    void unPedidoQueNoExisteEs404ConProblemDetails() throws Exception {
        HttpResponse<String> respuesta = pedir("GET", "/v1/pedidos/7b1e0f5e-3c7a-4a51-9d0e-2f6a1c9b8d11", null);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.headers().firstValue("Content-Type")).contains("application/problem+json");
        JsonNode problema = JSON.readTree(respuesta.body());
        assertThat(problema.get("type").asString()).isEqualTo("https://pedidos.example/errors/pedido_no_encontrado");
        assertThat(problema.get("status").asInt()).isEqualTo(404);
        assertThat(problema.get("instance").asString()).isEqualTo("/v1/pedidos/7b1e0f5e-3c7a-4a51-9d0e-2f6a1c9b8d11");
    }

    @Test
    void laSaludContestaArriba() throws Exception {
        HttpResponse<String> respuesta = pedir("GET", "/actuator/health", null);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(respuesta.body()).get("status").asString()).isEqualTo("UP");
    }

    @Test
    void loDemasDeActuatorNoSePublica() throws Exception {
        assertThat(pedir("GET", "/actuator/env", null).statusCode()).isEqualTo(404);
    }
}
