# Editor/Conversor de Texto - Práctica 3 (UT2 · RA3 · PRI)

## Vía elegida
Swing + WindowBuilder (Eclipse). Implementación entregada como clase `practica3_GVA.Practica3`.

## Requisitos cubiertos

### Funcionalidades base (UT1)
- ✅ Área de texto editable (JTextPane)
- ✅ Estado/resumen: nº caracteres, nº palabras, nº líneas (barra inferior)
- ✅ Operaciones encadenables: Mayúsculas, Minúsculas, Invertir texto, Eliminar dobles espacios
- ✅ Búsqueda y reemplazo (diálogo)
- ✅ Contadores en tiempo real
- ✅ Selección con ratón, menú contextual (cortar/copiar/pegar)
- ✅ Atajos: Ctrl+C/X/V, Ctrl+Z (deshacer), Ctrl+Y (rehacer), Ctrl+F (buscar)
- ✅ Estilos en selección (negrita, cursiva, color)
- ✅ Undo/Redo con `UndoManager`

### Nuevas funcionalidades (UT2 · RA3)
- ✅ **Componente personalizado ProgressLabel** - Combina JLabel + JProgressBar
- ✅ **Operaciones de archivo** - Nuevo, Abrir, Guardar, Guardar como
- ✅ **Progreso real** - Muestra progreso durante carga/guardado de archivos
- ✅ **Estados visuales** - Idle, Working, Done, Error con iconos y colores
- ✅ **Progreso detallado** - Muestra porcentaje, líneas procesadas y tamaño del archivo
- ✅ **Manejo de errores** - Feedback visual en el ProgressLabel
- ✅ **Integración no bloqueante** - Usa SwingWorker para operaciones en segundo plano

## Componente ProgressLabel

### Características
- **Estados visuales**: 
  - 🔵 **Working** (⟳): Operación en progreso con barra visible
  - ✅ **Done** (✓): Operación completada (se oculta automáticamente después de 3s)
  - ❌ **Error** (✗): Error en la operación
  - ⚫ **Idle** (●): Listo para nueva operación

- **Información mostrada**:
  - Nombre del archivo en proceso
  - Porcentaje de progreso numérico
  - Línea actual y tamaño procesado
  - Tiempo restante estimado (para archivos grandes)

### Integración
- Ubicado en la **barra de estado inferior**
- Actualización en tiempo real durante operaciones de archivo
- No bloquea la interfaz de usuario

## Cómo ejecutar

1. **Importar proyecto en Eclipse**:
   - File → Import → Existing Java Project
   - O crear proyecto nuevo y pegar `Practica3.java` en el paquete `practica3_GVA`

2. **Requisitos**:
   - JDK 8+ (Swing estándar, no requiere librerías externas)
   - Eclipse con WindowBuilder (opcional para modificaciones visuales)

3. **Ejecutar**:
   - Clase principal: `practica3_GVA.Practica3`
   - Run as → Java Application

## Uso básico

### Edición de texto
- Escribe texto en el área principal
- Selecciona texto con ratón y usa botones de estilo (B, I, Color)
- Transforma texto con botones Mayúsculas/Minúsculas/Invertir/Quitar dobles espacios
- Buscar/Reemplazar con barra de herramientas o Ctrl+F
- Clic derecho para menú contextual (cortar/copiar/pegar)
- Deshacer/Rehacer con Ctrl+Z/Ctrl+Y

### Gestión de archivos
- **Nuevo** (Ctrl+N): Crea nuevo documento (pregunta por guardar cambios)
- **Abrir** (Ctrl+O): Abre archivo de texto con ProgressLabel mostrando progreso
- **Guardar** (Ctrl+S): Guarda en archivo actual
- **Guardar como**: Guarda en nuevo archivo

### ProgressLabel en acción
Al abrir/guardar archivos:
1. ProgressLabel muestra estado "Working" (🔵)
2. Barra de progreso avanza con porcentaje real
3. Texto informativo muestra detalles del proceso
4. Al completar, muestra "Done" (✅) y vuelve a "Idle" automáticamente

## Casos de prueba recomendados

1. **Archivo pequeño** (< 50KB): ProgressLabel muestra progreso suave
2. **Archivo mediano** (50KB-1MB): Progreso real con actualizaciones visibles
3. **Archivo grande** (> 1MB): Muestra tiempo estimado y progreso detallado
4. **Archivo vacío**: Comportamiento correcto sin errores
5. **Error de archivo**: ProgressLabel muestra estado de error apropiado

## Estructura del proyecto
```
practica3_GVA/
├── Practica3.java          # Clase principal con interfaz completa
├── ProgressLabel           # Componente personalizado (clase interna)
├── Operaciones de archivo  # Gestión con progreso real
└── Utilidades             # Transformaciones, estilos, contadores
```

## Autor
**Gabriel Veiga Álvarez**  
*Desarrollo de Interfaces - DAM2*  
*Curso 2025–26*