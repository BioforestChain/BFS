//
//  ImageManager.swift
//  DwebBrowser
//
//  Created by ui06 on 5/15/23.
//

import Foundation
import UIKit

private let snapshotId = "_snapshot"

// 保存页面快照到本地文件，以便下次打开app使用
extension UIImage {
//    static var defaultSnapShotImage = UIImage.bundleImage(name: "snapshot")
    static var defaultWebIconImage = UIImage.assetsImage(name: "def_web_icon")

    // 保存图片到本地文件
    static func createLocalUrl(withImage image: UIImage, imageName: String) -> URL {
        do {
            let documentsDirectory = URL.documentsDirectory
            let filePath = documentsDirectory.appendingPathComponent(imageName + snapshotId + ".jpg")
            removeImage(with: filePath)
            try image.jpegData(compressionQuality: 1.0)?.write(to: filePath, options: .atomic)
             
            return filePath
        } catch {
            Log("Writing image data went wrong! Error: \(error)")
            return URL.defaultSnapshotURL
        }
    }
    
    // 删除缓存的图片
    static func removeImage(with fileUrl: URL) {
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: fileUrl.path) {
            do {
                try fileManager.removeItem(at: fileUrl)
                Log("deleted old snapshot for replacement successfully.")
            } catch {
                Log("Error while deleting the snapshot: \(error.localizedDescription)")
            }
        } else {
            Log("snapshot does not exist.")
        }
    }

    static func snapshotImage(from localUrl: URL) -> UIImage {
        Log("snapshot url is \(localUrl)")
        var image: UIImage?
        do {
            image = try UIImage(data: Data(contentsOf: localUrl))!
        } catch {
            image = .bundleImage(name: "snapshot")
        }
        return image!
    }
}
