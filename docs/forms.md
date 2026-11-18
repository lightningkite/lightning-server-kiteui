# Forms

`com.lightningkite.kiteui.forms` renders any `@Serializable` value as an editable form, a read-only view, or a
table, by walking its serializer.  Strings, numbers, dates, enums, nested classes, lists, sets, maps, sealed
classes, files and foreign keys each get a suitable control.  The admin panel is built on it; you can use it in
any screen.

## Minimal example

```kotlin
val module = FormModule().apply { defaults() }       // every built-in renderer
val draft = Signal(AppRelease(version = "1.0.0", platform = null, requiredUpdate = false))

col {
    form(module, draft)                               // one labeled field per property
    view(module, draft)                               // the same, read-only
}
```

`form` takes any `MutableReactive<T>` and `view` any `Reactive<T>`.  Both have overloads taking an explicit
`KSerializer<T>` for when the type isn't reified.  To edit a server record, combine this with the draft-and-diff
pattern in [model-cache.md](model-cache.md#editing-a-record); the demo's
[`AppReleasePages.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/forms/AppReleasePages.kt)
does exactly that.

## Configuring the module

Two features need app-specific wiring:

```kotlin
fun UserSession.formModule(): FormModule = FormModule().apply {
    defaults()
    // ServerFile fields get an upload button.
    fileUpload = { file -> file.toServerFile(api.uploadEarlyEndpoint) ?: error("Upload failed") }
    // @References(User::class) fields get search, a display name and a link.
    typeInfo = { typeName ->
        if (typeName == User.serializer().descriptor.serialName) TypeInfo(
            serializer = User.serializer(),
            cache = { users },
            page = { id -> { UserDetailPage(id.toString()) } },
            renderToString = { id -> users.item(id).awaitOnce()?.name ?: "Unknown user" },
        ) else null
    }
}
```

Without `fileUpload`, file fields can't upload; without `typeInfo`, foreign keys show as raw IDs.  Other knobs:
`visibilitySettings` (which annotations hide a field or make it read-only), `serializersModule` (for
`@Contextual` types), and `enableRendererSwitching` (a per-field picker between compatible renderers, handy while
designing).

## Annotations

Put these (from `com.lightningkite.services.data`) on model properties in `shared`:

| Annotation | Effect |
|---|---|
| `@DisplayName("...")`, `@Description("...")`, `@Hint("...")` | Label, help text, placeholder. |
| `@Multiline`, `@MaxLength(n)` | Text area; length limit. |
| `@References(Other::class)`, `@MultipleReferences(Other::class)` | Foreign-key picker (needs `typeInfo`). |
| `@AdminHidden` | Not rendered.  `_id: Uuid` fields are hidden by default. |
| `@AdminViewOnly`, `@Denormalized` | Rendered read-only. |
| `@Group("...")`, `@Importance(n)` | Grouping and ordering of fields. |
| `@AdminTableColumns(...)`, `@AdminTitleFields(...)`, `@NaturalSort(...)` | Default table columns, the fields that title a row, default sort. |

## Tables

`renderTable` shows a list with resizable, draggable, user-selectable columns, and pages a `ModelCache` list
automatically:

```kotlin
val module = FormModule().apply { defaults() }
val releases = remember { currentSessionNotNull().appReleases.list(Query()) }
val columns = Signal(AppRelease.serializer().defaultColumns().map { ColumnInfo(it, module) })

expanding.frame {
    sizeConstraints(height = 30.rem).renderTable(
        module = module,
        innerSerializer = AppRelease.serializer(),
        items = releases,
        columns = columns,
        linkTo = { release -> { AppReleaseEditPage(release._id.toString()) } },
    )
}
```

When the module depends on the session (as `formModule()` above does), build it with `rememberSuspending` and
render inside `swapping(current = { module() }) { m -> ... }`, as the demo's list pages do.

## Custom renderers

A `Renderer<T>` supplies `form` and `view` (and optionally compact `cellForm`/`cellView` for tables).  Register it
with a `Selector` matching a type name, a serial kind, or an annotation:

```kotlin
object CentsRenderer : Renderer<Long> {
    override fun priority(context: RenderContext<Long>, module: FormModule) = 10f
    override fun form(context: RenderContext<Long>, value: MutableReactive<Long>, module: FormModule):
            ElementWriter.CanAddTheme.() -> Unit = {
        fieldTheme.numberInput { content bind value.lens(get = { it / 100.0 }, set = { (it?.times(100))?.toLong() ?: 0L }) }
    }
    override fun view(context: RenderContext<Long>, value: Reactive<Long>, module: FormModule):
            ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { "$" + (value() / 100.0) } }
    }
}

val module = FormModule().apply {
    defaults()
    register(Selector(annotation = "com.example.Cents"), CentsRenderer)
}
```

The highest `priority` among matching renderers wins; a negative priority opts out for that context.

## Gotchas

- **Ties go to the first registered.**  Built-ins mostly return priority 1-2, so a replacement registered after
  `defaults()` must return more, or it is never chosen.
- **`form()` without a module renders nothing useful**: an empty `FormModule()` has no renderers and throws
  "No renderer for ...".  Always call `defaults()`.
- **Build the module once per screen**, not inside a reactive block that reruns on every change.
- **Selector `type` is the serial name without its type arguments**, e.g. `"kotlin.String"` or
  `"com.lightningkite.services.files.ServerFile"`.
