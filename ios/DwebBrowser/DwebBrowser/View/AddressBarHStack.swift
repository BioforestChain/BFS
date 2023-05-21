//
//  AddressBarHub.swift
//  TableviewDemo
//
//  Created by ui06 on 4/14/23.
//

import SwiftUI

struct AddressBarHContainer:View{
    @EnvironmentObject var browser: BrowerVM

    var body: some View{
        HStack(spacing: 0) {
            ForEach(browser.pages){ page in
                AddressBar(inputText: "", webStore: page.webWrapper)
                    .frame(width: screen_width)
            }
        }
        .background(.white)
    }
}

struct AddressBarHStack: View {
    @EnvironmentObject var states: ToolbarState
    @EnvironmentObject var browser: BrowerVM
    
    @State private var selectedTab = 0
    @State private var currentIndex = 0
    
    var body: some View {
        GeometryReader { innerGeometry in
            PagingScroll(contentSize: browser.pages.count, content: AddressBarHContainer(), currentPage: $currentIndex)
                .onChange(of: browser.selectedTabIndex) { newValue in
                    
                    print("")
                    currentIndex = newValue
                }
            
//            PageScroll(contentSize: browser.pages.count, content:AddressBarHContainer())
        }
        .frame(height: browser.addressBarHeight)
        .animation(.easeInOut(duration:0.3), value: browser.addressBarHeight)
    }
}

struct AddressBarState{
    var inputText: String
    var isAdressBarFocused: Bool
    var progressValue: Float = 0.0

}

struct AddressBar: View {
    @State var inputText: String = ""
    @FocusState var isAdressBarFocused: Bool
    @EnvironmentObject var browser: BrowerVM
//    @Binding var currentIndex: Int
    
    @ObservedObject var webStore: WebWrapper
    
    var body: some View {
        GeometryReader{ geometry in
            
            ZStack() {
                Color(.white)
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.darkGray))
                    .frame(width:screen_width - 48 ,height: 40)
                    .overlay {
                        if webStore.estimatedProgress > 0.0 && webStore.estimatedProgress < 1.0{
                            GeometryReader { geometry in
                                VStack(alignment: .leading, spacing: 0) {
                                    ProgressView(value: webStore.estimatedProgress)
                                        .progressViewStyle(LinearProgressViewStyle())
                                        .foregroundColor(.blue)
                                        .background(Color(white: 1))
                                        .cornerRadius(4)
                                        .frame(height: webStore.estimatedProgress >= 1.0 ? 0 : 3)
                                        .alignmentGuide(.leading) { d in
                                            d[.leading]
                                        }
                                    
                                }
                                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .bottom)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }
                    .onAppear{
//                        performNetworkRequest()
                    }
                TextField("", text: $inputText)
                    .placeholder(when: inputText.isEmpty) {
                        Text("请输入搜索内容").foregroundColor(Color(white: 0.8))
                    }
                    .background(Color(.darkGray))
                    .foregroundColor(.white)
                    .padding(.horizontal,30)
                    .zIndex(1)
                    .keyboardType(.webSearch)
                    .focused($isAdressBarFocused)
                    .onAppear{
                        print(inputText)
                    }
                    .onTapGesture {
                        print("tapped")
                        isAdressBarFocused = true
                    }
                    .onChange(of: geometry.frame(in: .named("Root")).minX) { offsetX in
                        browser.addressBarOffset = offsetX
                        let rest = CGFloat( Int(offsetX) % Int(screen_width) ) / screen_width
                        if rest <= 0.0001, rest >= -0.0001{
                            //滚动完成
                            print("current whole:\(offsetX)")
                        }
                        
                        
//                        browser.selectedTabIndex = Int(floor(offsetX / screen_width))
//                        let pageCount = Int(floor(geometry.size.width / pageWidth))

//                        currentIndex = Int(floor(offsetX / screen_width))

                    }
            }.frame(height: browser.addressBarHeight)
        }
    }
    
}

struct PageScroll<Content: View>: UIViewRepresentable {
    
    var contentSize: Int
    var content: Content
    
    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.isPagingEnabled = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.contentSize = CGSize(width: screen_width * CGFloat(contentSize), height: 0)
        
        return scrollView
    }
    
    func updateUIView(_ uiView: UIScrollView, context: Context) {
        
        uiView.subviews.forEach { $0.removeFromSuperview() }
        let hostingController = UIHostingController(rootView: content)
        for i in 0..<contentSize {
            let childView = hostingController.view!
            // There must be an adjustment to fix an unknown reason that is causing a strange bug.
            let adjustment = CGFloat((contentSize - 1)) * screen_width/2.0
            childView.frame = CGRect(x: screen_width * CGFloat(i) - adjustment, y: 0, width: screen_width, height: 50)
            uiView.addSubview(childView)
        }
    }
}

struct AddressBarHStack_Previews: PreviewProvider {
    static var previews: some View {
        AddressBar(webStore: WebWrapper(webCache: WebCache()))
            .environmentObject(BrowerVM())
    }
}
