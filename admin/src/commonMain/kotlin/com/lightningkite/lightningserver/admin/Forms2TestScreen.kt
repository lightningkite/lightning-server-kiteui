package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fileName
import com.lightningkite.kiteui.forms.description
import com.lightningkite.kiteui.forms2.*
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.context.invoke
import com.lightningkite.services.data.Description
import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.Group
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.default
import com.lightningkite.services.database.serializableAnnotations
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.services.database.sort
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Test data class covering all primitive and structural types.
 *
 * by Claude
 */
@GenerateDataClassPaths
@Serializable
data class TestFormData(
    @Description("Enable this option to activate the feature. When enabled, additional options become available.")
    @Group("A") val active: Boolean = false,
    @Description("Enter your full name as it appears on official documents.")
    @Group("A") val fullName: String = "Hello World",
    @Description("The quantity of items to process. Must be a positive integer.")
    val quantity: Int = 42,
    val externalId: Long = 1234567890L,
    val price: Double = 3.14159,
    val description: String? = null,
    val defaultQuantity: Int? = 123,
    val features: List<String> = listOf("one", "two", "three"),
    val intList: List<Int> = listOf(1, 2, 3),
    @Description("A set of strings with unique values.")
    val tags: Set<String> = setOf("apple", "banana"),
    @Description("Selected options from the available enum values.")
    val options: Set<TestEnum> = setOf(TestEnum.OPTION_A, TestEnum.OPTION_C),
    @Description("Key-value pairs mapping strings to integers.")
    val stringToIntMap: Map<String, Int> = mapOf("one" to 1, "two" to 2),
    val nestedObject: NestedData = NestedData(),
    val enumField: TestEnum = TestEnum.OPTION_A,
    val nestedList: List<NestedData> = listOf(
        NestedData("First", 1),
        NestedData("Second", 2),
        NestedData("Third", 3),
    ),
)

@Serializable
data class NestedData(
    @Description("The display name for this item.")
    val name: String = "Nested",
    @Description("A numeric value associated with this item.")
    val value: Int = 99,
)

@Serializable
enum class TestEnum {
    OPTION_A,
    OPTION_B,
    OPTION_C
}

/**
 * Test screen for the forms2 rendering system.
 *
 * Displays editable forms and read-only views for all supported types,
 * allowing visual verification that renderers work correctly.
 *
 * by Claude
 */
