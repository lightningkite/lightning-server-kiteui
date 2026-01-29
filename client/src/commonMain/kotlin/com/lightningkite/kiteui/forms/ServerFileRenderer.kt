package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.files.ServerFile

object ServerFileRenderer  : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "File"
    override val type: String = "com.lightningkite.services.files.ServerFile"

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        return FormRenderer<ServerFile?>(module, this, selector as FormSelector<ServerFile?>) { field, mutable ->
            row {
                expanding.externalLink {
                    newTab = true
                    ::enabled { mutable() != null }
                    ::to { mutable()?.location }
                    row {
                        sizeConstraints(width = 3.rem, height = 3.rem).image {
                            ::source { mutable()?.location?.let(::ImageRemote) }
                        }
                        centered.expanding.text {
                            ellipsis = true
                            wraps = false
                            ::content { mutable()?.location?.substringAfterLast('/')?.substringBefore('?')?.takeUnless { it.isBlank() } ?: "None" }
                        }
                    }
                }
                centered.button {
                    ::shown { module.fileUpload != null }
                    icon(Icon.upload, "Upload")
                    onClick {
                        context.requestFile()?.let {
                            mutable set module.fileUpload!!.invoke(it)
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
                newTab = true
                ::to { readable()?.location ?: "" }
                row {
                    sizeConstraints(width = 3.rem, height = 3.rem).image {
                        ::source { readable()?.location?.let(::ImageRemote) }
                    }
                    centered.expanding.text {
                        ellipsis = true
                        wraps = false
                        ::content { readable()?.location?.substringAfterLast('/')?.substringBefore('?') ?: "None" }
                    }
                }
            }
        } as ViewRenderer<T>
    }
}