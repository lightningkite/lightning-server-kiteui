package com.lightningkite.lightningserver.admin

import com.lightningkite.UUID
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.Property
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.lightningdb.*
import com.lightningkite.now
import com.lightningkite.uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Routable("experimental")
class ExperimentalScreen: Screen {
    override fun ViewWriter.render() = col {
        val prop = Property(Post())
        card - form(FormModule(), Post.serializer(), prop)
        card - view(FormModule(), Post.serializer(), prop)
    }
}


@GenerateDataClassPaths
@Serializable
data class Post(
    @AdminHidden override val _id: UUID = uuid(),
    val title: String = "My Post",

    @Hint("Content of your post goes here")
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
