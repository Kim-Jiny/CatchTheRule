import AppTrackingTransparency
import Foundation
import GoogleMobileAds
import UIKit

/// 광고 매니저.
///  - 리워드("광고 보고 힌트"): 시청 완료 시 onReward 호출
///  - 전면(스테이지 클리어): 챕터 2+ / 10% 확률 / 3분 쿨다운일 때만 노출
/// 둘 다 미리 로드(prefetch)해두고, 닫히면 다음 것을 재로드한다.
@Observable
final class AdsManager: NSObject {

    /// 표시 가능한 리워드 광고가 준비됐는지(버튼 활성/로딩 표시용).
    private(set) var isReady = false

    private var rewardedAd: GADRewardedAd?
    private var loading = false

    private var interstitialAd: GADInterstitialAd?
    private var interstitialLoading = false
    private var lastInterstitialAt: Date = .distantPast

    // 전면광고 노출 규칙(상수로 조정 가능)
    private static let interstitialMinChapter = 2          // 챕터 2부터
    private static let interstitialBaseProbability = 0.10  // 챕터 2 기준 10%
    private static let interstitialStepPerChapter = 0.05   // 챕터 1 증가마다 +5%p
    private static let interstitialCooldown: TimeInterval = 180   // 3분 쿨다운

    // DEBUG 빌드는 구글 테스트 광고 단위, 릴리스는 실제 단위.
    private var adUnitID: String {
        #if DEBUG
        "ca-app-pub-3940256099942544/1712485313"   // Google 테스트 리워드 단위
        #else
        "ca-app-pub-2707874353926722/2040040240"   // 실제 힌트 리워드 단위
        #endif
    }

    private var interstitialUnitID: String {
        #if DEBUG
        "ca-app-pub-3940256099942544/4411468910"   // Google 테스트 전면 단위
        #else
        "ca-app-pub-2707874353926722/3184529122"   // 실제 게임클리어 전면 단위
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
            self?.loadInterstitial()
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

    // MARK: - 전면(Interstitial)

    /// 전면광고가 없으면 미리 로드해 둔다.
    func loadInterstitial() {
        guard !interstitialLoading, interstitialAd == nil else { return }
        interstitialLoading = true
        GADInterstitialAd.load(withAdUnitID: interstitialUnitID, request: GADRequest()) { [weak self] ad, _ in
            guard let self else { return }
            self.interstitialLoading = false
            if let ad {
                ad.fullScreenContentDelegate = self
                self.interstitialAd = ad
            } else {
                self.interstitialAd = nil
            }
        }
    }

    /// 조건(챕터 >= 2 && 10% 당첨 && 마지막 노출 후 3분 경과 && 준비된 광고 존재)을
    /// 모두 만족할 때만 전면광고를 노출한다. 노출했으면 true.
    /// (광고 제거 구매 여부는 호출부에서 먼저 거른다.)
    @discardableResult
    func maybeShowInterstitial(chapter: Int) -> Bool {
        guard chapter >= Self.interstitialMinChapter else { return false }
        guard Date().timeIntervalSince(lastInterstitialAt) >= Self.interstitialCooldown else { return false }
        // 챕터가 높을수록 확률 상승: 챕터2=10%, 챕터당 +5%p (최대 100%).
        let prob = min(1.0, Self.interstitialBaseProbability + Self.interstitialStepPerChapter * Double(chapter - Self.interstitialMinChapter))
        guard Double.random(in: 0..<1) < prob else {
            loadInterstitial()   // 이번엔 미당첨 — 다음을 위해 준비
            return false
        }
        guard let ad = interstitialAd, let root = Self.rootViewController else {
            loadInterstitial()
            return false
        }
        lastInterstitialAt = Date()
        interstitialAd = nil
        ad.present(fromRootViewController: root)
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
        reload(after: ad)   // 닫힌 종류의 광고를 다시 로드
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        reload(after: ad)
    }

    private func reload(after ad: GADFullScreenPresentingAd) {
        if ad is GADInterstitialAd { loadInterstitial() } else { load() }
    }
}
