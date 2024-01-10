//
//  BrowserViewDataSource.swift
//  DwebWebBrowser
//
//  Created by instinct on 2024/1/4.
//

import Foundation

class BrowserWebSiteInfo: Identifiable, Hashable {
    
    let data: WebBrowserViewDataProtocol
    init(_ data: WebBrowserViewDataProtocol) {
        self.data = data
    }
    
    var id: Int64 {
        get {
            return data.id
        }
    }
    
    static func == (lhs: BrowserWebSiteInfo, rhs: BrowserWebSiteInfo) -> Bool {
        return lhs.data.title == rhs.data.title && lhs.data.url == rhs.data.url && lhs.data.id == rhs.data.id
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(data.title)
        hasher.combine(data.url)
     }
}
