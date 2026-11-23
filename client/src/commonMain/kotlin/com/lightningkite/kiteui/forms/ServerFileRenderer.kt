package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageLocal
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
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

    override fun form(context: RenderContext<ServerFile?>, value: MutableReactive<ServerFile?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            val localFile = Signal<FileReference?>(null)
            centered.expanding.text {
                ::shown { value().url() == null }
                content = "No file"
            }
            expanding.externalLink {
                newTab = true
                ::shown { value().url() != null }
                ::to { value().url() }
                row {
                    sizeConstraints(width = 3.rem, height = 3.rem).card.unpadded.frame {
                        centered.icon(Icon.download, "File")
                        image {
                            ::source { localFile()?.let(::ImageLocal) ?: value().url()?.let(::ImageRemote) }
                        }
                    }
                    centered.expanding.text {
                        ellipsis = true
                        wraps = false
                        ::content { value().fileName() ?: "" }
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
                        localFile.set(fileRef)
                        value set fileUpload(fileRef)
                    }
                }
            }
            centered.button {
                ::shown { context.serializer.descriptor.isNullable && value() != null }
                icon(Icon.close, "Remove")
                onClick {
                    localFile.set(null)
                    value set null
                }
            }
        }
    }

    override fun view(context: RenderContext<ServerFile?>, value: Reactive<ServerFile?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        frame {
            text {
                ::shown { value().url() == null }
                content = "No file"
            }
            externalLink {
                newTab = true
                ::shown { value().url() != null }
                ::to { value().url() ?: "" }
                row {
                    sizeConstraints(width = 3.rem, height = 3.rem).card.unpadded.frame {
                        centered.icon(Icon.download, "File")
                        image {
                            ::source { value().url()?.let(::ImageRemote) }
                        }
                    }
                    centered.expanding.text {
                        ellipsis = true
                        wraps = false
                        ::content { value().fileName() ?: "" }
                    }
                }
            }
        }
    }

    override fun cellView(context: RenderContext<ServerFile?>, value: Reactive<ServerFile?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { value().fileName() ?: "—" } }
    }

    override fun columnWidth(context: RenderContext<ServerFile?>, module: FormModule): Double = 12.0

    // Claude: a blank location (the default for a new non-null ServerFile) means no file; loading it as an image spins forever.
    private fun ServerFile?.url(): String? = this?.location?.takeUnless { it.isBlank() }

    private fun ServerFile?.fileName(): String? = url()?.substringAfterLast('/')?.substringBefore('?')
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
    register(Selector(type = "com.lightningkite.services.files.ServerFile?"), ServerFileRenderer)
}
