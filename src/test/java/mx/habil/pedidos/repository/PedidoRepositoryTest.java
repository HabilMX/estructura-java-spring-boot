package mx.habil.pedidos.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import mx.habil.pedidos.model.entity.LineaEntity;
import mx.habil.pedidos.model.entity.PedidoEntity;
import mx.habil.pedidos.model.vo.EstadoPedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * La despensa se prueba contra una base de verdad: H2, en memoria. Solo se levanta la parte de
 * Spring que habla con la base; ni controladores ni servicios.
 */
@DataJpaTest
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository repository;

    @Autowired
    private EntityManager base;

    @Test
    void loQueSeGuardaRegresaIgualConSusLineasEnOrden() {
        UUID id = UUID.randomUUID();
        Instant creado = Instant.parse("2026-11-17T18:00:00Z");
        repository.save(new PedidoEntity(id, "ana@tienda.mx", 42400, EstadoPedido.RECIBIDO, creado,
                List.of(new LineaEntity("CAFE-250", 2, 18950), new LineaEntity("TAZA-01", 1, 4500))));
        // Se manda a la base y se olvida lo que hay en memoria: lo que sigue se lee de las tablas.
        base.flush();
        base.clear();

        PedidoEntity leido = repository.findById(id).orElseThrow();

        assertThat(leido.getCorreo()).isEqualTo("ana@tienda.mx");
        assertThat(leido.getTotalCentavos()).isEqualTo(42400);
        assertThat(leido.getEstado()).isEqualTo(EstadoPedido.RECIBIDO);
        assertThat(leido.getCreadoEn()).isEqualTo(creado);
        assertThat(leido.getLineas()).containsExactly(
                new LineaEntity("CAFE-250", 2, 18950), new LineaEntity("TAZA-01", 1, 4500));
    }

    @Test
    void unIdentificadorQueNoEstaRegresaVacio() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }
}
