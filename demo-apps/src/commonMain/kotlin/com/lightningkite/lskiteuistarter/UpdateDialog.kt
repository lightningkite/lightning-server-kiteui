package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.toast


@Suppress("DEPRECATION")
class UpdateDialog(
    val newVersion: String,
    val forceUpdate: Boolean,
) : Page {
    override fun ElementWriter.CanAddTheme.render() {
        dismissBackground {
            onClick {
                if (!forceUpdate)
                    context.pageNavigator.dismiss()
            }
            centered.themed(DialogSemantic).frame {
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
                                onClick {
                                    context.dialogPageNavigator.dismiss()
                                }
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
}