//
//  AppDelegate.swift
//  Created by Joseph Ivie on 1/3/24.
//

import UIKit
import apps

@main
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate  {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Override point for customization after application launch.
        DataKt.remMultiplier = 1.23
        let newWindow = UIWindow()
        newWindow.rootViewController = ViewController()
        window = newWindow
        newWindow.makeKeyAndVisible()
        return true
    }
}


