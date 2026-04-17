package com.lightningkite.lightningserver.typed.sdk

import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.sdk.SDK.processToModules
import com.lightningkite.lightningserver.typed.sdk.SDK.sdk
import com.lightningkite.services.data.ExperimentalLightningServer
import kotlinx.serialization.KSerializer
import kotlin.reflect.full.isSubclassOf

@OptIn(ExperimentalLightningServer::class)
public class CachingSdk(
    public val packageName: String,
    public val rootInfo: SdkModule.Info = SdkModule.Info("Api"),
    public val filename: String = "Cached${rootInfo.interfaceName}.kt",
    public val namingScheme: NamingScheme = NamingScheme.SerialName()
) : SDK.Format {
    public fun interface NamingScheme {
        public fun getParameterName(path: List<SDK.Module>, serializer: KSerializer<*>): String

        public class SerialName(public val qualifiedNames: Boolean = true) : NamingScheme {
            private val usedNames = mutableSetOf<String>()

            override fun getParameterName(path: List<SDK.Module>, serializer: KSerializer<*>): String = serializer
                .descriptor
                .serialName
                .let { serialName ->
                    if (qualifiedNames) serialName
                        .split('.')
                        .takeLastWhile { it.firstOrNull()?.isUpperCase() == true }
                        .joinToPluralized("")
                    else serialName
                        .substringAfterLast('.')
                        .pluralize()
                }
                .camelCase()
                .let {
                    if (usedNames.add(it)) return@let it

                    var updated = it
                    var count = 1
                    while (!usedNames.add(updated)) updated = it + (++count)
                    updated
                }
        }

        public class SdkPath : NamingScheme {
            override fun getParameterName(path: List<SDK.Module>, serializer: KSerializer<*>): String = path
                .joinToPluralized("") { it.info.valueName }
                .camelCase()
        }
    }

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

        fun SDK.Module.appendCaches(chain: List<SDK.Module>) {
            extendsInterfaces
                .asSequence()
                .map { it.item }
                .firstOrNull { it.type.isSubclassOf(ClientModelRestEndpoints::class) }
                ?.let { interfaceInfo ->
                    val typeName = namingScheme.getParameterName(
                        chain + this,
                        interfaceInfo.typeParameters.firstOrNull()
                            ?: throw IllegalArgumentException("${info.interfaceName} inherits ClientModelRestEndpoints but does not have a type parameter serializer")
                    )

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

internal inline fun <T> List<T>.joinToPluralized(separator: CharSequence, crossinline transform: (T) -> String = { it.toString() }): String = this
    .withIndex()
    .joinToString(separator) { (idx, value) ->
        if (idx == lastIndex) transform(value).pluralize().pascalCase()
        else transform(value).pascalCase()
    }
