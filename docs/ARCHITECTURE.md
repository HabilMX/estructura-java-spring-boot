# Arquitectura

Una API por capas, simple y explícita. No es hexagonal ni pretende serlo: cada paquete tiene un
papel, y las dependencias van en un solo sentido.

## Tipo de componente

API REST con Spring Boot (Spring MVC), empaquetada como jar ejecutable. Java 25, Maven.

## Las capas y hacia dónde apuntan las flechas

```
api ──► facade ──► service ──► repository
 │         │          │
 │         └──► client ◄──┘
 └──────────────► service        (el atajo: cuando no hay nada que coordinar)
```

| Paquete | Puede llamar a | Lo pueden llamar |
|---|---|---|
| `api` | `facade`, `service` | nadie |
| `facade` | `service`, `client` | `api` |
| `service` | `repository`, `client` | `api`, `facade` |
| `repository` | — | `service` |
| `client` | — | `facade`, `service` |

Los papeles que pasan de mano en mano —`request`, `response`, `entity` y `vo`— viven juntos en `model/`.
Al lado de las capas, y usables desde cualquiera: `vo`, `utils`, `exception` y `mapper`. `request`
y `response` solo los conocen `api`, `facade` y `mapper`. `entity` la conocen `service`,
`repository`, `facade` y `mapper`; por la puerta de `api` nunca entra ni sale.

Estas reglas no son un acuerdo de palabra: las comprueba
`src/test/java/mx/habil/pedidos/arquitectura/CapasTest.java` en cada `./mvnw verify`.

## Cuándo hay facade

Cuando una operación coordina a **dos o más** servicios o clientes. Crear un pedido consulta al
proveedor de inventario (`client`) y después aplica las reglas y guarda (`service`): pasa por
`PedidoFacade`. Consultar un pedido lo atiende un solo servicio: `PedidoController` llama directo a
`PedidoService`.

Una facade cuyo método solo llama a otro método no coordina nada. Se borra, y `api` llama al
servicio.

## Decisiones

| Decisión | Por qué |
|---|---|
| Paquetes por capa, no por funcionalidad | Con una sola funcionalidad (pedidos), la capa es lo que enseña la plantilla. Spring Boot no exige ninguna de las dos formas |
| H2 en memoria | Corre a la primera, sin instalar una base ni levantar un contenedor. Los datos se pierden al apagar |
| Reintento con `RetryTemplate` de Spring Framework | Viene en el núcleo desde la versión 7: cero dependencias nuevas. Se reintenta solo cuando no hubo respuesta |
| Errores con `ProblemDetail`, armados en `CodigoError` | Un solo lugar define la forma del error (RFC 9457); ninguna clase construye un cuerpo de error por su cuenta |
| Sin autenticación | Fuera del ejemplo. Una API real no se publica así: mira «Cómo crece» en el README |
| Actuator en el mismo puerto, solo `health` | Lo mínimo para saber si está viva. En un servicio real, la administración va en otro puerto |
