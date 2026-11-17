package mx.habil.pedidos.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * La instalación del local: las piezas que alguien tiene que armar una vez, al abrir.
 *
 * <p>Aquí no hay reglas del negocio ni rutas HTTP. Solo se construyen objetos que otros paquetes
 * reciben ya listos por su constructor.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventarioProperties.class)
public class InstalacionConfig {

    /** El reloj. Las pruebas ponen uno detenido para que la hora no cambie entre corridas. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * El teléfono para llamar al proveedor. El {@code RestClient.Builder} que entrega Spring Boot
     * ya trae los tiempos límite de {@code spring.http.clients} en {@code application.yml}.
     */
    @Bean
    RestClient inventarioRestClient(RestClient.Builder builder, InventarioProperties inventario) {
        return builder.baseUrl(inventario.url().toString()).build();
    }

    /**
     * Cuántas veces se vuelve a llamar. Solo se reintenta cuando no hubo respuesta
     * ({@code ResourceAccessException}); un «no existe» del proveedor es una respuesta, y no se
     * repite. La espera se duplica en cada intento y lleva algo de azar, para que varios
     * clientes no vuelvan a llamar todos al mismo tiempo.
     */
    @Bean
    RetryTemplate inventarioReintento(InventarioProperties inventario) {
        RetryPolicy politica = RetryPolicy.builder()
                .includes(ResourceAccessException.class)
                .maxRetries(inventario.reintentos())
                .delay(inventario.espera())
                .jitter(inventario.espera().dividedBy(4))
                .multiplier(2)
                .build();
        return new RetryTemplate(politica);
    }
}
