package com.lightningkite.mppexampleapp

import com.lightningkite.*
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.login
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.recyclerView
import com.lightningkite.kiteui.views.direct.scrolls
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.schema.*
import com.lightningkite.serialization.ClientModule
import com.lightningkite.serialization.SerializationRegistry
import com.lightningkite.serialization.VirtualInstance
import com.lightningkite.serialization.serializableProperties
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Property<Theme>(defaultTheme)

fun ViewWriter.app(navigator: ScreenNavigator, dialog: ScreenNavigator) {
    com.lightningkite.prepareModelsShared()
    prepareModelsDemoClient()
    DefaultSerializersModule = ClientModule
    LargeTestModel.serializer().serializableProperties!!
//    rootTheme = { appTheme() }
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink(title = { "Home" }, icon = { Icon.home }) { { HomeScreen() } },
                NavLink("Auth", icon = Icon.person) { AuthTestScreen() }
            )
        }

//        ::exists {
//            navigator.currentScreen.await() !is UseFullScreen
//        }
    }
}

@Routable("/auth")
class AuthTestScreen : Screen {
    override fun ViewWriter.render() {
        stack {
            val schema = asyncReadable { fetch("http://localhost:8080/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered - card - login(server.auth.also { println("AUTH: $it") }) {
                    onLogin { println("Logged In! Info: $it") }
                }
            }
        }
    }
}

@Routable("/")
class HomeScreen : Screen {
    override fun ViewWriter.render() {
        scrolls - col {
            val prop = Property(Post())
            card - form(FormModule(), Post.serializer(), prop)
            card - view(FormModule(), Post.serializer(), prop)
//            val prop = Property<Condition<LargeTestModel>>(Condition.Never)
//            card - form(serializer<Condition<LargeTestModel>>(), prop)
//            text { ::content{ prop().toString() } }


//            Condition.serializer(LargeTestModel.serializer()).let {
//                val prop = Property(it.default())
//                card - form(it, prop)
//                text { ::content { prop().toString() }}
//            }
//
//            val json = DefaultJson
//            Query.serializer(LargeTestModel.serializer()).let {
//                val prop = Property(Query<LargeTestModel>())
//                card - form(it, prop)
//                text { ::content { json.encodeToString(it, prop()) }}
//                text { ::content { UrlProperties.encodeToString(it, prop()) }}
//            }

//            ListSerializer(SortPartSerializer(LargeTestModel.serializer())).let {
//                val prop = Property(it.default())
//                card - form(it, prop)
//                text { ::content { prop().toString() }}
//            }

//            SortPartSerializer(LargeTestModel.serializer()).let {
//                val prop = Property<SortPart<LargeTestModel>>(it.default())
//                card - form(it, prop)
//                text { ::content { prop().toString() }}
//            }
//
//            DataClassPathSerializer(LargeTestModel.serializer()).let {
//                val prop = Property(it.default())
//                card - form(it, prop)
//                text { ::content { prop().toString() }}
//            }

//            card - form(LargeTestModel.serializer(), prop)
//            card - view(LargeTestModel.serializer(), prop)
//            text { ::content { prop().toString() }}
//
//            val vtype = LargeTestModel.serializer().makeVirtualType() as VirtualStructure
//            text(vtype.annotations.toString())
//            val prop2 = Property(vtype())
//            form(vtype, prop2, listOf())
//            text { ::content { prop2().toString() }}
        }
    }
}

@Routable("real-test")
class RealTestScreen : Screen {
    override fun ViewWriter.render() {
        scrolls - col {
            val schema = asyncReadable { fetch("http://localhost:8080/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
                val s = schema()
                val registry = SerializationRegistry(ClientModule)
                registry.register(s)
                val user = s.structures.values.find { it.serialName.contains("User") }!!
                val userT = user.Concrete(registry, arrayOf())
                form(FormModule(), userT, Property(userT()))
            }
        }
    }
}

@Routable("real-test-2")
class RealTest2Screen : Screen {
    override fun ViewWriter.render() {
        col {
            val schema = asyncReadable { fetch("http://localhost:8080/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
                val server = ExternalLightningServer(schema())
                val user = server.models["/test-model/rest"] as ClientModelRestEndpointsStandardImpl<VirtualInstance, Comparable<Comparable<*>>>
                expanding - recyclerView {
                    children(asyncReadable { user.query(Query()) }) {
                        card - view(FormModule(), user.serializer, it)
                    }
                }
            }
        }
    }
}

@GenerateDataClassPaths
@Serializable
data class Post(
    @AdminHidden override val _id: UUID = uuid(),
    val title: String = "My Post",

    @Hint("Content of your post goes here")
    @DisplayName("boody")
    @Multiline
    val body: String = "",

    @Importance(8)
    @Sentence("Posted at _")
    @Denormalized
    val postedAt: Instant = now(),

    @Importance(8)
    @DoesNotNeedLabel val visibility: PostVisibility = PostVisibility.HIDDEN,
    @Sentence("_ likes") @Denormalized val likes: Int = 32
) : HasId<UUID>

@Serializable
enum class PostVisibility {
    @DisplayName("Hidden")
    HIDDEN,

    @DisplayName("Friends Only")
    FRIENDS_ONLY,

    @DisplayName("Public")
    PUBLIC,
}

@Serializable
enum class ShippingOptions {
    Slow,
    Prime,

    @DisplayName("ASAP")
    Panic,
}

@GenerateDataClassPaths
@Serializable
data class LargeTestModel(
    override val _id: UUID = uuid(),
    var boolean: Boolean = false,
    var byte: Byte = 0,
    var short: Short = 0,
    @Index var int: Int = 0,
    var long: Long = 0,
    var float: Float = 0f,
    var double: Double = 0.0,
    @Sentence("My char is _") var char: Char = ' ',
    var string: String = "",
    var uuid: UUID = uuid(),
    @Contextual var instant: Instant = Instant.fromEpochMilliseconds(0L),
    var option: ShippingOptions = ShippingOptions.Slow,
    var list: List<Int> = listOf(),
    var listEmbedded: List<ClassUsedForEmbedding> = listOf(),
    var set: Set<Int> = setOf(),
    var setEmbedded: Set<ClassUsedForEmbedding> = setOf(),
    var map: Map<String, Int> = mapOf(),
    var embedded: ClassUsedForEmbedding = ClassUsedForEmbedding(),
    var booleanNullable: Boolean? = null,
    var byteNullable: Byte? = null,
    var shortNullable: Short? = null,
    var intNullable: Int? = null,
    var longNullable: Long? = null,
    var floatNullable: Float? = null,
    var doubleNullable: Double? = null,
    var charNullable: Char? = null,
    var stringNullable: String? = null,
    var uuidNullable: UUID? = null,
    @Contextual var instantNullable: Instant? = null,
    var optionNullable: ShippingOptions? = null,
    var listNullable: List<Int>? = null,
    var mapNullable: Map<String, Int>? = null,
    var embeddedNullable: ClassUsedForEmbedding? = null,
) : HasId<UUID> {
    companion object
}

@GenerateDataClassPaths
@Serializable
data class ClassUsedForEmbedding(
    var value1: String = "default",
    var value2: Int = 1
)
