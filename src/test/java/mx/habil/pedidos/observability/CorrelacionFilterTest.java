package mx.habil.pedidos.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelacionFilterTest {

    private final CorrelacionFilter filtro = new CorrelacionFilter();

    /** Corre el filtro y regresa el request_id que había en la bitácora mientras se atendía. */
    private String atender(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> durante = new AtomicReference<>();
        filtro.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                durante.set(MDC.get(CorrelacionFilter.CLAVE));
            }
        });
        return durante.get();
    }

    @Test
    void respetaElIdentificadorQueTraeLaPeticion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "visita-0042");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(atender(request, response)).isEqualTo("visita-0042");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("visita-0042");
    }

    @Test
    void siNoTraeUnoLoInventa() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String durante = atender(new MockHttpServletRequest(), response);

        assertThat(durante).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(durante);
    }

    @Test
    void unIdentificadorConCaracteresRarosNoLlegaALaBitacora() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "abc def; rm -rf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String durante = atender(request, response);

        assertThat(durante).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(durante);
    }

    @Test
    void alTerminarLaBitacoraQuedaLimpia() throws Exception {
        atender(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelacionFilter.CLAVE)).isNull();
    }
}
