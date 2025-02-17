import com.lightningkite.kotlinx.serialization.csv.CsvFormat
import com.lightningkite.kotlinx.serialization.csv.StringDeferringConfig
import com.lightningkite.lightningserver.db.DatabaseSettings
import com.lightningkite.lightningserver.demo.Server
import com.lightningkite.lightningserver.demo.TestModel
import com.lightningkite.lightningserver.engine.UnitTestEngine
import com.lightningkite.lightningserver.engine.engine
import com.lightningkite.lightningserver.jsonschema.lightningServerSchema
import com.lightningkite.lightningserver.serialization.Serialization
import com.lightningkite.lightningserver.settings.GeneralServerSettings
import com.lightningkite.lightningserver.settings.Settings
import com.lightningkite.lightningserver.settings.generalSettings
import com.lightningkite.lightningserver.tasks.Tasks
import com.lightningkite.lightningserver.typed.Documentable
import com.lightningkite.lightningserver.typed.typescriptSdk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.awt.SystemColor.text

object TestSettings {
    init {
        Server
        Settings.populateDefaults(mapOf(
            generalSettings.name to GeneralServerSettings(
                debug = true
            )
        ))
        engine = UnitTestEngine
    }
}

class ServerTest {
    @Test fun test() {
        TestSettings
        println(Json(Serialization.jsonWithoutDefaults){ prettyPrint = true }.encodeToString(lightningServerSchema))
    }

    @Test fun generateSdk(): Unit = runBlocking {
        TestSettings
        Tasks.onSettingsReady()
        Documentable.typescriptSdk(System.out)
    }

    @Test fun quickCheck() {
        TestSettings
        val csv = CsvFormat(StringDeferringConfig(Serialization.Internal.module, ignoreUnknownKeys = true))
        csv.decodeFromString(ListSerializer(TestModel.serializer()), """
            timestamp,name,number,content,file,replyTo,privateInfo,status,hiddenField
            2025-02-06T20:41:09.912133Z,Sample,42,Some content,null,null,some private info,DRAFT,FALSE
            2025-02-06T20:41:09.912133Z,Sample2,41,Sad content,null,null,some private info,PUBLISHED,FALSE
            2025-02-06T20:41:09.912133Z,Sample3,40,Happy content,null,null,some private info,DRAFT,TRUE
            2025-02-06T20:41:09.912133Z,Sample4,39,Something else content,null,null,some private info,PUBLISHED,TRUE
        """.trimIndent())
    }
}