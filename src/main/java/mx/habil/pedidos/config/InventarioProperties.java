package mx.habil.pedidos.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lo que {@code application.yml} dice del proveedor de inventario, bajo {@code pedidos.inventario}.
 *
 * @param url        dónde vive el proveedor
 * @param reintentos cuántas veces más se intenta si no contesta
 * @param espera     cuánto se espera antes del primer reintento; después, el doble cada vez
 */
@ConfigurationProperties("pedidos.inventario")
public record InventarioProperties(URI url, int reintentos, Duration espera) {

    public InventarioProperties {
        if (url == null) {
            throw new IllegalArgumentException("falta pedidos.inventario.url");
        }
        if (reintentos < 0 || reintentos > 5) {
            throw new IllegalArgumentException("pedidos.inventario.reintentos va de 0 a 5");
        }
        if (espera == null || espera.isNegative()) {
            throw new IllegalArgumentException("pedidos.inventario.espera no puede ser negativa");
        }
    }
}
