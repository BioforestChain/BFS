//
//  TabHStackView.swift
//  DwebBrowser
//
//  Created by ui06 on 4/27/23.
//

import SwiftUI
import WebKit

//observe the showingOptions variety to do the switch animation
struct TabsContainerView: View{
    enum AnimateImageState: Int{
        case initial
        case startExpanding
        case expanded

        case startShrinking
        case shrinked
        
        case animateDone
        
        func next()->AnimateImageState{
            switch self{
            case .startExpanding: return .expanded
            case .startShrinking: return .shrinked
            default: return .animateDone
            }
        }
        func isLarge() -> Bool{
            return self == .startShrinking || self == .expanded
        }
        func isSmall() -> Bool{
            return self == .startExpanding || self == .shrinked
        }
    }
    
    @EnvironmentObject var browser: BrowerVM
//    @Environment(\.safeAreaInsets) var safeAreaInsets

    @State var cellFrames: [CGRect] = [.zero]
    @Namespace private var zoomAnimation
    
    @State private var geoRect: CGRect = .zero // 定义一个变量来存储geoInGlobal的值
    
    @State var imageState: AnimateImageState = .initial
    
    @State private var animateImage = UIImage.snapshotImage(from: URL.defaultSnapshotURL)
    
    private var selectedCellFrame: CGRect {
        cellFrames[browser.selectedTabIndex]
    }
    private var topSafeArea: CGFloat{
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let topSafeAreaInset = windowScene.windows.first?.safeAreaInsets.top {
            return topSafeAreaInset
        }
        return 0
    }
    @State var gridScale: CGFloat = 1
    
    init(){
        print("visiting TabsContainerView init")
    }
    
    var animationImage: some View{
        Image(uiImage: animateImage)
//        Image(uiImage: UIImage(named: "darkImage")!)
            .resizable()
            .scaledToFill()
            .frame(width: cellWidth(fullW: geoRect.width),
                   height: cellHeight(fullH: geoRect.height),alignment: .top)
            .cornerRadius(imageState == .shrinked || imageState == .startExpanding ? gridcellCornerR : 0)
        
            .clipped()
            .position(x: cellCenterX(geoMidX: geoRect.midX),
                      y: cellCenterY(geoMidY: geoRect.midY - geoRect.minY))
        
            .onAppear{
                DispatchQueue.main.asyncAfter(deadline: .now()) {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        imageState = imageState.next()
                    }
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        imageState = .animateDone
                    }
                }
            }
    }
    
    var body: some View{
        GeometryReader { geo in
            
            ZStack{
                WebPreViewGrid(cellFrames: $cellFrames)
                    .background(.secondary)
                    .scaleEffect(x: gridScale, y: gridScale)
                
                if imageState == .shrinked {
                    Color(white: 0.8)
                }
                
                if !browser.showingOptions, imageState == .animateDone{
                    TabHStackView()
                }
                
                if imageState.rawValue < AnimateImageState.animateDone.rawValue, imageState != .initial{
                    animationImage
                }
            }
            .onAppear{
                geoRect = geo.frame(in: .global)
                print("z geo: \(geoRect)")
            }
            
            .onChange(of: browser.showingOptions) { shouldShowGrid in
                print("change showOptions to \(shouldShowGrid)")
                if shouldShowGrid{
                    imageState = .startShrinking
                }else{
                    imageState = .startExpanding
                }
                withAnimation(.easeInOut(duration: 0.5),{
                    gridScale = shouldShowGrid ? 1 : 0.8
                })
            }
        }
    }

    func cellCenterX(geoMidX: CGFloat)-> CGFloat{
        if imageState.isSmall(){
            return selectedCellFrame.minX + selectedCellFrame.width/2.0
        }else if imageState.isLarge(){
            return geoMidX
        }else{
            return selectedCellFrame.minX + selectedCellFrame.width/2.0
        }
    }
    
    func cellCenterY(geoMidY: CGFloat)-> CGFloat{
        if imageState.isSmall(){
            return selectedCellFrame.minY + (selectedCellFrame.height - gridcellBottomH)/2.0 - topSafeArea
        }else if imageState.isLarge(){
            return geoMidY
        }else{
            return selectedCellFrame.minY + (selectedCellFrame.height - gridcellBottomH)/2.0
        }
    }
    
    func cellWidth(fullW: CGFloat)->CGFloat{
        if imageState.isSmall(){
            return selectedCellFrame.width
        }else if imageState.isLarge(){
            return fullW
        }else {
            return selectedCellFrame.width
        }
    }
    
    func cellHeight(fullH: CGFloat)->CGFloat{
        if imageState.isSmall(){
            return selectedCellFrame.height - gridcellBottomH
        }else if imageState.isLarge(){
            return fullH
        }else{
            return selectedCellFrame.height
        }
    }
}

struct TabHStackView: View{
    @EnvironmentObject var browser: BrowerVM
    
    var body: some View{
        
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(browser.pages, id: \.self) { page in
                    TabPageView(webViewStore: page.webStore)
                        .frame(width: screen_width)
                        .onReceive(browser.$showingOptions) { showDeck in
                            if showDeck {
                                if let index = browser.pages.firstIndex(of: page), index == browser.selectedTabIndex{
                                    if browser.currentSnapshotImage == nil, let image = self.environmentObject(browser).snapshot(){
                                        browser.currentSnapshotImage = image
                                        page.webStore.webCache.snapshotUrl = UIImage.createLocalUrl(withImage: image, imageName: page.webStore.webCache.id.uuidString)
                                        WebCacheStore.shared.saveCaches()
                                    }
                                }
                            }
                        }
                }
            }
            .offset(x: browser.addressBarOffset)
        }
        .scrollDisabled(true)
    }
}

struct TabHStackView_Previews: PreviewProvider {
    static var previews: some View {
        TabsContainerView()
            .environmentObject(BrowerVM())
        
    }
}

