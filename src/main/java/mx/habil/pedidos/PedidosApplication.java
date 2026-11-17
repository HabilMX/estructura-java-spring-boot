package mx.habil.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * La puerta del local: aquí arranca todo.
 *
 * <p>Vive en el paquete raíz, {@code mx.habil.pedidos}, encima de todos los demás. Spring busca
 * controladores, servicios, repositorios y entidades de este paquete hacia abajo; por eso ninguna
 * clase de la aplicación vive fuera de él.
 */
@SpringBootApplication
public class PedidosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosApplication.class, args);
    }
}
