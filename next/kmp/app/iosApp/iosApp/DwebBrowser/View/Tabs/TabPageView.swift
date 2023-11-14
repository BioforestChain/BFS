//
//  TabPageView.swift
//  TableviewDemo
//
//  Created by ui06 on 4/16/23.
//

import Combine
import SwiftUI

struct TabPageView: View {
    @EnvironmentObject var animation: ShiftAnimation
    @EnvironmentObject var toolbarState: ToolBarState
    @EnvironmentObject var selectedTab: SelectedTab
    @EnvironmentObject var openingLink: OpeningLink
    @EnvironmentObject var addressBar: AddressBarState
    @EnvironmentObject var webcacheStore: WebCacheStore
    @EnvironmentObject var dragScale: WndDragScale
    @EnvironmentObject var browerArea: BrowserArea

    var tabIndex: Int { webcacheStore.index(of: webCache)! }
    var webCache: WebCache
    @ObservedObject var webWrapper: WebWrapper

    @State private var snapshotHeight: CGFloat = 0
    private var isVisible: Bool { tabIndex == selectedTab.curIndex }
    var body: some View {
        GeometryReader { geo in
            ZStack {
                if webCache.shouldShowWeb {
                    webComponent
                }

                if !webCache.shouldShowWeb {
                    Color.bkColor.overlay {
                        HomePageView()
                    }
                }
            }
            .onChange(of: openingLink.clickedLink) { _, link in
                guard link != emptyURL else { return }

                print("clickedLink has changed: \(link)")
                let webcache = webcacheStore.cache(at: selectedTab.curIndex)
                webcache.lastVisitedUrl = link
                if webcache.shouldShowWeb {
                    webWrapper.webView.load(URLRequest(url: link))
                } else {
                    webcache.shouldShowWeb = true
                }
                openingLink.clickedLink = emptyURL
            }
            .onAppear {
                print("tabPage rect: \(geo.frame(in: .global))")
                snapshotHeight = geo.frame(in: .global).height
            }
            .onChange(of: toolbarState.goForwardTapped) { _, tapped in
                if tapped {
                    goForward()
                    toolbarState.goForwardTapped = false
                }
            }
            .onChange(of: toolbarState.goBackTapped) { _, tapped in
                if tapped {
                    goBack()
                    toolbarState.goBackTapped = false
                }
            }

            .onChange(of: toolbarState.shouldExpand) { _, shouldExpand in
                if isVisible, !shouldExpand { // 截图，为缩小动画做准备
                    var snapshot: UIImage? = nil
                    if webCache.shouldShowWeb {
                        webWrapper.webView.scrollView.showsVerticalScrollIndicator = false
                        webWrapper.webView.scrollView.showsHorizontalScrollIndicator = false
                        webWrapper.webView.takeSnapshot(with: nil) { image, error in
                            webWrapper.webView.scrollView.showsVerticalScrollIndicator = true
                            webWrapper.webView.scrollView.showsHorizontalScrollIndicator = true
                            if image != nil {
                                snapshot = image
                                webCache.snapshotUrl = UIImage.createLocalUrl(withImage: image!, imageName: webCache.id.uuidString)
                            }
                        }
                    }
                    animation.snapshotImage = snapshot ?? UIImage.snapshotImage(from: .defaultSnapshotURL)
                    // obtainedCellFrame 和 obtainedSnapshot 这两个步骤并行开始，谁先完成不确定
                    animation.progress = animation.progress == .obtainedCellFrame ? .startShrinking : .obtainedSnapshot
                }
            }
        }
    }

    var webComponent: some View {
        WebView(webView: webWrapper.webView)
            .onAppear {
                if webWrapper.estimatedProgress < 0.001 {
                    webWrapper.webView.load(URLRequest(url: webCache.lastVisitedUrl))
                }
                print("onappear progress:\(webWrapper.webView.estimatedProgress)")
            }

            .onChange(of: webWrapper.url) { _, newValue in
                if let validUrl = newValue, webCache.lastVisitedUrl != validUrl {
                    webCache.lastVisitedUrl = validUrl
                }
            }

            .onChange(of: webWrapper.title) { _, newValue in
                if let validTitle = newValue {
                    webCache.title = validTitle
                }
            }
            .onChange(of: webWrapper.icon) { _, icon in
                webCache.webIconUrl = URL(string: String(icon)) ?? .defaultWebIconURL
            }
            .onChange(of: webWrapper.canGoBack, perform: { canGoBack in
                if isVisible {
                    toolbarState.canGoBack = canGoBack
                }
            })
            .onChange(of: webWrapper.canGoForward, perform: { _, canGoForward in
                if isVisible {
                    toolbarState.canGoForward = canGoForward
                }
            })
            .onChange(of: webWrapper.estimatedProgress) { _, newValue in
                if newValue >= 1.0 {
                    webcacheStore.saveCaches()
                    if !TracelessMode.shared.isON {
                        let manager = HistoryCoreDataManager()
                        let history = LinkRecord(link: webCache.lastVisitedUrl.absoluteString, imageName: webCache.webIconUrl.absoluteString, title: webCache.title, createdDate: Date().milliStamp)
                        manager.insertHistory(history: history)
                    }
                }
            }
            .onChange(of: addressBar.needRefreshOfIndex) { _, refreshIndex in
                if refreshIndex == tabIndex {
                    webWrapper.webView.reload()
                    addressBar.needRefreshOfIndex = -1
                }
            }

            .onReceive(addressBar.$stopLoadingOfIndex) { stopIndex in
                if stopIndex == tabIndex {
                    webWrapper.webView.stopLoading()
                }
            }
    }

    func goBack() {
        webWrapper.webView.goBack()
    }

    func goForward() {
        webWrapper.webView.goForward()
    }
}

struct TabPageView_Previews: PreviewProvider {
    static var previews: some View {
        Text("problem")
    }
}
