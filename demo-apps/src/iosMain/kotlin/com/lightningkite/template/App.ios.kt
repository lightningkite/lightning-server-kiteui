package com.lightningkite.template

import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.direct.TextInput
import com.lightningkite.kiteui.views.setup
import platform.UIKit.UIViewController


fun root(viewController: UIViewController) {
    viewController.setup(appTheme) { app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes }) }
}
