package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.toast

/**
 * Shows a modal prompting the user to update the app. Non-dismissable when [forceUpdate] is set.
 */
fun ElementContext.updateDialog(newVersion: String, forceUpdate: Boolean) {
    dialog(dismissable = !forceUpdate) { close ->
        frame {
            col {
                h1 {
                    align = Align.Center
                    content = "New App Version Available"
                }

                centered.sizeConstraints(maxWidth = 40.rem, minWidth = 10.rem).padded.text {
                    align = Align.Center
                    content =
                        if (forceUpdate)
                            "There is a new version available to download from the store ($newVersion). You must update to the most recent version of the app before you can continue."
                        else
                            "There is a new version available to download from the store ($newVersion). Download it to stay up to date with the most recent features and bug fixes."
                }

                row {
                    if (!forceUpdate)
                        expanding.buttonTheme.button {
                            centered.text("OK")
                            onClick { close() }
                        }

                    expanding.buttonTheme.button {
                        centered.text("Go To Store")
                        onClick {
                            context.toast("Replace toast with store url")
//                                ExternalServices.openTab("")
                        }
                    }
                }
            }
        }
    }
}
