# ADR 0000: Microservicios con arquitectura hexagonal

## Estado

Aceptada

## Contexto

TicketGT necesita soportar un evento de 80,000 asientos con hasta 1 millón de personas
intentando comprar al mismo tiempo, sin vender ni cobrar dos veces, y sin que una caída
parcial (por ejemplo, el proveedor de pagos) tumbe la posibilidad de seguir consultando el
catálogo. Los dominios del sistema (Catalog, Inventory, Orders, Payments, Ticketing,
Waiting Room, Notifications) tienen patrones de carga y reglas de negocio muy distintos:
Catalog es de solo lectura y de alto volumen; Inventory tiene la regla más estricta de
concurrencia del sistema (nunca vender el mismo asiento dos veces); Payments depende de un
proveedor externo con latencia variable. Necesitamos poder escalar y desplegar cada uno de
forma independiente, y poder probar las reglas de negocio de Inventory y Orders en
milisegundos, sin depender de una base de datos real para cada test.

## Alternativas consideradas

- **Monolito modular**: un solo desplegable con módulos internos bien separados.
  Más simple de operar al inicio, pero todos los dominios comparten el mismo ciclo de
  despliegue y el mismo radio de falla: un bug o una sobrecarga en Notifications podría
  afectar la disponibilidad de Inventory.
- **Microservicios con capas tradicionales** (controller → service → repository, sin
  separar puertos/adaptadores): resuelve el despliegue independiente, pero mezcla la
  lógica de negocio con el framework y la infraestructura (JPA, HTTP) en las mismas
  clases, lo que hace lenta y frágil la prueba de reglas de negocio como "nunca vender el
  mismo asiento dos veces".
- **Microservicios con arquitectura hexagonal** (la elegida): cada servicio expone
  puertos (interfaces) para su dominio y adaptadores para framework/infraestructura
  (REST, Kafka, JPA). El dominio no depende de Spring, de JPA ni de Kafka.

## Decisión

Se usan **microservicios** para los dominios que tienen carga o reglas de negocio
claramente distintas entre sí (Catalog, Inventory, Orders, Payments, Ticketing, Waiting
Room, Notifications), cada uno con su propia base de datos y ciclo de despliegue.

Dentro de los servicios con lógica de negocio rica y crítica —especialmente **Inventory**
(concurrencia de asientos) y **Orders/Payments** (idempotencia, reconciliación)— se usa
**arquitectura hexagonal** (puertos y adaptadores):

- El **dominio** (entidades, reglas de negocio, casos de uso) no importa nada de Spring,
  JPA, Kafka ni HTTP.
- Los **puertos de entrada** (casos de uso) se exponen a los adaptadores de entrada
  (controladores REST, consumidores de Kafka).
- Los **puertos de salida** (ej. `SeatRepository`, `PaymentGateway`) se implementan en
  adaptadores de infraestructura (JPA/Postgres, cliente HTTP del proveedor de pagos),
  que el dominio desconoce.

Servicios con lógica simple (por ejemplo, Notifications: recibe un evento y envía un
correo) pueden usar una estructura en capas más convencional, sin forzar hexagonal donde
no aporta valor (YAGNI).

Comunicación entre servicios: **REST** para llamadas síncronas desde el Gateway hacia los
servicios, **Kafka** para eventos de negocio que varios servicios consumen (ej.
`pago-confirmado`, `boleto-emitido`), y **RabbitMQ** para tareas con reintentos y dead
letter queue (ej. envío de notificaciones). Cada servicio es dueño exclusivo de su base de
datos; ningún otro servicio escribe directamente en ella.

## Consecuencias

**Positivas**

- El dominio de Inventory y Orders se puede probar en milisegundos, sin levantar base de
  datos ni contenedores, porque las reglas de negocio no dependen de infraestructura.
- Cada servicio escala de forma independiente según su propio patrón de carga (Catalog
  necesita muchas réplicas de lectura; Payments necesita pocas pero con control estricto
  de concurrencia).
- Una caída de Payments no impide que Catalog siga respondiendo (cumple R5 en
  `docs/requirements.md`).
- Cambiar de proveedor de pagos, de base de datos o de broker de mensajería implica
  escribir un adaptador nuevo, no reescribir el dominio.

**Negativas**

- Más código de mapeo entre capas (DTO ↔ dominio ↔ entidad JPA) comparado con una
  arquitectura en capas simple.
- Complejidad operativa de un sistema distribuido: hay que resolver consistencia eventual
  entre servicios (ej. Orders se entera de un pago confirmado vía Kafka, no en la misma
  transacción), trazabilidad distribuida (OpenTelemetry) y despliegue coordinado de
  múltiples servicios.
- Curva de aprendizaje más alta para quien no conozca el patrón hexagonal; requiere
  disciplina para no dejar que un atajo meta JPA o Spring dentro del dominio.
- Más piezas de infraestructura que operar desde el día 1 (Kafka, RabbitMQ, múltiples
  bases de datos) comparado con un monolito con una sola base de datos.
