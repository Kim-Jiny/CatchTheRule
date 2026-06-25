import GoogleMobileAds
import SwiftUI
import UIKit

/// AdMob 적응형(anchored adaptive) 배너. 광고 제거 구매 시 아무것도 표시하지 않는다.
struct BannerAd: View {
    @Environment(StoreManager.self) private var store
    let unitID: String
    /// 배너 양옆 여백 합(좌우 각각 20이면 40). 스테이지 풀폭은 0.
    var horizontalInset: CGFloat = 40

    var body: some View {
        if !store.removeAdsPurchased {
            let width = UIScreen.main.bounds.width - horizontalInset
            let size = currentOrientationAnchoredAdaptiveBanner(width: width)
            BannerRepresentable(unitID: unitID, adSize: size)
                .frame(width: width, height: size.size.height)
        }
    }
}

private struct BannerRepresentable: UIViewRepresentable {
    let unitID: String
    let adSize: AdSize

    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: adSize)
        banner.adUnitID = unitID
        banner.rootViewController = Self.topViewController
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}

    private static var topViewController: UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// 배너 광고 단위 ID. DEBUG 빌드는 구글 테스트 단위, 릴리스는 실제 단위.
enum BannerUnits {
    #if DEBUG
    private static let test = "ca-app-pub-3940256099942544/2934735716"   // Google 테스트 배너(iOS)
    static let home = test
    static let challenge = test
    static let settings = test
    static let stage = test
    #else
    static let home = "ca-app-pub-2707874353926722/2301899708"
    static let challenge = "ca-app-pub-2707874353926722/5967676969"
    static let settings = "ca-app-pub-2707874353926722/8049050408"
    static let stage = "ca-app-pub-2707874353926722/1527039935"
    #endif
}
