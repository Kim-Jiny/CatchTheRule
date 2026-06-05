import Foundation
import StoreKit

/// 인앱결제(StoreKit 2) — 현재는 "광고 제거" 비소모성 1종.
/// 엔타이틀먼트(소유 여부)를 UserDefaults 에 미러링해 오프라인에서도 즉시 게이팅 가능.
@MainActor
@Observable
final class StoreManager {
    /// App Store Connect 에 등록할 제품 ID.
    static let removeAdsID = "com.jiny.catchtherule.remove_ads"
    private static let removeAdsKey = "ctr_remove_ads"

    private(set) var removeAdsProduct: Product?
    private(set) var removeAdsPurchased: Bool
    private(set) var loading = false

    private let defaults = UserDefaults.standard

    init() {
        removeAdsPurchased = defaults.bool(forKey: Self.removeAdsKey)
        listenForTransactions()
        Task {
            await loadProducts()
            await refreshEntitlements()
        }
    }

    /// 표시용 가격 (예: "₩1,100"). 제품 로드 전이면 빈 문자열.
    var priceText: String { removeAdsProduct?.displayPrice ?? "" }

    func loadProducts() async {
        do {
            let products = try await Product.products(for: [Self.removeAdsID])
            removeAdsProduct = products.first
        } catch {
            // 네트워크/구성 문제 — 조용히 무시(가격 미표시).
        }
    }

    /// 구매 시도. 성공 시 true.
    @discardableResult
    func purchase() async -> Bool {
        guard let product = removeAdsProduct else { return false }
        loading = true
        defer { loading = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    setPurchased(true)
                    return true
                }
                return false
            case .userCancelled, .pending:
                return false
            @unknown default:
                return false
            }
        } catch {
            return false
        }
    }

    /// 구매 복원 (App Store 동기화 후 엔타이틀먼트 재확인).
    @discardableResult
    func restore() async -> Bool {
        loading = true
        defer { loading = false }
        try? await AppStore.sync()
        await refreshEntitlements()
        return removeAdsPurchased
    }

    /// 현재 유효한 엔타이틀먼트로 소유 여부 갱신.
    func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let t) = result,
               t.productID == Self.removeAdsID,
               t.revocationDate == nil {
                owned = true
            }
        }
        setPurchased(owned)
    }

    private func listenForTransactions() {
        Task.detached { [weak self] in
            for await update in Transaction.updates {
                if case .verified(let transaction) = update {
                    await transaction.finish()
                    await self?.refreshEntitlements()
                }
            }
        }
    }

    private func setPurchased(_ value: Bool) {
        removeAdsPurchased = value
        defaults.set(value, forKey: Self.removeAdsKey)
    }
}
