package kspcommon

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.lightningkite.services.database.processor.MyProvider
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk

/**
 * Runs service-abstractions' database-processor over [commonSrcDir] using the KSP2 standalone API,
 * writing Kotlin output into [outputDir]. The plugin declares [outputDir] as a generated source
 * directory with no fragment modifier, which puts it in the module's COMMON fragment -- the only
 * place the processor's `@JvmName`-bearing output compiles, and the only place common source can
 * see it. The Toolchain's built-in KSP wires output per-platform and so can do neither.
 *
 * [commonSrcDir] is the module's `src` or `test` directory, which also decides the flavor. That is
 * not cosmetic: the processor infers whether it is generating for main or test sources by splitting
 * its own output path on a segment literally named `ksp` and reading two segments further along, so
 * the scratch directory below is shaped to match Gradle's `build/generated/ksp/common/<flavor>/`.
 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun runKspOverCommon(
    @Input commonSrcDir: Path,
    @Input classpath: Classpath,
    @Output outputDir: Path,
) {
    // Only Path-typed parameters may be @Input, so the flavor rides in on the source directory:
    // `src` is the module's common main fragment, `test` its common test fragment.
    val flavor = when (val dir = commonSrcDir.name) {
        "src" -> "commonMain"
        "test" -> "commonTest"
        else -> error("expected the module's src or test directory, got $dir")
    }
    // Nothing to process, and the processor throws on an empty source set rather than no-opping.
    if (!commonSrcDir.isDirectory() || commonSrcDir.walk().none { it.extension == "kt" }) {
        outputDir.createDirectories()
        return
    }

    outputDir.deleteRecursively()
    outputDir.createDirectories()
    val scratch = outputDir.parent.resolve("ksp/common/$flavor")

    val config = KSPJvmConfig.Builder().apply {
        javaSourceRoots = emptyList()
        javaOutputDir = scratch.resolve("java").createDirectories().toFile()
        jvmTarget = "17"
        moduleName = flavor
        sourceRoots = listOf(commonSrcDir.toFile())
        commonSourceRoots = listOf(commonSrcDir.toFile())
        // KSPCommonConfig would be the obvious choice, but under it these entries are silently
        // dropped and every type from a dependency fails to resolve.
        libraries = classpath.resolvedFiles.map { it.toFile() }
        projectBaseDir = commonSrcDir.parent.toFile()
        outputBaseDir = scratch.toFile()
        cachesDir = scratch.resolve("caches").createDirectories().toFile()
        kotlinOutputDir = outputDir.toFile()
        classOutputDir = scratch.resolve("classes").createDirectories().toFile()
        // The processor reads the first file it creates -- always one of its .txt side-outputs --
        // to work out the flavor, so this is the path that has to carry the `ksp/common/<flavor>`
        // segments.
        resourceOutputDir = scratch.createDirectories().toFile()
        incremental = false
        languageVersion = "2.2"
        apiVersion = "2.2"
    }.build()

    val logger = object : KSPLogger {
        override fun logging(message: String, symbol: KSNode?) {}
        override fun info(message: String, symbol: KSNode?) {}
        override fun warn(message: String, symbol: KSNode?) = println("[ksp] w: $message")
        override fun error(message: String, symbol: KSNode?) = println("[ksp] e: $message")
        override fun exception(e: Throwable) = e.printStackTrace()
    }

    val exit = KotlinSymbolProcessing(config, listOf(MyProvider()), logger).execute()
    if (exit != KotlinSymbolProcessing.ExitCode.OK) error("KSP over common sources failed: $exit")
    println("KSP over $flavor wrote ${outputDir.walk().count { it.extension == "kt" }} file(s)")
}
