package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.direct.TextInput
import com.lightningkite.kiteui.views.setup
import com.lightningkite.lightningserver.*
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.services.data.*
import com.lightningkite.services.database.*
import com.lightningkite.services.files.*
import kotlin.uuid.Uuid
import platform.UIKit.UIViewController


fun root(viewController: UIViewController) {
    viewController.setup(appTheme) { app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes }) }
}
