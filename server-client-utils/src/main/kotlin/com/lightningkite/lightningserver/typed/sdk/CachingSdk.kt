package com.lightningkite.lightningserver.typed.sdk

import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.sdk.SDK.processToModules
import com.lightningkite.lightningserver.typed.sdk.SDK.sdk
import com.lightningkite.services.data.ExperimentalLightningServer
import kotlin.reflect.full.isSubclassOf

@OptIn(ExperimentalLightningServer::class)
public class CachingSdk(
    public val packageName: String,
    public val rootInfo: SdkModule.Info = SdkModule.Info("Api"),
    public val filename: String = "Cached${rootInfo.interfaceName}.kt"
) : SDK.Format {
    context(server: ServerRuntime)
    override fun write(archive: Archive) {
        val processed = server.server.sdk(rootInfo).processToModules().ensureUniqueNames()

        archive.appendableEntry(filename) { writeCache(processed, packageName) }
    }

    context(_: ServerRuntime)
    private fun Appendable.writeCache(data: SDK.Module, packageName: String) {
        appendLine("package $packageName")

        data.appendImports(
            "com.lightningkite.lightningserver.db.*",
            "kotlinx.serialization.builtins.*",
        )

        appendLine("open class Cached${data.info.interfaceName}(val uncached: ${data.info.interfaceName}) {")

        val usedNames = mutableSetOf<String>()

        fun SDK.Module.appendCaches(chain: List<SDK.Module>) {
            extendsInterfaces
                .asSequence()
                .map { it.item }
                .firstOrNull { it.type.isSubclassOf(ClientModelRestEndpoints::class) }
                ?.let { interfaceInfo ->
                    val typeName = interfaceInfo.typeParameters
                        .first()
                        .descriptor
                        .serialName
                        .substringAfterLast('.')
                        .camelCase()
                        .pluralize()
                        .let {
                            if (it !in usedNames) {
                                usedNames += it
                                return@let it
                            }

                            val appended = it.plus(this.info.valueName)
                            var updated = appended
                            var count = 1
                            while (updated in usedNames) updated = appended + (++count)

                            usedNames += updated
                            updated
                        }

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
}

private val pluralizeRules = listOf(
    // Irregular/Static mappings
    "person" to "people",
    "child" to "children",
    "mouse" to "mice",
    "tooth" to "teeth",
    "goose" to "geese",

    // Regex rules (Pattern to Replacement)
    "(quiz)$" to "$1zes",
    "^(ox)$" to "$1en",
    "([m|l])ouse$" to "$1ice",
    "(matr|vert|ind)ix|ex$" to "$1ices",
    "(x|ch|ss|sh)$" to "$1es",
    "([^aeiouy]|qu)y$" to "$1ies",
    "(hive)$" to "$1s",
    "(?:([^f])fe|([lr])f)$" to "$1$2ves",
    "sis$" to "ses",
    "([ti])um$" to "$1a",
    "(buffal|tomat)o$" to "$1oes",
    "(bu)s$" to "$1ses",
    "(alias|status)$" to "$1es",
    "(octop|vir)us$" to "$1i",
    "(ax|test)is$" to "$1es",
    "s$" to "s"
).map { (pattern, replacement) -> Regex(pattern, RegexOption.IGNORE_CASE) to replacement }

internal fun String.pluralize(): String {
    val word = this
    if (word.isBlank()) return word

    // 1. Check for exact matches in our rules first (for things like "person")
    // 2. Otherwise, apply Regex rules
    for ((regex, replacement) in pluralizeRules) {
        if (regex.containsMatchIn(word)) {
            return word.replace(regex, replacement)
        }
    }

    // Default: just add 's'
    return "${word}s"
}