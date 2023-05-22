//
//  ScrollPageContainer.swift
//  DwebBrowser
//
//  Created by ui06 on 5/18/23.
//

import SwiftUI

struct PagingScroll<Content: View>: UIViewRepresentable {
    
    var contentSize: Int
    var content: Content
    @Binding var currentPage: Int
    @Binding var offsetX: CGFloat

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.isPagingEnabled = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.contentSize = CGSize(width: screen_width * CGFloat(contentSize), height: 0)
        scrollView.delegate = context.coordinator
        
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
//                uiView.setContentOffset(CGPoint(x: screen_width * CGFloat(currentPage), y: 0), animated: true)

    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIScrollViewDelegate {
        var parent: PagingScroll
        
        init(_ parent: PagingScroll) {
            self.parent = parent
        }
        
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            let page = Int(scrollView.contentOffset.x / scrollView.frame.width)
            parent.currentPage = page
//            print("scolling offsetX is \(scrollView.contentOffset.x)")
        }
        
        func scrollViewDidScroll(_ scrollView: UIScrollView) {
//            scrollView.contentOffset
            parent.offsetX = -( scrollView.contentOffset.x)
//            print("scolling offsetX is \(scrollView.contentOffset.x)")

        }
    }
}

//let screen_width = UIScreen.main.bounds.width

struct PagingScrollTestView: View {
    @State var currentPage = 0
    @State var offsetX: CGFloat = 0.0

    @State private var randomIndex = 0
    var body: some View {
        VStack {
            PagingScroll(contentSize: 5, content: HStack(spacing: 0) {
                Color.red.frame(width: screen_width, height: 50)
                Color.green.frame(width: screen_width, height: 50)
                Color.blue.frame(width: screen_width, height: 50)
                Color.orange.frame(width: screen_width, height: 50)
                Color.cyan.frame(width: screen_width, height: 50)
            }, currentPage: $currentPage,offsetX: $offsetX)
            
            Button("Random") {
                
                    currentPage = Int.random(in: Range(0...4))
            }
            
            HStack {
                Button("Prev") {
                    if currentPage > 0 {
                        currentPage -= 1
                    }
                }
                Spacer()
                Text("Page \(currentPage + 1)")
                Spacer()
                Button("Next") {
                    if currentPage < 2 {
                        currentPage += 1
                    }
                }
            }.padding()
        }
    }
}

struct ContentView_Previews9: PreviewProvider {
    static var previews: some View {
        PagingScrollTestView()
    }
}
