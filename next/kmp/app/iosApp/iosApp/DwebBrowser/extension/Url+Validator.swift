//
//  Url+Validator.swift
//  DwebBrowser
//
//  Created by ui06 on 5/15/23.
//

import Foundation
import UIKit

extension URL {
    static func createUrl(_ urlString: String) -> URL {
        if var url = URL(string: urlString), urlString.isURL() || url.canBeOpen {
            if url.scheme == nil {
                url = URL(string: "https://" + urlString)!
            }
            return url
        } else {
            let searchString = "https://www.baidu.com/s?wd=\(urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            return URL(string: searchString)!
        }
    }

    var canBeOpen: Bool {
        // 特殊处理dweb和about这两种schema, 用于打开内部页面
        if self.scheme == "dweb" || self.scheme == "about" {
            return true
        }
        return UIApplication.shared.canOpenURL(self)
    }
}

extension URL {
    static var defaultSnapshotURL: URL {
         imageUrl(with: "snapshot")!
    }

    static var defaultWebIconURL: URL {
        imageUrl(with: "defWebIcon")!
    }
    
    static func imageUrl(with name: String) -> URL? {
        if let resourceBundlePath = Bundle.main.path(forResource: "resource", ofType: "bundle"),
           let resourceBundle = Bundle(path: resourceBundlePath) {
            return resourceBundle.url(forResource: name, withExtension: "png") 
        }
        return nil
    }

    func getDomain() -> String {
        guard var domain = self.host else { return self.absoluteString }
        if domain.lowercased().hasPrefix("www.") {
            domain.removeFirst(4)
        }
        return domain
    }
}
