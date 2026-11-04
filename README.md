# TicketGT

[![ci](https://github.com/FabianR2134/ticketgt/actions/workflows/ci.yml/badge.svg)](https://github.com/FabianR2134/ticketgt/actions/workflows/ci.yml)

Plataforma de venta de tiquetes de alta disponibilidad. Monorepo con los servicios, la plataforma (infraestructura local y Kubernetes), pruebas de carga y documentación.

## Estructura

```
ticketgt/
├── services/
│   ├── inventory/     # asientos y disponibilidad
│   ├── orders/        # órdenes de compra
│   └── payments/      # integración con el proveedor de pagos
├── platform/          # docker-compose, k8s, observabilidad
├── loadtests/         # scripts k6
├── docs/
│   ├── adr/           # decisiones de arquitectura
│   ├── lab/           # diario de laboratorio
│   ├── security/      # threat model, hallazgos
│   └── runbooks/      # procedimientos operativos
└── .github/workflows/ # CI
```

## Prerrequisitos

| Herramienta | Versión | Verificar |
| --- | --- | --- |
| JDK (Temurin) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker | reciente | `docker run --rm hello-world` |
| kind | reciente | `kind version` |
| kubectl | reciente | `kubectl version --client` |
| k6 | reciente | `k6 version` |

## Compilar y probar

```bash
mvn -B verify
```

El mismo comando corre en GitHub Actions en cada push y pull request (`.github/workflows/ci.yml`). El build falla si el JDK es menor a 21 o Maven menor a 3.9.

## Convención de commits

[Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` nueva funcionalidad
- `fix:` corrección de un bug
- `docs:` solo documentación
- `test:` pruebas
- `refactor:`, `chore:`, `ci:`, `perf:` según aplique

Ejemplo: `feat(orders): reservar asiento antes de cobrar`
