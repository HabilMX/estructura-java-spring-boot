#!/usr/bin/env bash
# La frontera que sabe fallar: comprueba que la prueba de arquitectura de verdad
# detecta una flecha hacia afuera, y que la detecta por la razón exacta.
#
# Qué hace, en una COPIA temporal del proyecto (tu carpeta no se toca):
#
#   Corre la misma prueba, CapasTest, dos veces. Entre las dos solo cambia un
#   método de PedidoService.
#
#   1. CONTROL. Con el proyecto tal como está. CapasTest tiene que correr y
#      pasar: al menos una regla, cero fallas. Si no, el experimento está mal
#      montado (falta Java 25, no hay red para bajar las dependencias) o tu
#      proyecto ya trae una flecha hacia afuera, y el paso 2 no probaría nada:
#      se para en rojo.
#   2. VIOLACIÓN. Se siembra en PedidoService (el cocinero) un método que recibe
#      un PedidoController (el mesero). Tiene que COMPILAR: para Java no hay
#      nada malo. Y CapasTest tiene que FALLAR, en una sola regla y por la
#      razón exacta: «PedidoService.avisar(...) has parameter of type
#      <mx.habil.pedidos.api.PedidoController>». Si pasa, la frontera no
#      existe. Si no compila, o falla por otra cosa, tampoco demuestra nada.
#      En los tres casos sale en rojo.
#
# Uso:  bash herramientas/probar-frontera.sh   → sale 0 si la frontera se sostiene
#
# Por qué así: un experimento que solo espera un rojo se pone «verde» también
# cuando falla por la razón equivocada. Lo impiden tres cosas: el control
# demuestra que la prueba corre y pasa con el proyecto limpio; la violación
# exige que el código compile; y el resultado se lee del informe de Surefire
# (cuántas reglas corrieron, cuántas fallaron y con qué mensaje), no del código
# de salida de Maven.
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
prueba="mx.habil.pedidos.arquitectura.CapasTest"
cocinero="src/main/java/mx/habil/pedidos/service/PedidoService.java"
mesero="mx.habil.pedidos.api.PedidoController"
regla="lasFlechasSoloVanHaciaAdentro"
esperado="Method <mx.habil.pedidos.service.PedidoService.avisar(${mesero})> has parameter of type <${mesero}>"

tmp="$(cd "$(mktemp -d)" && pwd -P)"
# Al salir se borra la copia, y el código de salida se conserva. Con el bash
# 3.2 que trae macOS, si el guion se corta por una variable sin definir
# (set -u), la trampa recibe un 0 en «$?»: sin la bandera «terminado», un
# guion cortado a la mitad saldría 0, un falso verde. Por eso, si no llegó al
# final, sale 1 aunque «$?» diga 0.
salir() {
  local codigo=$?
  rm -rf "$tmp"
  if [ "$codigo" -eq 0 ] && [ "${terminado:-}" != 1 ]; then
    echo "❌ el guion se cortó antes de terminar" >&2
    codigo=1
  fi
  exit "$codigo"
}
trap salir EXIT

if [ ! -f "$raiz/mvnw" ] || [ ! -f "$raiz/$cocinero" ]; then
  echo "❌ no encuentro mvnw o ${cocinero}: corre el guion desde la plantilla"
  exit 1
fi

# La copia lleva todo menos lo compilado y la historia de git.
copia="$tmp/copia"
mkdir -p "$copia"
(cd "$raiz" && tar --exclude=./target --exclude=./.git -cf - .) | (cd "$copia" && tar -xf -)

informe="$copia/target/surefire-reports/TEST-${prueba}.xml"
detalle="$copia/target/surefire-reports/${prueba}.txt"

