//
//  TabPageViewModel.swift
//  DwebBrowser
//
//  Created by ui06 on 5/5/23.
//

import Foundation
import UIKit

struct WebPage: Identifiable, Codable{
    var id = UUID()
    // url to the source of somewhere in internet
    var icon: URL
    var openedUrl: String?  //the website that user has opened on webview
    var title: String   // page title
  
    
    //local file path is direct to the image has saved in document dir
    var snapshot: UIImage{
        let url = URL(fileURLWithPath: "本地路径")
        return UIImage(contentsOfFile: url.path) ?? UIImage(named: "snapshot")!
    }
    static let websites = [
        "baidu.com",
        "163.com",
        "sohu.com",
        "yahoo.com",
        "douban.com",
        "zhihu.com",
        
        
    ]
    
    static func createItem() -> WebPage{
        WebPage(icon: URL(string: "https://img.icons8.com/?size=2x&id=VJz2Ob51dvZJ&format=png")!,  openedUrl: websites[Int.random(in: 0..<websites.count)], title: "Apple")
    }
    
    static let example = WebPage(icon: URL(string: "https://img.icons8.com/?size=2x&id=VJz2Ob51dvZJ&format=png")!,  openedUrl: "https://www.apple.com", title: "Apple")
}


class WebPages: ObservableObject{
    let pages: [WebPage]
    private let storageKey = "WebPages"

    init() {
        let fileManager = FileManager.default
        let url = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent(storageKey)
        if let data = try? Data(contentsOf: url),
           let models = try? NSKeyedUnarchiver.unarchiveObject(with: data) as? [WebPage] {
            pages =  models
        }else{
            pages =  [WebPage.createItem(),WebPage.createItem()]//,WebPage.createItem(),WebPage.createItem()]
        }
    }
    
    func getWebPages() -> [WebPage] {
        let fileManager = FileManager.default
        let url = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent(storageKey)
        if let data = try? Data(contentsOf: url),
           let models = try? NSKeyedUnarchiver.unarchiveObject(with: data) as? [WebPage] {
            return models
        }
        return [WebPage.example]
    }
    
    func saveWebPages(_ pages: [WebPage]) {
        if let data = try? NSKeyedArchiver.archivedData(withRootObject: pages, requiringSecureCoding: false) {
            let fileManager = FileManager.default
            let url = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent(storageKey)
            // 如果文件已经存在，先删除
            if fileManager.fileExists(atPath: url.path) {
                try? fileManager.removeItem(at: url)
            }
            try? data.write(to: url)
        }
    }
}
/*
class TabVCModel: NSObject, NSCoding {
    var icon: UIImage?
    var pageTitle: String?
    //页面截图
    var snapshotView: UIView?{
        didSet{
            if snapshotView != oldValue {
                shotViewChanged = true
                
            }
        }
    }
    static var defSnapShotImageName: String? //the image will be placed in the bundle

    var url: URL?
    var appId: String?
//    var backForwardList: WKBackForwardList?
    var shotViewChanged: Bool!
    
    override init() {
        super.init()
        icon = UIImage(named: "")
        pageTitle = "示例首页"
//        snapshotView = UIView()
//        url = URL(string: "https://www.sina.com")
//        appId = "KEJPMHLA"
        shotViewChanged = false
    }
    
    required init?(coder aDecoder: NSCoder) {
        icon = aDecoder.decodeObject(forKey: "icon") as? UIImage
        pageTitle = aDecoder.decodeObject(forKey: "pageTitle") as? String
        if let cachedshotImage = aDecoder.decodeObject(forKey: "snapshotView") as? UIImage{
            let imageView = UIImageView(image: cachedshotImage)
            snapshotView = imageView
        }
        
        url = aDecoder.decodeObject(forKey: "url") as? URL
        appId = aDecoder.decodeObject(forKey: "appId") as? String
//        guard let backForwardListData = aDecoder.decodeObject(forKey: "backForwardListData") as? Data,
//                     let list = NSKeyedUnarchiver.unarchiveObject(with: backForwardListData) as? WKBackForwardList
//               else {
//                   return nil
//               }
//        backForwardList = list
        shotViewChanged = false
    }
    
    func encode(with aCoder: NSCoder) {
        aCoder.encode(icon, forKey: "icon")
        aCoder.encode(pageTitle, forKey: "pageTitle")
        if snapshotView == nil{
            if TabVCModel.defSnapShotImage != nil{
                snapshotView = UIImageView(image: TabVCModel.defSnapShotImage)
                aCoder.encode(TabVCModel.defSnapShotImage, forKey: "snapshotView")
            }

        }else{
            snapshotView?.takeScreenshot(completion: { image in
                aCoder.encode(image, forKey: "snapshotView")
            })
        }

        aCoder.encode(url, forKey: "url")
        aCoder.encode(appId, forKey: "appId")
//        let backForwardListData = NSKeyedArchiver.archivedData(withRootObject: backForwardList)
//        aCoder.encode(backForwardListData, forKey: "backForwardListData")
    }
}

func isEqualContents(array1: [TabVCModel], array2: [TabVCModel]) -> Bool {
    // 判断元素数量是否相同
    guard array1.count == array2.count else {
        return false
    }
    
    // 遍历数组并比较元素属性是否相同
    for i in 0..<array1.count {
        let element1 = array1[i]
        let element2 = array2[i]
        if element1.icon != element2.icon ||
            element1.pageTitle != element2.pageTitle ||
            element1.snapshotView != element2.snapshotView ||
            element1.url != element2.url ||
            element1.appId != element2.appId ||
            element1.shotViewChanged != element2.shotViewChanged {
//            element1.backForwardList != element2.backForwardList {
            return false
        }
    }
    
    return true
}
 
 
 class DataStorageManager {
     
     static let shared = DataStorageManager()
     
     let userDefaults = UserDefaults.standard
     let storageKey = "SubPageVCModels"
     
     func cacheTabVCModels(_ models: [TabVCModel]) {
         //        let snapshotView = view.snapshotView(afterScreenUpdates: true)
         if let data = try? NSKeyedArchiver.archivedData(withRootObject: models, requiringSecureCoding: false) {
             let fileManager = FileManager.default
             let url = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent(storageKey)
             // 如果文件已经存在，先删除
             if fileManager.fileExists(atPath: url.path) {
                 try? fileManager.removeItem(at: url)
             }
             try? data.write(to: url)
         }
     }
     
     // 获取缓存的快照视图
     func getTabVCModels() -> [TabVCModel]? {
         let fileManager = FileManager.default
         let url = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent(storageKey)
         if let data = try? Data(contentsOf: url),
            let models = try? NSKeyedUnarchiver.unarchiveObject(with: data) as? [TabVCModel] {
             return models
         }
         return nil
     }
 }
 
 
*/
