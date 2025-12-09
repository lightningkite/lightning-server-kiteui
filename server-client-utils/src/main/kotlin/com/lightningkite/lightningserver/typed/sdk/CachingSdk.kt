package com.lightningkite.lightningserver.typed.sdk

import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.sdk.SDK.processToModules
import com.lightningkite.lightningserver.typed.sdk.SDK.sdk
import com.lightningkite.services.data.KFile

public class CachingSdk(
    public val packageName: String,
    public val rootInfo: SdkModule.Info = SdkModule.Info("Api"),
    public val filename: String = "Cached${rootInfo.interfaceName}.kt"
) : SDK.Format {
    context(server: ServerRuntime)
    override fun write(folder: KFile) {
        val processed = server.server.sdk(rootInfo).processToModules().ensureUniqueNames()

        folder.then(filename).overwrite { writeCache(processed, packageName) }
    }

    context(_: ServerRuntime)
    private fun Appendable.writeCache(data: SDK.Module, packageName: String) {
        appendLine("package $packageName")

        data.appendImports(
            "com.lightningkite.lightningserver.db.*",
            "kotlinx.serialization.builtins.*",
        )

        appendLine("open class Cached${data.info.interfaceName}(val uncached: ${data.info.interfaceName}) {")

        fun SDK.Module.appendCaches(chain: List<SDK.Module>) {
            extendsInterfaces
                .asSequence()
                .map { it.item }
                .firstOrNull { it.type == ClientModelRestEndpoints::class }
                ?.let { interfaceInfo ->
                    val typeName = interfaceInfo.typeParameters
                        .first()
                        .descriptor
                        .serialName
                        .substringAfterLast('.')
                        .camelCase()
                        .pluralize()

                    appendLine("\topen val $typeName = ModelCache(uncached.${(chain + this).drop(1).joinToString(".") { it.info.valueName }}, ${interfaceInfo.typeParameters[0].kotlinSerializer()})")
                }

            for (child in children) child.appendCaches(chain + this)
        }

        data.appendCaches(emptyList())

        appendLine("}")
    }

    context(buffer: Appendable)
    private fun SDK.Module.appendImports(
        vararg imports: String
    ) {
        fun SDK.Module.imports(): List<String> =
            extendsInterfaces.flatMap { it.item.imports } + children.flatMap { it.imports() }

        (imports.toList() + imports())
            .distinct()
            .joinTo(buffer, "\n", prefix = "\n", postfix = "\n\n") { "import $it" }
    }

    private fun KFile.overwrite(action: Appendable.() -> Unit) {
        parent?.createDirectories()
        sink().useAsAppendable(action)
    }

    private fun String.pluralize() = when {
        endsWith("lf") -> this.removeSuffix("lf") + "lves"
        endsWith('s') -> this + "es"
        endsWith('y') -> this.removeSuffix("y") + "ies"
        else -> this + "s"
    }
}