# Compila el código y las pruebas, sin correrlas. Deja la salida en $1.
compilar() { (cd "$copia" && sh ./mvnw -q -B test-compile >"$1" 2>&1); }
# Corre solo la prueba de arquitectura. Deja la salida en $1. El código de
# salida no se usa: lo que cuenta es el informe.
probar() { (cd "$copia" && rm -rf target/surefire-reports && sh ./mvnw -q -B test -Dtest=CapasTest >"$1" 2>&1) || true; }
# Lee un número del encabezado del informe: tests, failures, errors o skipped.
cuenta() { sed -n "s/.*<testsuite [^>]* $1=\"\([0-9][0-9]*\)\".*/\1/p" "$informe" | head -n 1; }
# Muestra un registro sin la ruta de la carpeta temporal, que cambia en cada corrida.
mostrar() { sed -e "s#$copia#…#g" -e 's/^/     /' "$1" | head -n 25; }

echo "1/2 control: el proyecto tal como está; el cocinero (service) no conoce al mesero (api)"
if ! compilar "$tmp/control-compila.log"; then
  echo "❌ el control NO compiló; el experimento está mal montado:"
  mostrar "$tmp/control-compila.log"
  exit 1
fi
probar "$tmp/control-prueba.log"
if [ ! -f "$informe" ]; then
  echo "❌ el control no dejó informe: CapasTest no corrió. El experimento está mal montado:"
  mostrar "$tmp/control-prueba.log"
  exit 1
fi
corrieron="$(cuenta tests)"
if [ "${corrieron:-0}" -lt 1 ] || [ "$(cuenta failures)" != 0 ] || [ "$(cuenta errors)" != 0 ] ||
  [ "$(cuenta skipped)" != 0 ]; then
  echo "❌ el control NO pasó: tu proyecto, tal como está, ya rompe una regla de CapasTest:"
  mostrar "$detalle"
  exit 1
fi
if grep -F -q "$mesero" "$copia/$cocinero"; then
  echo "❌ ${cocinero} ya menciona a ${mesero}; el experimento está mal montado"
  exit 1
fi
echo "   ✅ compiló y CapasTest pasó: ${corrieron} reglas, 0 fallas"

echo "2/2 violación: se siembra en PedidoService un método que recibe un PedidoController"
if [ "$(tail -n 1 "$raiz/$cocinero")" != "}" ]; then
  echo "❌ ${cocinero} ya no termina en «}»; no se pudo sembrar. El experimento está mal montado"
  exit 1
fi
sed '$d' "$raiz/$cocinero" >"$copia/$cocinero"
cat >>"$copia/$cocinero" <<SEMBRADO

    public void avisar(${mesero} mesero) {
    }
}
SEMBRADO
if ! compilar "$tmp/violacion-compila.log"; then
  echo "❌ la violación NO compiló. Esto no demuestra la frontera:"
  mostrar "$tmp/violacion-compila.log"
  exit 1
fi
echo "   ✅ compiló sin una queja: para Java, que el cocinero conozca al mesero no es un error"
probar "$tmp/violacion-prueba.log"
if [ ! -f "$informe" ]; then
  echo "❌ la violación no dejó informe: CapasTest no corrió. Esto no demuestra la frontera:"
  mostrar "$tmp/violacion-prueba.log"
  exit 1
fi
if [ "$(cuenta failures)" = 0 ] && [ "$(cuenta errors)" = 0 ]; then
  echo "❌ PASÓ: CapasTest no vio que el cocinero conoce al mesero. La frontera no existe."
  exit 1
fi
if [ "$(cuenta tests)" != "$corrieron" ] || [ "$(cuenta failures)" != 1 ] || [ "$(cuenta errors)" != 0 ] ||
  ! grep -F -q "${regla}" "$detalle" || ! grep -F -q "Architecture Violation" "$detalle" ||
  ! grep -F -q "$esperado" "$detalle"; then
  echo "❌ falló, pero NO por la flecha sembrada. Esto no demuestra la frontera:"
  mostrar "$detalle"
  exit 1
fi
grep -F "$esperado" "$detalle" | sed -e 's/ in (.*$//' -e 's/^/     /'
echo "   ✅ CapasTest falló en una sola regla, ${regla}: las flechas solo van hacia adentro"
echo "✅ frontera: se sostiene"
terminado=1