@Routable("forms2-test")
class Forms2TestScreen : Page {
    override fun ViewWriter.render() {
        val module = FormModule().apply {
            defaults()
            // Mock file uploader for testing - by Claude
            fileUpload = { fileRef ->
                ServerFile("https://example.com/uploads/${fileRef.fileName()}")
            }
            enableRendererSwitching = true
        }

        // Create mutable signals for testing
        val testData = Signal(TestFormData())
        val boolVal = Signal(true)
        val stringVal = Signal("Test String")
        val intVal = Signal(42)
        val longVal = Signal(9876543210L)
        val doubleVal = Signal(2.71828)
        val nullableStringVal = Signal<String?>("Has value")
        val nullableIntVal = Signal<Int?>(null)
        val stringListVal = Signal(listOf("alpha", "beta", "gamma"))
        val enumVal = Signal(TestEnum.OPTION_B)
        val conditionVal = Signal<Condition<TestFormData>>(Condition.serializer(TestFormData.serializer()).default())
        val modificationVal = Signal<Modification<TestFormData>>(Modification.serializer(TestFormData.serializer()).default())
        val sortVal = Signal<List<SortPart<TestFormData>>>(sort { it.quantity.ascending() })

        frame {
            centeredHorizontally.sizeConstraints(width = 60.rem).scrolling.col {
                h1("Forms2 Test Screen")
                subtext("Testing the simplified form rendering system")

                card.col {
                    h2("Condition Editor")

                    labeledForm(module, conditionVal, "Condition")
                    labeledView(module, conditionVal, "Condition")
                }

                card.col {
                    h2("Modification Editor")

                    labeledForm(module, modificationVal, "Modification")
                    labeledView(module, modificationVal, "Modification")
                }

                card.col {
                    h2("Sort Editor")

                    labeledForm(module, sortVal, "Sort")
                    labeledView(module, sortVal, "Sort")
                }

                // Section: Primitive Forms
                card.col {
                    h2("Primitive Types - Forms (Editable)")

                    labeledForm(module, boolVal, "Boolean")
                    labeledForm(module, stringVal, "String")
                    labeledForm(module, intVal, "Int")
                    labeledForm(module, longVal, "Long")
                    labeledForm(module, doubleVal, "Double")
                }

                // Section: Nullable Types
                card.col {
                    h2("Nullable Types - Forms")

                    labeledForm(module, nullableStringVal, "Nullable String (with value)")
                    labeledForm(module, nullableIntVal, "Nullable Int (null)")
                }

                // Section: Collections
                card.col {
                    h2("Collections - Forms")

                    labeledForm(module, stringListVal, "List<String>")
                }

                // Section: Enum
                card.col {
                    h2("Enum Types - Forms")

                    labeledForm(module, enumVal, "TestEnum")
                }

                // Section: Sets - by Claude
                card.col {
                    h2("Set Types - Forms")

                    val stringSetVal = Signal(setOf("apple", "banana", "cherry"))
                    val enumSetVal = Signal(setOf(TestEnum.OPTION_A, TestEnum.OPTION_C))

                    labeledForm(module, stringSetVal, "Set<String>", "A set ensures unique values")
                    labeledForm(module, enumSetVal, "Set<TestEnum>", "Select multiple enum values")

                    subtext { ::content { "String Set: ${stringSetVal()}" } }
                    subtext { ::content { "Enum Set: ${enumSetVal()}" } }
                }

                // Section: Maps - by Claude
                card.col {
                    h2("Map Types - Forms")

                    val stringToIntMap = Signal(mapOf("one" to 1, "two" to 2, "three" to 3))
                    val stringToStringMap = Signal(mapOf("key1" to "value1", "key2" to "value2"))

                    labeledForm(module, stringToIntMap, "Map<String, Int>", "String keys to integer values")
                    labeledForm(module, stringToStringMap, "Map<String, String>", "String keys to string values")

                    subtext { ::content { "String->Int Map: ${stringToIntMap()}" } }
                    subtext { ::content { "String->String Map: ${stringToStringMap()}" } }
                }

                // Section: Complex Object
                card.col {
                    h2("Complex Object - Form")
                    labeledForm(module, testData, "TestFormData")
                }

                // Section: Views (Read-only)
                card.col {
                    h2("Read-Only Views")

                    labeledView(module, boolVal, "Boolean (view)")
                    labeledView(module, stringVal, "String (view)")
                    labeledView(module, intVal, "Int (view)")
                    labeledView(module, enumVal, "Enum (view)")
                    labeledView(module, stringListVal, "List<String> (view)")
                }

                // Section: Cell Views
                card.col {
                    h2("Cell Views (Compact)")

                    row {
                        sizeConstraints(width = 10.rem).card.col {
                            subtext("Boolean")
                            cellView(module, boolVal)
                        }
                        sizeConstraints(width = 10.rem).card.col {
                            subtext("String")
                            cellView(module, stringVal)
                        }
                        sizeConstraints(width = 10.rem).card.col {
                            subtext("Int")
                            cellView(module, intVal)
                        }
                        sizeConstraints(width = 10.rem).card.col {
                            subtext("List")
                            cellView(module, stringListVal)
                        }
                    }
                }

                // Section: Table Renderer (List of data classes)
                // by Claude
                card.col {
                    h2("Table Renderer - List<NestedData>")
                    subtext("Tables render lists of data classes with columns")

                    val tableData = Signal(
                        listOf(
                            NestedData("Alpha", 100),
                            NestedData("Beta", 200),
                            NestedData("Gamma", 300),
                            NestedData("Delta", 400),
                            NestedData("Epsilon", 500),
                            NestedData("Zeta", 600),
                            NestedData("Eta", 700),
                            NestedData("Theta", 800),
                            NestedData("Iota", 900),
                            NestedData("Kappa", 1000),
                        )
                    )

                    labeledView(module, tableData, "Table View")
                }

                // Section: ServerFile Renderer
                // by Claude
                card.col {
                    h2("ServerFile Renderer")
                    subtext("File upload support (requires fileUpload configured on module)")

                    val fileVal = Signal<ServerFile?>(null)
                    val fileWithValue = Signal<ServerFile?>(ServerFile("https://example.com/sample-file.pdf"))

                    labeledForm(module, fileVal, "ServerFile (null)")
                    labeledForm(module, fileWithValue, "ServerFile (with value)")
                    labeledView(module, fileWithValue, "ServerFile (view)")
                }

                // Section: Current Values (for debugging)
                card.col {
                    h2("Current Values (Debug)")

                    text { ::content { "Boolean: ${boolVal()}" } }
                    text { ::content { "String: ${stringVal()}" } }
                    text { ::content { "Int: ${intVal()}" } }
                    text { ::content { "Long: ${longVal()}" } }
                    text { ::content { "Double: ${doubleVal()}" } }
                    text { ::content { "Nullable String: ${nullableStringVal()}" } }
                    text { ::content { "Nullable Int: ${nullableIntVal()}" } }
                    text { ::content { "String List: ${stringListVal()}" } }
                    text { ::content { "Enum: ${enumVal()}" } }
                    text { ::content { "Test Data: ${testData()}" } }
                }
            }
        }
    }
}

