package mx.habil.pedidos.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import mx.habil.pedidos.model.entity.LineaEntity;
import mx.habil.pedidos.model.entity.PedidoEntity;
import mx.habil.pedidos.exception.CodigoError;
import mx.habil.pedidos.exception.PedidosException;
import mx.habil.pedidos.facade.PedidoFacade;
import mx.habil.pedidos.mapper.PedidoMapper;
import mx.habil.pedidos.model.request.CrearPedidoRequest;
import mx.habil.pedidos.model.response.PedidoResponse;
import mx.habil.pedidos.service.PedidoService;
import mx.habil.pedidos.model.vo.EstadoPedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Del mesero se prueba lo que es suyo: rutas, códigos, la forma del JSON y que una comanda mal
 * llenada no entre a la cocina. La cocina es de mentiras ({@code @MockitoBean}).
 */
@WebMvcTest(PedidoController.class)
@Import(PedidoMapper.class)
class PedidoControllerTest {

    private static final UUID ID = UUID.fromString("7b1e0f5e-3c7a-4a51-9d0e-2f6a1c9b8d11");
    private static final Instant CREADO = Instant.parse("2026-11-17T18:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoFacade facade;

    @MockitoBean
    private PedidoService service;

    @Test
    void crearContesta201ConElTicketEnSnakeCase() throws Exception {
        when(facade.crear(new CrearPedidoRequest("ana@tienda.mx", List.of(new CrearPedidoRequest.Linea("CAFE-250", 2)))))
                .thenReturn(new PedidoResponse(ID, "RECIBIDO", 37900, CREADO,
                        List.of(new PedidoResponse.Linea("CAFE-250", 2, 18950))));

        mvc.perform(post("/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "ana@tienda.mx", "lineas": [{"sku": "CAFE-250", "cantidad": 2}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/pedidos/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.total_centavos").value(37900))
                .andExpect(jsonPath("$.creado_en").value("2026-11-17T18:00:00Z"))
                .andExpect(jsonPath("$.lineas[0].precio_centavos").value(18950))
                .andExpect(jsonPath("$.totalCentavos").doesNotExist());
    }

    @Test
    void unaComandaMalLlenadaNoEntraALaCocina() throws Exception {
        mvc.perform(post("/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "no-es-correo", "lineas": [{"sku": "cafe", "cantidad": 0}]}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pedidos.example/errors/solicitud_invalida"))
                .andExpect(jsonPath("$.title").value("La solicitud no es válida"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("La solicitud tiene 3 campo(s) inválido(s)"))
                .andExpect(jsonPath("$.instance").value("/v1/pedidos"))
                .andExpect(jsonPath("$.error_code").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.violations", hasSize(3)))
                .andExpect(jsonPath("$.violations[0].campo").value("correo"))
                .andExpect(jsonPath("$.violations[1].campo").value("lineas[0].cantidad"))
                .andExpect(jsonPath("$.violations[2].campo").value("lineas[0].sku"));
        verifyNoInteractions(facade, service);
    }

    @Test
    void unaComandaSinLineasTampocoEntra() throws Exception {
        mvc.perform(post("/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "ana@tienda.mx", "lineas": []}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations[0].campo").value("lineas"));
        verifyNoInteractions(facade, service);
    }

    @Test
    void unaQuejaDeLaCocinaSaleComoProblemDetails() throws Exception {
        when(facade.crear(any())).thenThrow(
                new PedidosException(CodigoError.SIN_EXISTENCIAS, "De TAZA-01 se pidieron 4 y hay 3"));

        mvc.perform(post("/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "ana@tienda.mx", "lineas": [{"sku": "TAZA-01", "cantidad": 4}]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pedidos.example/errors/sin_existencias"))
                .andExpect(jsonPath("$.detail").value("De TAZA-01 se pidieron 4 y hay 3"))
                .andExpect(jsonPath("$.error_code").value("SIN_EXISTENCIAS"));
    }

    @Test
    void buscarVaDirectoAlServicioYEntregaElTicketNoLaFicha() throws Exception {
        when(service.buscar(ID)).thenReturn(new PedidoEntity(ID, "ana@tienda.mx", 4500, EstadoPedido.RECIBIDO,
                CREADO, List.of(new LineaEntity("TAZA-01", 1, 4500))));

        mvc.perform(get("/v1/pedidos/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.total_centavos").value(4500))
                .andExpect(jsonPath("$.correo").doesNotExist());
        verifyNoInteractions(facade);
    }

    @Test
    void unPedidoQueNoExisteEs404() throws Exception {
        when(service.buscar(ID)).thenThrow(new PedidosException(CodigoError.PEDIDO_NO_ENCONTRADO,
                "No hay un pedido con el identificador " + ID));

        mvc.perform(get("/v1/pedidos/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("PEDIDO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value("/v1/pedidos/" + ID));
    }

    @Test
    void unIdentificadorQueNoEsUuidEs400() throws Exception {
        mvc.perform(get("/v1/pedidos/{id}", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    @Test
    void unJsonMalEscritoEs400() throws Exception {
        mvc.perform(post("/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(facade);
    }

    @Test
    void loInesperadoEs500SinContarQuePasoPorDentro() throws Exception {
        when(service.buscar(ID)).thenThrow(new IllegalStateException("la base explotó: jdbc:h2:mem:pedidos"));

        mvc.perform(get("/v1/pedidos/{id}", ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("ERROR_INTERNO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
    }
}
