//
//  ViewController.swift
//  Hammer Price
//
//  Created by Joseph Ivie on 1/3/24.
//

import UIKit
import apps

class ViewController: UIViewController {

    override func viewDidLoad() {
        print("HIT")
        super.viewDidLoad()
        App_iosKt.root(viewController: self)
    }

}

