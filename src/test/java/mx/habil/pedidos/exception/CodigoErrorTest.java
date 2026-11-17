package mx.habil.pedidos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class CodigoErrorTest {

    @Test
    void armaElCuerpoConLaFormaDeProblemDetails() {
        ProblemDetail problema = CodigoError.PEDIDO_NO_ENCONTRADO.toProblemDetail("No hay un pedido con ese identificador");

        assertThat(problema.getType()).isEqualTo(URI.create("https://pedidos.example/errors/pedido_no_encontrado"));
        assertThat(problema.getTitle()).isEqualTo("El pedido no existe");
        assertThat(problema.getStatus()).isEqualTo(404);
        assertThat(problema.getDetail()).isEqualTo("No hay un pedido con ese identificador");
        assertThat(problema.getProperties()).containsEntry("error_code", "PEDIDO_NO_ENCONTRADO");
    }

    @ParameterizedTest
    @EnumSource(CodigoError.class)
    void cadaCodigoTieneSuTipoYSuCodigoPlano(CodigoError codigo) {
        ProblemDetail problema = codigo.toProblemDetail("detalle");

        assertThat(problema.getType().toString()).startsWith(CodigoError.BASE).isLowerCase();
        assertThat(problema.getProperties()).containsEntry("error_code", codigo.name());
        assertThat(problema.getStatus()).isEqualTo(codigo.estado().value());
    }

    @Test
    void laExcepcionLlevaElEstadoYElCuerpoDeSuCodigo() {
        PedidosException queja = new PedidosException(CodigoError.SIN_EXISTENCIAS, "De CAFE-250 se pidieron 5 y hay 2");

        assertThat(queja.codigo()).isEqualTo(CodigoError.SIN_EXISTENCIAS);
        assertThat(queja.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(queja.getBody().getDetail()).isEqualTo("De CAFE-250 se pidieron 5 y hay 2");
        assertThat(queja.getBody().getProperties()).containsEntry("error_code", "SIN_EXISTENCIAS");
    }
}
