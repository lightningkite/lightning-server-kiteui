package com.lightningkite.lightningserver.demo

import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.auth.old.*
import com.lightningkite.lightningserver.auth.old.BaseAuthEndpoints
import com.lightningkite.lightningserver.auth.old.EmailAuthEndpoints
import com.lightningkite.lightningserver.auth.old.PasswordAuthEndpoints
import com.lightningkite.lightningserver.auth.proof.*
import com.lightningkite.lightningserver.auth.proof.PinHandler
import com.lightningkite.lightningserver.auth.subject.AuthEndpointsForSubject
import com.lightningkite.lightningserver.cache.CacheSettings
import com.lightningkite.lightningserver.cache.MemcachedCache
import com.lightningkite.lightningserver.cache.get
import com.lightningkite.lightningserver.client
import com.lightningkite.lightningserver.core.ServerPath
import com.lightningkite.lightningserver.core.ServerPathGroup
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.email.Email
import com.lightningkite.lightningserver.email.EmailLabeledValue
import com.lightningkite.lightningserver.email.EmailSettings
import com.lightningkite.lightningserver.encryption.secureHash
import com.lightningkite.lightningserver.exceptions.NotFoundException
import com.lightningkite.lightningserver.exceptions.SentryExceptionReporter
import com.lightningkite.lightningserver.files.FilesSettings
import com.lightningkite.lightningserver.files.S3FileSystem
import com.lightningkite.lightningserver.files.UploadEarlyEndpoint
import com.lightningkite.lightningserver.http.HttpResponse
import com.lightningkite.lightningserver.http.get
import com.lightningkite.lightningserver.http.handler
import com.lightningkite.lightningserver.meta.metaEndpoints
import com.lightningkite.lightningserver.metrics.Metrics
import com.lightningkite.lightningserver.schedule.schedule
import com.lightningkite.lightningserver.serialization.FileRedirectHandler
import com.lightningkite.lightningserver.serialization.Serialization
import com.lightningkite.lightningserver.settings.setting
import com.lightningkite.lightningserver.sms.SMSSettings
import com.lightningkite.lightningserver.tasks.Tasks
import com.lightningkite.lightningserver.tasks.startupOnce
import com.lightningkite.lightningserver.tasks.task
import com.lightningkite.lightningserver.typed.ApiExample
import com.lightningkite.lightningserver.typed.typed
import com.lightningkite.lightningserver.websocket.MultiplexWebSocketHandler
import com.lightningkite.lightningserver.websocket.websocket
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import java.lang.IllegalStateException
import kotlin.time.Duration
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import com.lightningkite.UUID
import com.lightningkite.lightningserver.files.S3File
import com.lightningkite.lightningserver.meta.MetaEndpoints
import com.lightningkite.lightningserver.typed.Documentable
import com.lightningkite.prepareModelsServerCore
import com.lightningkite.prepareModelsShared
import com.lightningkite.uuid

object Server : ServerPathGroup(ServerPath.root) {

    val database = setting("database", DatabaseSettings())
    val email = setting("email", EmailSettings())
    val jwtSigner = setting("jwt", JwtSigner())
    val sms = setting("sms", SMSSettings())
    val files = setting("files", FilesSettings())
    val cache = setting("cache", CacheSettings())

    init {
        Metrics
        DynamoDbCache
        MongoDatabase
        MemcachedCache
        SentryExceptionReporter
        S3FileSystem
        prepareModelsShared()
        prepareModelsServerCore()
        prepareModelsDemoServer()
        Tasks.onSettingsReady {
            Metrics.main()
            println("Files started, got ${files().root.url}")
        }
        Serialization.handler(FileRedirectHandler)
        startupOnce("adminUser", database) {
            database().collection<User>().insertOne(
                User(
                    email = "joseph+admin@lightningkite.com",
                    isSuperUser = true
                )
            )
        }
        Authentication.isDeveloper = authRequired<User> {
            (it.get() as User).isSuperUser
        }
        Authentication.isSuperUser = authRequired<User> {
            (it.get() as User).isSuperUser
        }
    }

    val userInfo = modelInfoWithDefault<User, User, UUID>(
        authOptions = authOptions(),
        serialization = ModelSerializationInfo(),
        getBaseCollection = { database().collection() },
        defaultItem = { User(email = "") },
        forUser = { it ->
            val user = user()
            val everyone: Condition<User> = Condition.Always()
            val self: Condition<User> = condition { it._id eq user._id }
            val admin: Condition<User> = if (user.isSuperUser) Condition.Always() else Condition.Never()
            it.withPermissions(
                ModelPermissions(
                    create = everyone,
                    read = self or admin,
                    update = self or admin,
                    delete = self or admin
                )
            )
        }
    )
    object Users: ServerPathGroup(path("user")) {
        val rest = ModelRestEndpoints(path("rest"), userInfo)
    }

    val uploadEarly = UploadEarlyEndpoint(path("upload"), files, database)
    val testModel = TestModelEndpoints(path("test-model"))

    val pins = PinHandler(cache, "pins")
    val proofPhone = SmsProofEndpoints(path("proof/phone"), pins, sms)
    val proofEmail = EmailProofEndpoints(path("proof/email"), pins, email, { to, pin -> // TODO: Bug with PIN endpoints, not registering interface info. Maybe because the interface info is initialized in the abstract class after the endpoint is already created?
        Email(
            subject = "Log In Code",
            to = listOf(EmailLabeledValue(to)),
            plainText = "Your PIN is $pin."
        )
    })
    val proofOtp = OneTimePasswordProofEndpoints(path("proof/otp"), database, cache)
    val proofPassword = PasswordProofEndpoints(path("proof/password"), database, cache)
    val subjects = AuthEndpointsForSubject(
        path("subject"),
        object : Authentication.SubjectHandler<User, UUID> {
            override val name: String get() = "User"
            override val authType: AuthType get() = AuthType<User>()
            override val idSerializer: KSerializer<UUID>
                get() = userInfo.serialization.idSerializer
            override val subjectSerializer: KSerializer<User>
                get() = userInfo.serialization.serializer

            override suspend fun fetch(id: UUID): User = userInfo.collection().get(id) ?: throw NotFoundException()
            override suspend fun findUser(property: String, value: String): User? = when (property) {
                "email" -> userInfo.collection().findOne(condition { it.email eq value }) ?: userInfo.collection().insertOne(User(email = value))
                "_id" -> userInfo.collection().get(uuid(value))
                else -> null
            }

            override val knownCacheTypes: List<RequestAuth.CacheKey<User, UUID, *>> = listOf(EmailCacheKey)

            override suspend fun desiredStrengthFor(result: User): Int = if (result.isSuperUser) Int.MAX_VALUE else 5
        },
        database = database
    )

    val meta = MetaEndpoints(path("meta"))

    init {
        startupOnce("default-data", database) {
            val user = userInfo.collection().insertOne(
                User(email = "hunter@lightningkite.com")
            ) ?: throw Exception("User not inserted")

            proofPassword.establish(subjects.handler, user._id, EstablishPassword("password"))
        }
    }

    val interfaces = path("interfaces").get.handler {
        HttpResponse.json(Documentable.interfaces.map { it.name to it.path.toString() }.toList())
    }
}

object EmailCacheKey : RequestAuth.CacheKey<User, UUID, String>() {
    override val name: String
        get() = "email"
    override val serializer: KSerializer<String>
        get() = String.serializer()
    override val validFor: Duration
        get() = 5.minutes

    override suspend fun calculate(auth: RequestAuth<User>): String = auth.get().email
}