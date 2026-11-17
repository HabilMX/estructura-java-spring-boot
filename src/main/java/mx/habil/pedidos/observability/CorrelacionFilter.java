package mx.habil.pedidos.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * La bitácora del local: le pone un número a cada visita.
 *
 * <p>Cada petición recibe un identificador ({@code request_id}). Sale en todas las líneas de la
 * bitácora que esa petición produce y regresa al cliente en el encabezado {@code X-Request-ID}.
 * Con ese número, una queja se encuentra en la bitácora sin buscar por nombre ni por correo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFilter extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-Request-ID";
    public static final String CLAVE = "request_id";

    // Lo que llega de fuera no se copia a la bitácora sin revisar: solo letras, números y guiones.
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String recibido = request.getHeader(ENCABEZADO);
        String id = recibido != null && VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        MDC.put(CLAVE, id);
        response.setHeader(ENCABEZADO, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // El hilo se reutiliza: si no se limpia, la siguiente visita hereda este número.
            MDC.remove(CLAVE);
        }
    }
}
