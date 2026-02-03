# Plan: Renderer Switching for forms2

## Goal
Add the ability to swap between compatible renderers for a field, similar to the old `showTypePicker` functionality, but as an opt-in feature in FormModule. The switcher UI must be extremely compact.

## Design Decisions

### 1. Configuration
Add to `FormModule`:
```kotlin
var enableRendererSwitching: Boolean = false
```

### 2. Get All Compatible Renderers
Add method to `FormModule`:
```kotlin
fun <T> selectAll(context: RenderContext<T>): List<Renderer<T>>
```
Returns all renderers with `priority >= 0` sorted by priority (highest first).

### 3. Renderer Identification
Add to `Renderer<T>` interface:
```kotlin
val name: String get() = this::class.simpleName ?: "Unknown"
```
Provides a human-readable name for the dropdown. Uses class name by default.

### 4. Selection State
Store user's renderer selections in `FormModule`:
```kotlin
internal val rendererSelections: MutableMap<String, Renderer<*>> = mutableMapOf()
```
Key is generated from `RenderContext` (serializer name + annotation FQNs).

Add helper:
```kotlin
fun <T> selectionKey(context: RenderContext<T>): String
```

### 5. UI Integration Points

Two options:

**Option A: Modify `labeledForm`/`labeledView` extension functions**
The top-level extension functions check `enableRendererSwitching` and wrap with switcher UI.

**Option B: Override `Renderer.labeledForm`/`labeledView` defaults (Chosen)**
Create a wrapper in `fieldWithDescription` that checks the module and adds switcher. This keeps logic centralized and works for all renderers.

### 6. Switcher UI Design
Following the old system's approach (0.75rem × 0.75rem select at top-end):
- Tiny select dropdown in the label row (next to description icon if present)
- Only visible when multiple renderers are available
- Shows renderer name in dropdown
- Positioned at the end of the label row

## Implementation Steps

### Step 1: Update FormModule
- Add `enableRendererSwitching: Boolean = false`
- Add `rendererSelections: MutableMap<String, Renderer<*>>`
- Add `selectAll()` method
- Add `selectionKey()` helper

### Step 2: Update Renderer Interface
- Add `val name: String` property with default implementation

### Step 3: Create Switcher UI Helper
Create new helper function:
```kotlin
fun ViewWriter.fieldWithSwitcher(
    context: RenderContext<*>,
    module: FormModule,
    label: String,
    description: String?,
    content: (Renderer<*>) -> ViewWriter.() -> Unit
)
```

### Step 4: Update labeledForm Extension Functions
Modify `ViewWriter.labeledForm()` and `ViewWriter.labeledView()` in FormModule.kt to:
1. Check if `module.enableRendererSwitching` is true
2. If true and multiple renderers available, show switcher UI
3. Track selection in `module.rendererSelections`
4. Re-render with selected renderer when changed

### Step 5: Update defaults.kt (optional)
No changes needed - works automatically.

## Files to Modify
1. `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms2/FormModule.kt`
   - Add configuration and helper methods
   - Update extension functions

2. `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms2/Renderer.kt`
   - Add `name` property to interface

## UI Mockup

Without switching:
```
┌─────────────────────────────────────┐
│ Label [i]                           │
│ [                input            ] │
└─────────────────────────────────────┘
```

With switching (multiple renderers available):
```
┌─────────────────────────────────────┐
│ Label [i]                       [▼] │
│ [                input            ] │
└─────────────────────────────────────┘
```

The [▼] is a tiny dropdown showing renderer options when clicked.

## Notes
- Selection persists within the FormModule instance (session-scoped)
- Same type+annotations across different fields share the same selection
- Dropdown only appears when >1 renderer matches
- Uses same compact styling as old system (SubtextSemantic, 0.75rem size)
