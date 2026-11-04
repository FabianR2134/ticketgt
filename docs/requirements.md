# Requisitos medibles

Traduce las reglas de negocio de Marcela (ver descripción del proyecto en Linear) a criterios
que una prueba automatizada pueda aprobar o reprobar. Cada fila de la tabla es un requisito
que el sistema debe cumplir; el detalle de implementación y la prueba concreta se documentan
en el servicio o en `loadtests/` correspondiente cuando se construya.

## Tabla de requisitos

| ID | Requisito de Marcela | Criterio medible | Cómo se prueba |
| -- | -- | -- | -- |
| R1 | Nunca vender el mismo asiento dos veces | 0 asientos duplicados en 1,000,000 de intentos de reserva concurrentes sobre el mismo asiento | Test de concurrencia (muchos hilos/clientes contra `POST /seats/{id}/hold`) + job reconciliador que compara `seats.status = SOLD` contra `tickets` emitidos |
| R2 | Nunca cobrar dos veces | 0 cobros duplicados con 10,000 reintentos de la misma petición de pago | Test de idempotencia: reenviar la misma `Idempotency-Key` contra `POST /payments` y verificar un único cargo en el proveedor y en la base de datos |
| R3 | Si se cobra, el cliente recibe su boleto | `count(payments.status = PAID) == count(tickets.status = ISSUED)` en todo momento, con desviación 0 al cierre de cada ventana de reconciliación | Job reconciliador cada 1 minuto; alerta si la igualdad no se cumple por más de 5 minutos |
| R4 | Los apartados (holds) vencen solos | El asiento pasa de `HELD` a `AVAILABLE` en menos de 30 s después de su `expires_at`, sin intervención manual | Test con reloj controlado (`Clock` inyectado): se crea un hold, se avanza el reloj simulado más allá de `expires_at` y se verifica el cambio de estado |
| R5 | Las caídas parciales no tumban todo | Con el servicio Payments caído, Catalog sigue respondiendo `GET /events/{id}/seats` con p99 < 200 ms y tasa de error < 1% | Experimento de caos (Chaos Mesh): matar los pods de Payments y correr k6 contra Catalog durante el experimento |
| R6 | Fila virtual con admisión controlada | El backend (Inventory/Orders) recibe como máximo **N = 200 admisiones/segundo** (ver nota) sin importar cuántos usuarios esperan en la fila | Script k6 que simula 50,000 usuarios en la sala de espera y mide la tasa de admisión real contra el backend |
| R7 | Frenar bots | Más del 95% de las peticiones del escenario de bots simulado (sin cookie de sesión válida, sin resolver el challenge, patrón de ráfaga) quedan bloqueadas antes de llegar a Inventory | Script k6 que mezcla tráfico "humano" y "bot" + revisión de los contadores de la capa de rate limiting/challenge |
| R8 | Ver todo en tiempo real | El tablero (Grafana) refleja ventas/s, tamaño de la fila y tasa de error con un retraso (staleness) menor a 15 s respecto al evento real | Revisión manual contra el dashboard durante una prueba de carga + verificación del intervalo de scrape de Prometheus |

> **Nota sobre R6:** Marcela no definió el valor de N. Se propone **200 admisiones/segundo**
> como punto de partida (permite vaciar una fila de 80,000 asientos en ~7 minutos sin saturar
> Inventory) y se deja como pregunta abierta para validar con ella. El valor debe ser
> configurable sin recompilar (variable de entorno o flag).

## Relación con los SLOs

Los requisitos R5, R6 y R8 se apoyan directamente en los SLOs definidos en
[`docs/slo.md`](./slo.md) (latencia de Catalog, disponibilidad general). Los requisitos R1 a
R4 son invariantes de negocio (correctness), no SLOs: no admiten "casi siempre", deben
cumplirse siempre o el sistema está roto.

## Preguntas abiertas para Marcela

Cada pregunta trae el supuesto provisional que se usará mientras no haya respuesta. Si la
respuesta real difiere, hay que actualizar este documento y el código que dependa del supuesto.

| # | Pregunta | Supuesto provisional |
| -- | -- | -- |
| 1 | Si el apartado (hold) vence mientras el pago ya está en curso (el usuario ya envió los datos de tarjeta), ¿qué pasa con el asiento? | El asiento se congela (no se libera ni se vende a otro) por hasta 2 minutos adicionales mientras se espera la respuesta del proveedor de pagos. Si el pago no se confirma en ese plazo, se libera y el pago se revierte/cancela. |
| 2 | ¿Se maneja más de una moneda? | No. Solo quetzales (GTQ) en esta fase. Soporte multi-moneda queda fuera de alcance. |
| 3 | ¿Existen reembolsos? ¿Bajo qué condiciones? | Solo si el evento se cancela por completo. El reembolso es un proceso manual/batch fuera del flujo de compra; no hay reembolso parcial ni por desistimiento del comprador. |
| 4 | ¿Cuántos boletos puede comprar una misma cuenta para un mismo evento? | Máximo 6 boletos por cuenta por evento. Se aplica a nivel de Orders antes de crear los holds. |
| 5 | ¿Cuánto dura un apartado (hold) antes de vencer? | 10 minutos desde que se crea el hold hasta `expires_at`. |
| 6 | ¿Los boletos se pueden transferir a otra persona después de comprados? | No en esta fase. El boleto queda asociado a la cuenta que pagó; transferencia queda como funcionalidad futura. |
| 7 | Si el usuario cierra el navegador o pierde conexión mientras está en la fila virtual, ¿pierde su lugar? | Sí, pero conserva un token de reingreso válido por 5 minutos: si vuelve a entrar en ese plazo recupera su posición aproximada; después de eso, pierde el lugar y debe reingresar a la fila. |
| 8 | ¿Los asientos son numerados o es admisión general (solo cupo)? | Numerados. El modelo de datos de Inventory asume asiento individual con estado propio (`AVAILABLE` / `HELD` / `SOLD`), no solo un contador de cupo. |

## Fuera de alcance (explícito)

- Reventa o mercado secundario de boletos.
- Promociones, cupones o precios dinámicos.
- Facturación fiscal electrónica (se asume fuera del alcance del laboratorio).
