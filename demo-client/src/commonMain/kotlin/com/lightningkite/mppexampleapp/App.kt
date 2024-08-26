package com.lightningkite.mppexampleapp

import com.lightningkite.UUID
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FieldVisibility
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.ScreenNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.scrolls
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningdb.*
import com.lightningkite.uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Property<Theme>(defaultTheme)

fun ViewWriter.app(navigator: ScreenNavigator, dialog: ScreenNavigator) {
    prepareModels()
    com.lightningkite.lightningdb.prepareModels()
//    rootTheme = { appTheme() }
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink(title = { "Home" }, icon = { Icon.home }) { { HomeScreen() } },
            )
        }

//        ::exists {
//            navigator.currentScreen.await() !is UseFullScreen
//        }
    }
}

@Routable("/")
class HomeScreen : Screen {
    override fun ViewWriter.render() {
        scrolls - col {
//            val prop = Property(Post())
//            card - form(Post.serializer(), prop)
//            card - view(Post.serializer(), prop)
//            val prop = Property<Condition<LargeTestModel>>(Condition.Never)
//            card - form(serializer<Condition<LargeTestModel>>(), prop)
//            text { ::content{ prop().toString() } }

            DataClassPathSerializer(LargeTestModel.serializer()).let {
                val prop = Property<DataClassPathPartial<LargeTestModel>>(DataClassPathSelf(LargeTestModel.serializer()))
                card - form(it, prop)
                text { ::content { prop().toString() }}
            }

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

@GenerateDataClassPaths
@Serializable
data class Post(
    @AdminHidden override val _id: UUID = uuid(),
    @DoesNotNeedLabel val title: String = "My Post",

    @Hint("Content of your post goes here")
    @DoesNotNeedLabel
    @Multiline
    val body: String = "",

    @DoesNotNeedLabel val visibility: PostVisibility = PostVisibility.HIDDEN,
    @Denormalized val likes: Int = 32
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
