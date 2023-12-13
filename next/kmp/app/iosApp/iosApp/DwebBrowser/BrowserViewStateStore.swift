//
//  BrowserViewStateStore.swift
//  iosApp
//
//  Created by instinct on 2023/12/13.
//  Copyright © 2023 orgName. All rights reserved.
//

import Foundation
import SwiftUI

class BrowserViewStateStore: ObservableObject {
    
    static let shared = BrowserViewStateStore()
    
    @Published var selectedTab = SelectedTab()
    @Published var addressBar = AddressBarState()
    @Published var openingLink = OpeningLink()
    @Published var toolBarState = ToolBarState()
    @Published var webcacheStore = WebCacheStore()
    @Published var dragScale = WndDragScale()
    @Published var wndArea = BrowserArea()
    @Published var searchKey: String? = nil
    @Published var colorScheme = ColorScheme.light
    
    func clear() {
        selectedTab = SelectedTab()
        addressBar = AddressBarState()
        openingLink = OpeningLink()
        toolBarState = ToolBarState()
        webcacheStore = WebCacheStore()
        dragScale = WndDragScale()
        wndArea = BrowserArea()
    }
}

//这个地方暴露BrowserView的行为给外部使用
extension BrowserViewStateStore {
    func doBackIfCan() -> Bool {
        guard webcacheStore.caches.count > 0 else { return false }
        let shouldShowWeb = webcacheStore.cache(at: selectedTab.curIndex).shouldShowWeb
        guard shouldShowWeb else { return false }
        let webwrapper = webcacheStore.webWrappers[selectedTab.curIndex]
        if webwrapper.webView.canGoBack {
            webwrapper.webView.goBack()
            return true
        }
        return false
    }
    
    func doSearch(_ key: String?) {
        guard let key = key, !key.isEmpty, searchKey != key else {
            return
        }
        searchKey = key
    }
}