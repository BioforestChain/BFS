//
//  TabPageView.swift
//  TableviewDemo
//
//  Created by ui06 on 4/16/23.
//

import Combine
import DwebShared
import SwiftUI
import WebKit

struct TabPageView: View {
    @EnvironmentObject var animation: ShiftAnimation
    @EnvironmentObject var toolbarState: ToolBarState
    @EnvironmentObject var openingLink: OpeningLink
    @EnvironmentObject var addressBar: AddressBarState

    @EnvironmentObject var dragScale: WndDragScale
    @Environment(\.colorScheme) var colorScheme

    var webCache: WebCache
    @ObservedObject var webWrapper: WebWrapper
    let isVisible: Bool
    var doneLoading: (WebCache) -> Void

    @State private var snapshotHeight: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            content
                .onChange(of: openingLink.clickedLink) { _, link in
                    guard link != emptyURL else { return }
                    if isVisible {
                        webCache.lastVisitedUrl = link
                        if webCache.shouldShowWeb {
                            webWrapper.webMonitor.isLoadingDone = false
                            webWrapper.webView.load(URLRequest(url: link))
                        } else {
                            webCache.shouldShowWeb = true
                        }
                        openingLink.clickedLink = emptyURL
                        print("clickedLink has changed at index: \(link)")
                    }
                }
                .onAppear {
                    print("tabPage rect: \(geo.frame(in: .global))")
                    snapshotHeight = geo.frame(in: .global).height
                }

                .onChange(of: toolbarState.shouldExpand) { _, shouldExpand in
                    if isVisible, !shouldExpand { // 截图，为缩小动画做准备
                        animation.snapshotImage = UIImage.snapshotImage(from: .defaultSnapshotURL)
                        if webCache.shouldShowWeb {
                            webWrapper.webView.scrollView.showsVerticalScrollIndicator = false
                            webWrapper.webView.scrollView.showsHorizontalScrollIndicator = false
                            webWrapper.webView.takeSnapshot(with: nil) { image, _ in
                                webWrapper.webView.scrollView.showsVerticalScrollIndicator = true
                                webWrapper.webView.scrollView.showsHorizontalScrollIndicator = true
                                if let img = image {
                                    animation.snapshotImage = img
                                    webCache.snapshotUrl = UIImage.createLocalUrl(withImage: img, imageName: webCache.id.uuidString)
                                }
                                animation.progress = animation.progress == .obtainedCellFrame ? .startShrinking : .obtainedSnapshot
                            }
                        } else {
                            let toSnapView = content
                                .environment(\.colorScheme, colorScheme)
                                .frame(width: geo.size.width, height: geo.size.height)
                            let render = ImageRenderer(content: toSnapView)
                            render.scale = UIScreen.main.scale
                            animation.snapshotImage = render.uiImage ?? UIImage.snapshotImage(from: .defaultSnapshotURL)
                            if BrowserViewStateStore.shared.colorScheme == .dark,
                               colorSchemeImage.darkImage == nil
                            {
                                colorSchemeImage.darkImage = animation.snapshotImage
                            }

                            if BrowserViewStateStore.shared.colorScheme == .light,
                               colorSchemeImage.lightImage == nil
                            {
                                colorSchemeImage.lightImage = animation.snapshotImage
                            }
                            webCache.snapshotUrl = UIImage.createLocalUrl(withImage: animation.snapshotImage, imageName: webCache.id.uuidString)
                            animation.progress = animation.progress == .obtainedCellFrame ? .startShrinking : .obtainedSnapshot
                        }
                    }
                }
        }
    }

    var content: some View {
        ZStack {
            if webCache.shouldShowWeb {
                webComponent
            } else {
                Color.bkColor.overlay {
                    HomePageView()
                        .environmentObject(dragScale)
                        .opacity(addressBar.isFocused ? 0 : 1)
                }
            }
        }
    }

    var webComponent: some View {
        let _ = Self._printChanges()
        return TabWebView(webView: webWrapper.webView)
            .id(webWrapper.id)
            .onAppear {
                if webWrapper.estimatedProgress < 0.001 {
                    webWrapper.webView.load(URLRequest(url: webCache.lastVisitedUrl))
                }
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

            .onChange(of: webWrapper.estimatedProgress) { _, newValue in
                if newValue >= 1.0 {
                    doneLoading(webCache)
                }
            }
            .onChange(of: addressBar.needRefreshOfIndex) { _, _ in
                if isVisible {
                    webWrapper.webMonitor.isLoadingDone = false
                    webWrapper.webView.reload()
                    addressBar.needRefreshOfIndex = -1
                }
            }
            .onChange(of: addressBar.stopLoadingOfIndex) { _, _ in
                if isVisible {
                    webWrapper.webView.stopLoading()
                }
            }
            .onChange(of: toolbarState.creatingDesktopLink) { _, _ in
                Task {
                    try await browserService.createDesktopLink(link: webCache.lastVisitedUrl.absoluteString, title: webCache.title, iconString: webCache.webIconUrl.absoluteString)
                }
            }
    }
}

struct TabPageView_Previews: PreviewProvider {
    static var previews: some View {
        Text("problem")
    }
}
