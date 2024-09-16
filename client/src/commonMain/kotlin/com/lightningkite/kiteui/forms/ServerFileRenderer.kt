package com.lightningkite.kiteui.forms

import com.lightningkite.CaselessStringSerializer
import com.lightningkite.TrimmedCaselessStringSerializer
import com.lightningkite.TrimmedStringSerializer
import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.SortPart
import com.lightningkite.lightningdb.SortPartSerializer
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object ServerFileRenderer  : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "File"
    override val type: String = "com.lightningkite.lightningserver.files.ServerFile"

    var fileUpload: suspend (FileReference) -> ServerFile = { TODO("No file uploader set!") }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        return FormRenderer<ServerFile?>(module, this, selector as FormSelector<ServerFile?>) { field, writable ->
            row {
                expanding - externalLink {
                    ::to { writable()?.location ?: "" }
                    row {
                        sizeConstraints(width = 3.rem, height = 3.rem) - image {
                            ::source { writable()?.location?.let(::ImageRemote) }
                        }
                        centered - expanding - text {
                            ::content { writable()?.location?.substringAfterLast('/') ?: "None" }
                        }
                    }
                }
                centered - button {
                    icon(Icon.send, "Upload")
                    onClick {
                        ExternalServices.requestFile()?.let {
                            writable set fileUpload(it)
                        }
                    }
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        return ViewRenderer<ServerFile?>(module, this, selector as FormSelector<ServerFile?>) { field, readable ->
            externalLink {
                ::to { readable()?.location ?: "" }
                row {
                    sizeConstraints(width = 3.rem, height = 3.rem) - image {
                        ::source { readable()?.location?.let(::ImageRemote) }
                    }
                    centered - expanding - text {
                        ::content { readable()?.location?.substringAfterLast('/') ?: "None" }
                    }
                }
            }
        } as ViewRenderer<T>
    }
}