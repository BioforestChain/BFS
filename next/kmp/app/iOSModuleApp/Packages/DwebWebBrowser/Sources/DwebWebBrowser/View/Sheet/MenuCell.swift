//
//  MenuCell.swift
//  BFS_SwiftUI
//
//  Created by ui03 on 2023/5/6.
//

import SwiftUI

struct MenuCell: View {
    @EnvironmentObject var dragScale: WndDragScale

    var title: String = ""
    var imageName: String = ""

    var body: some View {
        HStack {
            Text(title)
                .padding(.leading, 16)
                .foregroundColor(Color.menuTitleColor)
                .font(.system(size: dragScale.scaledFontSize(maxSize: 16)))
            Spacer()
            Image(uiImage: .assetsImage(name: imageName))
                .renderingMode(.template)
                .foregroundColor(Color.menuTitleColor)
                .padding(12)
                .scaleEffect(dragScale.onWidth)
        }
        .frame(height: 50 * dragScale.onWidth)
        .background(Color.menubkColor)
        .cornerRadius(6)
        .padding(.horizontal, 16)
    }
}

struct MenuCell_Previews: PreviewProvider {
    static var previews: some View {
        MenuCell()
    }
}
