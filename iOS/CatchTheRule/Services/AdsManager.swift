import AppTrackingTransparency
import Foundation
import GoogleMobileAds
import UIKit

/// 리워드 광고("광고 보고 힌트 받기").
/// 광고를 미리 한 개 로드해두고(prefetch), 시청을 완료하면 onReward 가 한 번 호출된다.
/// 닫힌 뒤에는 다음 광고를 다시 로드한다.
@Observable
final class AdsManager: NSObject {

    /// 표시 가능한 광고가 준비됐는지(버튼 활성/로딩 표시용).
    private(set) var isReady = false

    private var rewardedAd: GADRewardedAd?
    private var loading = false

    // DEBUG 빌드는 구글 테스트 광고 단위, 릴리스는 실제 단위.
    private var adUnitID: String {
        #if DEBUG
        "ca-app-pub-3940256099942544/1712485313"   // Google 테스트 리워드 단위
        #else
        "ca-app-pub-2707874353926722/2040040240"   // 실제 힌트 리워드 단위
        #endif
    }

    /// ATT(추적 권한) 프롬프트를 먼저 띄운 뒤 광고 SDK를 시작한다.
    /// 권한 결과와 무관하게 SDK는 시작하며, 허용 시 맞춤형 광고에 IDFA가 사용된다.
    func start() {
        // ATT 프롬프트는 앱이 active 상태여야 표시되므로 살짝 지연 후 요청.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            ATTrackingManager.requestTrackingAuthorization { _ in
                DispatchQueue.main.async { self?.startAdSDK() }
            }
        }
    }

    private func startAdSDK() {
        GADMobileAds.sharedInstance().start { [weak self] _ in
            self?.load()
        }
    }

    /// 광고가 없으면 미리 로드해 둔다.
    func load() {
        guard !loading, rewardedAd == nil else { return }
        loading = true
        GADRewardedAd.load(withAdUnitID: adUnitID, request: GADRequest()) { [weak self] ad, _ in
            guard let self else { return }
            self.loading = false
            if let ad {
                ad.fullScreenContentDelegate = self
                self.rewardedAd = ad
                self.isReady = true
            } else {
                self.rewardedAd = nil
                self.isReady = false
            }
        }
    }

    /// 광고를 표시한다. 시청을 완료하면 onReward 가 한 번 호출된다.
    /// 준비된 광고가 없으면 onReward 없이 false 를 반환하고 로드를 시도한다.
    @discardableResult
    func showRewarded(onReward: @escaping () -> Void) -> Bool {
        guard let ad = rewardedAd, let root = Self.rootViewController else {
            load()
            return false
        }
        rewardedAd = nil
        isReady = false
        ad.present(fromRootViewController: root) { onReward() }
        return true
    }

    /// 현재 화면 최상위 VC(시트가 떠 있으면 그 시트). 광고는 여기서 present 해야 충돌이 없다.
    private static var rootViewController: UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

extension AdsManager: GADFullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        load()   // 다음 광고 미리 로드
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        load()
    }
}
