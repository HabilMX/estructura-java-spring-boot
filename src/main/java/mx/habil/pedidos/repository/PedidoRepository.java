package mx.habil.pedidos.repository;

import java.util.UUID;
import mx.habil.pedidos.model.entity.PedidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * La despensa: guarda y trae fichas de pedidos.
 *
 * <p>Es una interfaz sin código: Spring Data escribe la implementación al arrancar. De
 * {@code JpaRepository} hereda {@code save}, {@code findById} y los demás. Aquí no se decide
 * nada: si un pedido se puede guardar o no, lo dice el cocinero ({@code service}).
 */
public interface PedidoRepository extends JpaRepository<PedidoEntity, UUID> {
}
