// Document is the forms-engine demo's richest fixture: a Set<String>, a Map<String, String>, a
// @References(User::class) foreign key (ForeignKeyRenderer), a nullable ServerFile and a
// List<ServerFile> (ServerFileRenderer, driven by a real upload through UploadEarlyEndpoint), and
// a plain Instant. The edit page also renders the attachment with ServerFile.asImage() directly,
// outside the renderer, to show that helper being used the way a normal screen would.
package com.lightningkite.lskiteuistarter.forms

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.forms.ColumnInfo
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.defaultColumns
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.renderTable
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningserver.files.asImage
import com.lightningkite.lskiteuistarter.Document
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.Query
import kotlin.uuid.Uuid

private class DocumentTableContext(val module: FormModule, val columns: Signal<List<ColumnInfo<Document>>>)

@Routable("/forms/documents")
class DocumentListPage : Page {
    override val title: Reactive<String> get() = Constant("Documents")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val ctx = rememberSuspending {
            val module = session().formModule()
            DocumentTableContext(module, Signal(Document.serializer().defaultColumns().map { ColumnInfo(it, module) }))
        }
        val documents = remember { session().documents.list(Query(Condition.Always)) }

        val elementContext = this.context
        val createNew = Action("New Document") {
            val owner = session().userId
            val created = session().documents.add(Document(title = "New Document", owner = owner))
            elementContext.pageNavigator.navigate(DocumentEditPage(created._id.toString()))
        }

        col {
            row {
                expanding.col { h2("Documents") }
                button {
                    text("New Document")
                    action = createNew
                }
            }
            subtext("Set<String> tags, Map<String, String> metadata, a @References owner, and file uploads, all through the forms engine and a live ModelCache-backed table.")

            expanding.frame {
                swapping(current = { ctx() }) { c ->
                    sizeConstraints(height = 30.rem).renderTable(
                        module = c.module,
                        innerSerializer = Document.serializer(),
                        items = documents,
                        columns = c.columns,
                        linkTo = { doc -> { DocumentEditPage(doc._id.toString()) } },
                    )
                }
            }
        }
    }
}

@Routable("/forms/documents/{id}")
class DocumentEditPage(val id: String) : Page {
    override val title: Reactive<String> get() = Constant("Edit Document")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val documentId = Uuid.parse(id)
        val module = rememberSuspending { session().formModule() }
        val item = remember { session().documents.item(documentId) }

        var seeded = false
        val draft = Signal(Document(_id = documentId))
        reactive {
            val loaded = item()() ?: return@reactive
            if (!seeded) {
                seeded = true
                draft.value = loaded
            }
        }

        val elementContext = this.context
        val save = Action("Save") {
            val old = item()() ?: throw PlainTextException("Document no longer exists", "Not found")
            val diff = modification(old, draft()) ?: return@Action
            item().modify(diff)
            elementContext.toast("Saved")
        }
        val delete = Action("Delete") {
            item().delete()
            elementContext.toast("Deleted")
            elementContext.pageNavigator.navigate(DocumentListPage())
        }

        scrolling.col {
            h2("Edit Document")
            shownWhen { item()() == null }.text("Loading...")
            shownWhen { item()() != null }.col {
                // Direct use of ServerFile.asImage(), outside of ServerFileRenderer, the way an
                // ordinary detail screen would show an already-uploaded attachment.
                shownWhen { draft().attachment != null }.sizeConstraints(width = 8.rem, height = 8.rem).card.frame {
                    image { ::source { draft().attachment?.asImage() } }
                }

                swapping(current = { module() }) { m ->
                    card.col { form(m, draft) }
                }
                row {
                    important.button { text("Save"); action = save }
                    danger.button { text("Delete"); action = delete }
                }
            }
        }
    }
}
