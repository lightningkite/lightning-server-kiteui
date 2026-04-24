package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.HasId
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.KSerializer

/**
 * Renderer for ServerFile type (file uploads).
 *
 * Form mode shows:
 * - Current file link with preview image
 * - Upload button (only when fileUpload is configured on module)
 *
 * View mode shows:
 * - External link with preview image and filename
 *
 * Requires FormModule.fileUpload to be set for upload functionality.
 *
 * by Claude
 */
public object ServerFileRenderer : Renderer<ServerFile?> {
    override val name: String = "File Upload"  // by Claude

    override fun priority(context: RenderContext<ServerFile?>, module: FormModule): Float {
        return if (context.serializer.descriptor.serialName == "com.lightningkite.services.files.ServerFile") 1f else -1f
    }

    override fun form(context: RenderContext<ServerFile?>, value: MutableReactive<ServerFile?>, module: FormModule): ViewWriter.() -> Unit = {
        row {
            expanding.externalLink {
                newTab = true
                ::enabled { value() != null }
                ::to { value()?.location }
                row {
                    sizeConstraints(width = 3.rem, height = 3.rem).card.unpadded.frame {
                        centered.icon(Icon.download, "File")
                        image {
                            ::source { value()?.location?.let(::ImageRemote) }
                        }
                    }
                    centered.expanding.text {
                        ellipsis = true
                        wraps = false
                        ::content {
                            value()?.location
                                ?.substringAfterLast('/')
                                ?.substringBefore('?')
                                ?.takeUnless { it.isBlank() }
                                ?: "None"
                        }
                    }
                }
            }
            centered.button {
                ::shown { module.fileUpload != null }
                icon(Icon.upload, "Upload")
                val rContext = this.context  // Capture RContext from Button (not the RenderContext parameter)
                onClick {
                    val fileUpload = module.fileUpload ?: return@onClick
                    rContext.requestFile()?.let { fileRef ->
                        value set fileUpload(fileRef)
                    }
                }
            }
        }
    }

    override fun view(context: RenderContext<ServerFile?>, value: Reactive<ServerFile?>, module: FormModule): ViewWriter.() -> Unit = {
        externalLink {
            newTab = true
            ::to { value()?.location ?: "" }
            row {
                sizeConstraints(width = 3.rem, height = 3.rem).card.unpadded.frame {
                    centered.icon(Icon.download, "File")
                    image {
                        ::source { value()?.location?.let(::ImageRemote) }
                    }
                }
                centered.expanding.text {
                    ellipsis = true
                    wraps = false
                    ::content {
                        value()?.location
                            ?.substringAfterLast('/')
                            ?.substringBefore('?')
                            ?: "None"
                    }
                }
            }
        }
    }

    override fun cellView(context: RenderContext<ServerFile?>, value: Reactive<ServerFile?>, module: FormModule): ViewWriter.() -> Unit = {
        text {
            ::content {
                value()?.location
                    ?.substringAfterLast('/')
                    ?.substringBefore('?')
                    ?: "—"
            }
        }
    }

    override fun columnWidth(context: RenderContext<ServerFile?>, module: FormModule): Double = 12.0
}

/**
 * Type info for foreign key resolution.
 *
 * Provides all the metadata needed to render and interact with foreign key references.
 *
 * @property serializer KSerializer for the referenced type
 * @property cache Function to access the ModelCache for this type
 * @property page Function to get a navigation page for viewing an instance by ID
 * @property renderToString Function to convert an ID to a display string
 *
 * by Claude
 */
public class TypeInfo<T : HasId<ID>, ID : Comparable<ID>>(
    public val serializer: KSerializer<T>,
    public val cache: () -> com.lightningkite.lightningserver.db.ModelCache<T, ID>,
    public val page: (ID) -> (() -> Page)?,
    public val renderToString: suspend (ID) -> String
)

public fun FormModule.registerServerFile() {
    register(Selector(type = "com.lightningkite.services.files.ServerFile"), ServerFileRenderer)
}
