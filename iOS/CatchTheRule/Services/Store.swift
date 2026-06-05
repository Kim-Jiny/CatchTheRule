import Foundation
import StoreKit

/// 인앱결제(StoreKit 2)
///  - 광고 제거: 비소모성(엔타이틀먼트)
///  - 힌트 묶음: 소모성(+20 힌트)
/// 구매는 StoreKit 로 로컬 검증 후 서버(/iap/verify)에 영수증을 보내 2차 검증·기록한다.
@MainActor
@Observable
final class StoreManager {
    static let removeAdsID = "com.jiny.catchtherule.remove_ads"
    static let hintsID = "com.jiny.catchtherule.hints_20"
    static let hintsGrant = 20
    private static let removeAdsKey = "ctr_remove_ads"

    private(set) var removeAdsProduct: Product?
    private(set) var hintsProduct: Product?
    private(set) var removeAdsPurchased: Bool
    private(set) var loading = false

    private let defaults = UserDefaults.standard
    private let verifyURL = URL(string: "https://duo.jiny.shop/api/catchtherule/iap/verify")!

    init() {
        removeAdsPurchased = defaults.bool(forKey: Self.removeAdsKey)
        listenForTransactions()
        Task {
            await loadProducts()
            await refreshEntitlements()
        }
    }

    var priceText: String { removeAdsProduct?.displayPrice ?? "" }
    var hintsPriceText: String { hintsProduct?.displayPrice ?? "" }

    func loadProducts() async {
        do {
            let products = try await Product.products(for: [Self.removeAdsID, Self.hintsID])
            removeAdsProduct = products.first { $0.id == Self.removeAdsID }
            hintsProduct = products.first { $0.id == Self.hintsID }
        } catch {
            // 네트워크/구성 문제 — 가격 미표시.
        }
    }

    /// 광고 제거 구매. 성공 시 true.
    @discardableResult
    func purchase() async -> Bool {
        guard let product = removeAdsProduct else { return false }
        guard let transaction = await buy(product) else { return false }
        await transaction.finish()
        setPurchased(true)
        return true
    }

    /// 힌트 묶음 구매(소모성). 성공 시 지급할 힌트 수, 실패/취소 시 0.
    func purchaseHints() async -> Int {
        guard let product = hintsProduct else { return 0 }
        guard let transaction = await buy(product) else { return 0 }
        await transaction.finish()   // 소모성: finish 로 소비 처리
        return Self.hintsGrant
    }

    /// 구매 복원.
    @discardableResult
    func restore() async -> Bool {
        loading = true
        defer { loading = false }
        try? await AppStore.sync()
        await refreshEntitlements()
        return removeAdsPurchased
    }

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

    // MARK: - Internal

    /// 구매 → 로컬 검증된 트랜잭션 반환(서버 2차 검증 포함). 실패/취소 시 nil.
    private func buy(_ product: Product) async -> Transaction? {
        loading = true
        defer { loading = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                guard case .verified(let transaction) = verification else { return nil }
                await verifyWithServer(jws: verification.jwsRepresentation,
                                       productID: transaction.productID,
                                       transactionID: String(transaction.id))
                return transaction
            case .userCancelled, .pending:
                return nil
            @unknown default:
                return nil
            }
        } catch {
            return nil
        }
    }

    /// 서버 영수증 검증(기록/관리자 확인용). 결과는 게이팅에 강제하지 않음(StoreKit 로컬 검증이 1차).
    private func verifyWithServer(jws: String, productID: String, transactionID: String) async {
        var request = URLRequest(url: verifyURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "platform": "ios",
            "deviceId": CTRDevice.id,
            "productId": productID,
            "transactionId": transactionID,
            "payload": jws,
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        _ = try? await URLSession.shared.data(for: request)
    }

    private func listenForTransactions() {
        Task.detached { [weak self] in
            for await update in Transaction.updates {
                if case .verified(let transaction) = update {
                    await self?.verifyWithServer(jws: update.jwsRepresentation,
                                                 productID: transaction.productID,
                                                 transactionID: String(transaction.id))
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
