package mx.habil.pedidos.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.habil.pedidos.facade.PedidoFacade;
import mx.habil.pedidos.mapper.PedidoMapper;
import mx.habil.pedidos.model.request.CrearPedidoRequest;
import mx.habil.pedidos.model.response.PedidoResponse;
import mx.habil.pedidos.service.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El mesero: recibe la comanda, revisa que venga bien llenada y la pasa a la cocina.
 *
 * <p>Aquí está todo lo que es HTTP: rutas, verbos y códigos de respuesta. No hay ni un
 * {@code if} sobre un dato del pedido. La versión de la API ({@code /v1}) va una sola vez, en la
 * clase.
 */
@RestController
@RequestMapping("/v1/pedidos")
public class PedidoController {

    private final PedidoFacade facade;
    private final PedidoService service;
    private final PedidoMapper mapper;

    public PedidoController(PedidoFacade facade, PedidoService service, PedidoMapper mapper) {
        this.facade = facade;
        this.service = service;
        this.mapper = mapper;
    }

    /** Crear un pedido interviene a dos (el proveedor y el cocinero): pasa por el jefe de cocina. */
    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest comanda) {
        PedidoResponse creado = facade.crear(comanda);
        return ResponseEntity.created(URI.create("/v1/pedidos/" + creado.id())).body(creado);
    }

    /** Consultar un pedido lo atiende un solo servicio: el atajo, directo al cocinero. */
    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable UUID id) {
        return mapper.aResponse(service.buscar(id));
    }
}
