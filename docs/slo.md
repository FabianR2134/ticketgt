# SLOs (Service Level Objectives)

Este documento define los **SLI** (qué se mide), el **SLO** (la meta sobre esa medición) y el
**presupuesto de error** (cuánto margen de falla permite esa meta) para TicketGT. No hay SLA
(acuerdo contractual con penalización): es un proyecto de laboratorio, pero los SLOs se tratan
como si fueran reales para poder practicar el proceso completo.

Referencia: [Google SRE Book — Service Level Objectives](https://sre.google/sre-book/service-level-objectives/)
y [SRE Workbook — Implementing SLOs](https://sre.google/workbook/implementing-slos/).

## Definición

```yaml
slos:
  - name: consulta-asientos
    sli: "latencia p99 de GET /events/{id}/seats"
    objetivo: "menor a 200 ms durante 99% del tiempo"
    ventana: "30 días móviles"

  - name: reserva
    sli: "latencia p99 de POST /seats/{id}/hold"
    objetivo: "menor a 500 ms durante 99% del tiempo"
    ventana: "30 días móviles"

  - name: pago
    sli: "latencia p99 de POST /payments"
    objetivo: "menor a 1500 ms durante 99% del tiempo"
    ventana: "30 días móviles"

  - name: disponibilidad
    sli: "proporción de respuestas no 5xx sobre el total de respuestas, en los endpoints públicos del Gateway"
    objetivo: "99.95% mensual"
    ventana: "30 días calendario"
```

## Por qué p99 y no el promedio

El promedio esconde a los usuarios que peor lo pasan. Si 99 peticiones tardan 50 ms y una
tarda 10 segundos, el promedio parece bueno (~150 ms) pero hay un usuario esperando 10 s. El
p99 dice: "99 de cada 100 peticiones terminan antes de este tiempo", así que expone esa cola
larga. Para un evento donde 1 millón de personas compiten por 80,000 asientos, esa cola larga
es exactamente la experiencia que más duele (el usuario que se queda viendo un spinner).

## Presupuesto de error

El presupuesto de error es el tiempo (o proporción de peticiones) que el SLO permite fallar
sin que se considere un incumplimiento. Mientras quede presupuesto, el equipo puede tomar
riesgos (desplegar más seguido, probar cambios agresivos, experimentos de caos). Cuando se
agota, la prioridad cambia a estabilizar antes que agregar funcionalidad nueva.

### disponibilidad — 99.95% mensual

```
Minutos en 30 días         = 30 × 24 × 60 = 43,200 min
Presupuesto de error (0.05%) = 43,200 × 0.0005 ≈ 21.6 min/mes de tiempo con error
```

Es decir: el sistema puede estar efectivamente "caído" (devolviendo 5xx a una proporción
relevante de usuarios) hasta ~21.6 minutos por mes sin romper el SLO. Repartido en 10 meses de
laboratorio, son ~3.6 horas de margen total — suena generoso, pero un solo despliegue mal hecho
en un pico de tráfico puede consumirlo todo de una vez.

### consulta-asientos — p99 < 200 ms (99% del tiempo)

```
Peticiones toleradas por encima de 200 ms = 1% del total
```

Con el objetivo de carga del proyecto (100,000 usuarios consultando el mismo evento), 1% de
margen son hasta 1,000 peticiones lentas sin incumplir el SLO en esa ventana. Si Catalog
empieza a superar ese 1%, hay que investigar antes de seguir cargando tráfico.

### reserva — p99 < 500 ms (99% del tiempo)

El umbral es más alto que consulta-asientos porque `POST /seats/{id}/hold` hace más trabajo
(bloqueo de fila/registro, posible coordinación con Redis) y es una operación de escritura, no
de lectura. Mismo cálculo de presupuesto: 1% de las reservas pueden tardar más de 500 ms sin
romper el SLO.

### pago — p99 < 1500 ms (99% del tiempo)

El pago depende de un proveedor externo (latencia fuera de nuestro control), por eso el umbral
es el más alto de los tres. Si el proveedor real resulta más lento que esto de forma
consistente, el SLO se revisa — no tiene sentido fijar una meta que depende de un tercero sin
validarla primero contra datos reales.

## Qué pasa cuando se agota el presupuesto

1. Se congelan despliegues de funcionalidad nueva en el servicio afectado.
2. Se prioriza la causa raíz del incumplimiento (ver `docs/runbooks/`).
3. Se documenta el incidente en `docs/lab/` (hipótesis, medición, conclusión) y, si cambia una
   decisión de arquitectura, se agrega un ADR en `docs/adr/`.
4. No se reanuda el ritmo normal de cambios hasta que la ventana móvil vuelva a tener margen.

## Relación con los requisitos de negocio

Estos SLOs son metas de **rendimiento y disponibilidad** (R5, R6, R8 en
[`docs/requirements.md`](./requirements.md)). Los requisitos de **corrección** (R1–R4: no
vender ni cobrar dos veces, pago implica boleto, los holds vencen solos) no son SLOs — no
admiten un "99% de las veces", deben cumplirse siempre. Un incumplimiento de R1–R4 es un bug
crítico, no un consumo de presupuesto de error.
