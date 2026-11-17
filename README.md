> El código es MIT; el texto y las explicaciones son CC BY-SA 4.0.

# estructura-java-spring-boot

**Una plantilla de una API con Spring Boot que se clona, corre a la primera y te dice qué va en
cada paquete**: un paquete por papel, las dependencias en un solo sentido y una prueba que se pone
roja cuando alguien las voltea.

Es una **semilla**, no un molde completo: trae una funcionalidad real de punta a punta (recibir y
consultar los pedidos de una tienda), sus pruebas y un CI que comprueba una frontera que el
compilador de Java no vigila: quién puede conocer a quién. Lo demás crece cuando duela, y la
sección [«Cómo crece»](#cómo-crece-cuándo-agregar-cada-cosa) dice cuándo.

Es parte de la serie «La estructura de un proyecto», de Hábil. Si Java o Spring Boot son nuevos
para ti, empieza por el [curso de Java](https://www.habil.mx/es/cursos/java/).

---

## Córrelo

Necesitas Java 25 (`java -version`), Git, `bash` y `curl`. Maven no hace falta: lo trae el
proyecto (`./mvnw`).

- **Linux Mint 22, Ubuntu o macOS**: instala Java con [SDKMAN](https://sdkman.io/), que no toca el
  Java del sistema.

  ```bash
  sudo apt install curl zip unzip        # solo en Linux
  curl -s "https://get.sdkman.io" | bash
  ```

  Abre otra terminal, entra a la carpeta de la plantilla y pide el Java que declara `.sdkmanrc`:

  ```bash
  sdk env install
  ```

  Abre una terminal más (en la que instaló, `java` todavía no se encuentra; medido), vuelve a la
  carpeta y actívalo:

  ```bash
  sdk env
  java -version
  ```

  Debe contestar `openjdk version "25.0.5"` y `Temurin-25.0.5+8`. Cualquier Java 25 sirve. Con
  otra línea, la construcción se detiene y te lo dice (medido con Java 21).
- **Windows**: usa WSL; el guion de la frontera pide `bash`.

**1. Construye y corre las pruebas**:

```bash
./mvnw verify
```

La primera vez tarda: baja Maven y las bibliotecas. Compila, corre las pruebas, mide la cobertura
y exige su piso. Las líneas que importan, cerca del final:

```
[INFO] Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Son 72 pruebas. Cuéntalas en esa línea o en los informes de `target/surefire-reports/`, no en el
`BUILD SUCCESS`: una clase de pruebas que no arranca deja sus pruebas como saltadas (`Skipped`), y
el total no baja.

**2. Comprueba la frontera**:

```bash
bash herramientas/probar-frontera.sh
```

Termina en `✅ frontera: se sostiene` (más abajo se explica qué hace).

**3. Úsalo.** Necesitas dos terminales. En la primera, el proveedor de inventario de ejemplo: una
carpeta con dos archivos JSON, servida con `jwebserver`, que ya trae el JDK:

```bash
jwebserver -b 127.0.0.1 -p 9090 -d "$PWD/ejemplo/inventario"
```

En la segunda, la API:

```bash
java -jar target/pedidos.jar
```

Cuando veas `Started PedidosApplication`, pide un pedido desde una tercera terminal:

```bash
curl -s -i -X POST localhost:8080/v1/pedidos \
  -H 'Content-Type: application/json' \
  -d '{"correo": "ana@tienda.mx", "lineas": [{"sku": "CAFE-250", "cantidad": 2}, {"sku": "TAZA-01", "cantidad": 1}]}'
```

Lo que contestó el 17-nov-2026 (el `id`, la hora y el `X-Request-ID` cambian en cada corrida; el
JSON sale en una sola línea, aquí va partido para leerlo):

```
HTTP/1.1 201
X-Request-ID: cab9381c-2558-4f18-addc-362868041e4f
Location: /v1/pedidos/f6bd398a-ec01-409f-ba8d-b1d7961571ed
Content-Type: application/json

{"id":"f6bd398a-ec01-409f-ba8d-b1d7961571ed","estado":"RECIBIDO","total_centavos":42400,
 "creado_en":"2026-11-17T21:32:06.144Z",
 "lineas":[{"sku":"CAFE-250","cantidad":2,"precio_centavos":18950},
           {"sku":"TAZA-01","cantidad":1,"precio_centavos":4500}]}
```

El precio no venía en lo que mandaste: lo puso el proveedor. Y en la bitácora de la API, el correo
sale tapado:

```
INFO [cab9381c-2558-4f18-addc-362868041e4f] … PedidoService : pedido f6bd398a-… recibido de a***@tienda.mx por $424.00
```

Ahora los errores. Todos salen con la misma forma, Problem Details (`application/problem+json`):

```bash
# Un pedido que no existe
curl -s localhost:8080/v1/pedidos/00000000-0000-0000-0000-000000000000

# Más piezas de las que hay (de TAZA-01 hay 3)
curl -s -X POST localhost:8080/v1/pedidos -H 'Content-Type: application/json' \
  -d '{"correo": "ana@tienda.mx", "lineas": [{"sku": "TAZA-01", "cantidad": 4}]}'
```

```json
{"detail":"No hay un pedido con el identificador 00000000-0000-0000-0000-000000000000",
 "instance":"/v1/pedidos/00000000-0000-0000-0000-000000000000","status":404,
 "title":"El pedido no existe","type":"https://pedidos.example/errors/pedido_no_encontrado",
 "error_code":"PEDIDO_NO_ENCONTRADO"}
```

```json
{"detail":"De TAZA-01 se pidieron 4 y hay 3","instance":"/v1/pedidos","status":409,
 "title":"No hay existencias suficientes","type":"https://pedidos.example/errors/sin_existencias",
 "error_code":"SIN_EXISTENCIAS"}
```

Si mandas una comanda mal llenada (un correo que no es correo, cero piezas), la respuesta es un
`422` con la lista `violations`: qué campo falló y por qué. Esos mensajes salen en el idioma de tu
sistema: «debe ser mayor que 0» en una máquina en español, «must be greater than 0» en una en
inglés (medido en las dos).

Apaga el proveedor (Ctrl+C en la primera terminal) y repite el primer `curl`: contesta `503`, con
`"error_code":"PROVEEDOR_NO_DISPONIBLE"`, después de intentarlo tres veces. La salud de la API
está en `curl -s localhost:8080/actuator/health`.

La base de datos es H2, en memoria: nace vacía al arrancar y se borra al apagar. Es lo más simple
que corre a la primera; cuándo cambiarla está en «Cómo crece».

---

## Qué es cada carpeta y por qué existe

```
estructura-java-spring-boot/
├── pom.xml                          qué bibliotecas usa y cómo se construye
├── mvnw · mvnw.cmd · .mvn/          el Maven del proyecto: nadie instala Maven
├── .sdkmanrc                        el Java con que se probó
├── Dockerfile                       la imagen: construye, desempaca y corre sin root
├── docs/ARCHITECTURE.md             las capas, las flechas y las decisiones
├── ejemplo/inventario/              el proveedor de ejemplo que usa «Córrelo»
├── herramientas/probar-frontera.sh  demuestra que la prueba de arquitectura sabe fallar
├── src/main/java/mx/habil/pedidos/
│   ├── PedidosApplication.java      la puerta del local: aquí arranca todo
│   ├── api/                         el mesero
│   ├── facade/                      el jefe de cocina
│   ├── service/                     el cocinero
│   ├── repository/                  la despensa
│   ├── client/                      el proveedor de afuera
│   ├── model/                       los papeles que pasan de mano en mano
│   │   ├── request/                 la comanda
│   │   ├── response/                el ticket
│   │   ├── entity/                  la ficha del archivo
│   │   └── vo/                      las medidas exactas
│   ├── mapper/                      el que pasa en limpio
│   ├── config/                      la instalación del local
│   ├── exception/                   el libro de quejas
│   ├── observability/               las cámaras y la bitácora
│   └── utils/                       el cajón de herramientas
├── src/main/resources/
│   └── application.yml              toda la configuración, en un archivo
└── src/test/java/mx/habil/pedidos/  el mismo árbol: cada prueba vive junto a lo que prueba
    └── arquitectura/CapasTest.java  la que vigila las flechas
```

Piensa en un restaurante. Cada paquete es un papel, y cada papel tiene algo que **nunca** hace.

| Paquete | En el restaurante | En la plantilla | Lo que NUNCA va ahí |
|---|---|---|---|
| `api` | El mesero: recibe la comanda, revisa que venga bien llenada y la pasa | `PedidoController`: rutas, verbos, `@Valid`, el `201` con su `Location` | Una regla del negocio. Un `if` sobre un dato del pedido |
| `facade` | El jefe de cocina: coordina, solo si intervienen dos o más | `PedidoFacade.crear`: pregunta al proveedor, pasa al cocinero, entrega el ticket | Cálculos, reglas o consultas a la base. Un método que solo llama a otro |
| `service` | El cocinero: las reglas | `PedidoService`: «no se acepta si falta una pieza» y «el total es precio por cantidad» | HTTP y JSON: ni `request`, ni `response`, ni códigos de estado |
| `repository` | La despensa: guarda y trae | `PedidoRepository`: una interfaz; Spring Data escribe el código | Decisiones. Si un pedido se puede guardar lo dice el cocinero |
| `client` | El proveedor de afuera, con tiempo límite y reintento | `InventarioClient`: pregunta existencias y precio por HTTP | Reglas del pedido. Y una llamada sin tiempo límite |
| `model/request` | La comanda: lo que el cliente pide | `CrearPedidoRequest`: un `record` con `@NotBlank`, `@Email`, `@Positive` | Lógica. Y campos que el cliente no debe decidir, como el precio |
| `model/response` | El ticket: lo que el cliente recibe | `PedidoResponse`: un `record` con lo que el cliente necesita ver | Datos que nadie pidió, como el correo. Anotaciones de la base |
| `model/entity` | La ficha del archivo: lo que se guarda | `PedidoEntity` y `LineaEntity`: la forma de las tablas | Salir por la puerta: el mesero entrega el ticket, nunca la ficha |
| `model/vo` | La medida exacta | `Dinero`, `Correo`, `LineaCotizada`: si existen, ya son válidos | Dependencias de Spring, de la base o de otra capa |
| `mapper` | El que pasa en limpio | `PedidoMapper`: copia de la ficha al ticket | Un `if` que decida algo del negocio |
| `config` | La instalación del local | `InstalacionConfig`: el reloj, el cliente HTTP, la política de reintento | Reglas del negocio y rutas HTTP |
| `exception` | El libro de quejas | `CodigoError`, `PedidosException`, `ManejadorErrores`: Problem Details | Un formato de error propio. Un error armado fuera del catálogo |
| `observability` | Las cámaras y la bitácora | `CorrelacionFilter`: un número por visita, en cada línea de la bitácora | Datos personales en claro |
| `utils` | El cajón de herramientas | `Mascara.correo`: `a***@tienda.mx` | Algo que sepa qué es un pedido. Si lo sabe, tiene otro paquete |

`vo` quiere decir *value object*, objeto de valor: un dato pequeño que se define por lo que vale,
no por un identificador. Dos `Dinero` de 100 centavos son el mismo dinero.

### El camino de un pedido

```
POST /v1/pedidos                              GET /v1/pedidos/{id}
       │                                             │
       ▼                                             ▼
  api (mesero)   revisa la comanda              api (mesero)
       │                                             │
       ▼                                             │  el atajo: lo atiende un
  facade (jefe de cocina)                            │  solo servicio, no hay
       │                                             │  nada que coordinar
       ├──► client (proveedor)  ¿hay? ¿a cuánto?     │
       │                                             │
       ▼                                             ▼
  service (cocinero)   las reglas  ◄─────────────────┘
       │
       ▼
  repository (despensa)   guarda la ficha
```

De regreso, la ficha (`entity`) se pasa en limpio (`mapper`) y sale el ticket (`response`).

### Las tres reglas

1. **Cada quien en lo suyo.** Si no sabes dónde va una línea de código, pregunta de quién es el
   trabajo: ¿del mesero, del cocinero, de la despensa?
2. **Las flechas solo van hacia adentro.** `api → facade → service → repository` o `client`. El
   cocinero nunca llama al mesero.
3. **Lo que entra, lo que se guarda y lo que sale son cosas distintas.** `request`, `entity` y
   `response`: tres clases, aunque hoy se parezcan. Mañana la tabla gana una columna y el JSON no
   tiene por qué enterarse.

### ¿Cuándo sí y cuándo no la facade?

**Sí**, cuando una operación coordina a dos o más servicios o clientes. Crear un pedido necesita al
proveedor de inventario y al cocinero, en ese orden: `PedidoFacade` dice el orden, y nada más.

**No**, cuando la atiende un solo servicio. Consultar un pedido es una sola llamada:
`PedidoController` va directo a `PedidoService`. Una facade cuyo método solo llama a otro método no
coordina nada: es un archivo más que tocar en cada cambio.

Ninguna prueba te va a avisar de una facade que sobra: las flechas quedan bien. Eso se decide
leyendo, con la regla de dos.

### Lo demás

**`pom.xml`.** Hereda de `spring-boot-starter-parent`, que trae las versiones de las bibliotecas ya
probadas entre sí; por eso casi ninguna dependencia declara `<version>`. Tres cuidados: el Enforcer
detiene la construcción si el Java no es 25; Surefire falla si una corrida termina con cero
pruebas; y JaCoCo exige que las pruebas cubran al menos el 84 % de las instrucciones.

**`mvnw`.** El Maven Wrapper: un guion que baja la versión de Maven que declara
`.mvn/wrapper/maven-wrapper.properties` y la usa. Se versiona, para que todos construyan con el
mismo Maven.

**`application.yml`.** Toda la configuración en un archivo. Lo que cambia entre ambientes se lee
de una variable con su valor para desarrollo: `${INVENTARIO_URL:http://localhost:9090}`. Tres
renglones que conviene conocer: `spring.jackson.property-naming-strategy: SNAKE_CASE` (el JSON
entra y sale como `total_centavos`), `spring.http.clients.read-timeout` (el tiempo límite de toda
llamada hacia afuera) y `management.endpoints.web.exposure.include: health` (de Actuator solo se
publica la salud).

**El JSON, con Jackson 3.** Spring Boot 4 usa Jackson 3. Sus clases viven en `tools.jackson.*`
(mira los `import` de `PedidosApplicationTest`); las anotaciones siguen en
`com.fasterxml.jackson.annotation` (mira `InventarioClient`). Si copias un `import` de un tutorial
viejo, `com.fasterxml.jackson.databind`, no compila.

**`src/test/`.** El mismo árbol que `src/main/`. Cada capa se prueba con lo que necesita, y no más:

| Prueba | Qué levanta |
|---|---|
| `vo`, `utils`, `mapper`, `exception`, `service`, `facade`, `observability` | Nada: Java y, si hace falta, Mockito |
| `client/InventarioClientTest` | Un servidor HTTP de verdad, el del JDK, en `127.0.0.1` |
| `repository/PedidoRepositoryTest` | Solo la parte de Spring que habla con la base (`@DataJpaTest`), sobre H2 |
| `api/PedidoControllerTest` | Solo la parte web de Spring (`@WebMvcTest`); la cocina es de mentiras |
| `PedidosApplicationTest` | Todo, en un puerto libre; el único de mentiras es el proveedor |
| `arquitectura/CapasTest` | Nada: lee las clases compiladas |

Ninguna prueba levanta un contenedor ni sale a internet.

---

## La frontera que sabe fallar

Para Java, todas las clases de este proyecto pueden importarse entre sí. Si el cocinero importa al
mesero, compila. La regla «las flechas solo van hacia adentro» la vigila una prueba,
`CapasTest`, escrita con [ArchUnit](https://www.archunit.org/): lee las clases ya compiladas y
falla cuando una flecha apunta hacia afuera.

Pero una prueba que siempre pasa no demuestra nada. `herramientas/probar-frontera.sh` comprueba que
`CapasTest` sabe fallar. Trabaja en una copia temporal del proyecto (tu carpeta no se toca) y corre
la misma prueba dos veces:

1. **Control**, con el proyecto tal como está. `CapasTest` tiene que correr y pasar. Si no, el
   experimento está mal montado, o tu proyecto ya trae una flecha hacia afuera, y el guion para en
   rojo.
2. **Violación**: se siembra en `PedidoService` un método que recibe un `PedidoController`. Tiene
   que **compilar**, y `CapasTest` tiene que **fallar**, en una sola regla y por la razón exacta.

Ésta es la salida real:

```
1/2 control: el proyecto tal como está; el cocinero (service) no conoce al mesero (api)
   ✅ compiló y CapasTest pasó: 4 reglas, 0 fallas
2/2 violación: se siembra en PedidoService un método que recibe un PedidoController
   ✅ compiló sin una queja: para Java, que el cocinero conozca al mesero no es un error
     Method <mx.habil.pedidos.service.PedidoService.avisar(mx.habil.pedidos.api.PedidoController)> has parameter of type <mx.habil.pedidos.api.PedidoController>
   ✅ CapasTest falló en una sola regla, lasFlechasSoloVanHaciaAdentro: las flechas solo van hacia adentro
✅ frontera: se sostiene
```

Si algún día alguien afloja la regla, sale `❌ PASÓ: CapasTest no vio que el cocinero conoce al
mesero. La frontera no existe.` Si la violación no compila, o la prueba falla por otra razón,
también sale en rojo. Y el resultado se lee del informe de Surefire (cuántas reglas corrieron,
cuántas fallaron y con qué mensaje), no del código de salida de Maven.

**Rómpelo tú.** Siembra la flecha en tu copia del proyecto:

```bash
sed -i.bak '$d' src/main/java/mx/habil/pedidos/service/PedidoService.java
printf '    public void avisar(mx.habil.pedidos.api.PedidoController mesero) {\n    }\n}\n' \
  >> src/main/java/mx/habil/pedidos/service/PedidoService.java
./mvnw -q test-compile
./mvnw -q test -Dtest=CapasTest
```

La primera línea quita la última llave de la clase (y deja una copia `.bak`); la segunda agrega el
método y vuelve a cerrar la clase. `test-compile` termina sin decir nada: compiló. La prueba, no.
Entre muchas líneas, la que importa:

```
Method <mx.habil.pedidos.service.PedidoService.avisar(mx.habil.pedidos.api.PedidoController)> has parameter of type <mx.habil.pedidos.api.PedidoController> in (PedidoService.java:0)
```

**Regresa solo lo que tocaste.** El experimento cambia un archivo. Primero mira qué cambió en él:

```bash
git diff -- src/main/java/mx/habil/pedidos/service/PedidoService.java
```

Si solo ves el método que sembraste, regresa ese archivo, y ninguno más:

```bash
git restore src/main/java/mx/habil/pedidos/service/PedidoService.java
bash herramientas/probar-frontera.sh
git status --short
```

La frontera vuelve a `✅ frontera: se sostiene`, y `git status` enseña lo único que queda: la copia
`PedidoService.java.bak` que dejó `sed`. Consérvala hasta que estés seguro de que no la necesitas
y bórrala con `rm -i src/main/java/mx/habil/pedidos/service/PedidoService.java.bak`, que pregunta
antes. No uses un comando que limpie toda la carpeta: se llevaría también tus notas. Lo más cómodo
es practicar en un clon que solo uses para esto.

---

## Qué NO trae y por qué

| No trae | Por qué |
|---|---|
| Autenticación y permisos | Fuera del ejemplo. Una API real no se publica sin ellos: ver «Cómo crece» |
| Una base de datos de verdad, migraciones | H2 en memoria corre a la primera. Los datos se pierden al apagar |
| Contenedores para las pruebas | Las pruebas corren con Java y nada más: el proveedor de mentiras es el servidor HTTP del JDK |
| Interfaz e implementación por cada servicio (`service/impl`) | Con una sola implementación, la interfaz es un archivo más. Ver «Cómo crece» |
| Lombok | Los `record` ya escriben constructores y accesores, y la inyección va por constructor |
| Un generador de mapeo | Un mapper de diez líneas se lee completo. Ver «Cómo crece» |
| Interruptor de circuito (*circuit breaker*) | El tiempo límite y el reintento alcanzan para un proveedor. Ver «Cómo crece» |
| Contrato OpenAPI | Fuera del ejemplo. Ver «Cómo crece» |
| Paginación, idempotencia | El ejemplo no lista pedidos ni recibe pagos |
| Una facade para consultar | Solo interviene un servicio: `api` lo llama directo |

---

## Cómo crece: cuándo agregar cada cosa

| Cuando… | Agrega… | Y deja… |
|---|---|---|
| Una operación coordina a dos o más servicios o clientes | un método en la facade | el atajo `api → service` para lo simple |
| Aparece otra funcionalidad (clientes, pagos) | sus clases en los mismos paquetes: `ClienteController`, `ClienteService`… | un solo árbol de capas |
| Los paquetes pasan de unas diez clases cada uno | un nivel por funcionalidad dentro de cada capa, o al revés | `CapasTest`, ajustada: la regla no cambia |
| Los datos tienen que sobrevivir al apagado | PostgreSQL u otra base, migraciones versionadas y `ddl-auto: validate` | H2 solo para las pruebas |
| La API sale de tu máquina | Spring Security: quién entra y qué puede hacer | `/actuator/health` abierto para quien vigila |
| Un proveedor se cae seguido | un interruptor de circuito (Resilience4j) | el reintento en una sola capa |
| Un servicio tiene dos implementaciones de verdad | la interfaz y sus implementaciones | sin interfaz donde solo hay una |
| El mapeo pasa de una pantalla | un generador de mapeo, como MapStruct | las reglas fuera del mapper |
| Otros equipos consumen la API | el contrato OpenAPI, generado en la construcción | los ejemplos con `curl` |
| Se despliega en un orquestador | `docker build -t pedidos .`, y la administración en otro puerto | `exec java` como proceso 1 |

Regla para decidir: **un paquete o una dependencia nueva tiene que resolver un problema que ya
tienes**. Si no puedes decir cuál, todavía no lo necesitas.

---

## Cómo quitar el ejemplo

Los pedidos están para demostrar la estructura. Para empezar tu propio proyecto:

1. En `pom.xml`, cambia `groupId`, `artifactId`, `name` y `description`.
2. Renombra el paquete raíz `mx.habil.pedidos` por el tuyo, en `src/main` y en `src/test`. El
   nombre va al revés de tu dominio: si tu sitio es `tienda.mx`, el paquete es `mx.tienda.<app>`.
3. Cambia las clases de pedidos por las tuyas, paquete por paquete. Deja los paquetes que no uses
   todavía fuera: `facade` solo nace cuando hay algo que coordinar.
4. En `CodigoError`, cambia la dirección base de los errores (`https://pedidos.example/errors/`)
   por una de tu dominio, y deja solo los códigos que uses.
5. En `CapasTest` y en `herramientas/probar-frontera.sh`, cambia `mx.habil.pedidos` por tu paquete
   y `PedidoService` y `PedidoController` por un servicio y un controlador tuyos.
6. Corre `./mvnw verify && bash herramientas/probar-frontera.sh`.
7. Reescribe este README. El código y `LICENSE` son MIT; si conservas el texto de los `.md`, mira
   `LICENSE-CONTENIDO.md`.

---

## Versiones

Medidas el 17 de noviembre de 2026.

| Pieza | Versión |
|---|---|
| Java | 25, la LTS vigente. Probado con Temurin 25.0.5+8 en macOS arm64 y en Linux arm64 (contenedor `ubuntu:24.04`), y con Temurin 25.0.4+7 en macOS arm64. Linux x86-64 todavía no se ha probado. El Enforcer no deja construir con otra línea |
| Spring Boot | 4.1.2, la vigente ese día. Trae Spring Framework 7, Jackson 3, Hibernate 7 y Tomcat 11 |
| Maven | 3.9.16, por el Maven Wrapper 3.3.4 |
| Pruebas | JUnit, Mockito y AssertJ, las que trae Spring Boot; ArchUnit 1.5.1; JaCoCo 0.8.16 |
| Imagen | `eclipse-temurin:25-jdk` para construir y `eclipse-temurin:25-jre` para correr |
| CI | GitHub Actions: `actions/checkout@v7` y `actions/setup-java@v6` con Temurin 25, en Ubuntu y en macOS con `/bin/bash`; corre en cada cambio, a mano y cada lunes. GitHub apaga la corrida de los lunes si el repositorio público pasa 60 días sin actividad |

---

## Licencia

El código es [MIT](LICENSE). El texto y las explicaciones son
[CC BY-SA 4.0](LICENSE-CONTENIDO.md).
