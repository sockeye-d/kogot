# Reglas del proyecto (resumen)

- Sin reflexión en runtime (evitar `Class.forName`, `ServiceLoader`, etc.).
- Auto-registro debe ser determinista, reproducible y compatible con GraalVM.
- Preferir Java cuando todo es static; Kotlin cuando reduce boilerplate real.
- Usar `@NullMarked` y jspecify; null safety primero.
- Utilidades con constructor `private` que lanza `UnsupportedOperationException`.
- Sin async/suspend.
- Respetar Palantir Java Format y convenciones de estilo.
