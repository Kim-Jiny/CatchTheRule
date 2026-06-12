import Foundation
import StoreKit

/// 인앱결제(StoreKit 2)
///  - 광고 제거: 비소모성
///  - 힌트 묶음: 소모성(5/10/20/50)
/// 모든 지급은 redeem(트랜잭션 1회 처리, 중복 방지) 에서 일어나며 구매/복원/백그라운드(updates)
/// 경로 모두 redeem 을 거친다. 소비형 힌트는 onHintsPurchased 콜백으로 ProgressStore 에 적립한다.
@MainActor
@Observable
final class StoreManager {
    static let removeAdsID = "com.jiny.catchtherule.remove_ads"
    static let hintTiers = [5, 10, 20, 50]
    static func hintsID(_ n: Int) -> String { "com.jiny.catchtherule.hints_\(n)" }
    private static let removeAdsKey = "ctr_remove_ads"
    private static let redeemedKey = "ctr_redeemed_txns"

    /// 소비형(힌트) 지급 콜백. 앱 시작 시 ProgressStore 에 연결.
    var onHintsPurchased: ((Int) -> Void)?

    private(set) var products: [String: Product] = [:]
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

    var removeAdsProduct: Product? { products[Self.removeAdsID] }
    var priceText: String { removeAdsProduct?.displayPrice ?? "" }
    /// 해당 힌트 티어의 표시 가격(스토어 현지화). 로드 전이면 빈 문자열.
    func hintsPrice(_ n: Int) -> String { products[Self.hintsID(n)]?.displayPrice ?? "" }

    func loadProducts() async {
        let ids = [Self.removeAdsID] + Self.hintTiers.map { Self.hintsID($0) }
        do {
            let loaded = try await Product.products(for: ids)
            var map: [String: Product] = [:]
            for p in loaded { map[p.id] = p }
            products = map
        } catch {
            // 네트워크/구성 문제 — 가격 미표시.
        }
    }

    /// 광고 제거 구매. 성공 시 true.
    @discardableResult
    func purchase() async -> Bool {
        guard let product = removeAdsProduct else { return false }
        _ = await buy(product)
        return removeAdsPurchased
    }

    /// 힌트 묶음 구매(소모성). 성공 시 true. 실제 지급은 redeem→onHintsPurchased 로 처리(durable).
    @discardableResult
    func purchaseHints(_ count: Int) async -> Bool {
        guard let product = products[Self.hintsID(count)] else { return false }
        return await buy(product) != nil
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

    /// 구매 → 로컬 검증된 트랜잭션. 서버 2차검증 + redeem(지급) 수행. 실패/취소 시 nil.
    private func buy(_ product: Product) async -> Transaction? {
        guard !loading else { return nil }   // 진행 중이면 중복 결제창 방지(연타 대응)
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
                await redeem(transaction)
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

    /// 트랜잭션을 1회만 지급(중복 방지) 후 finish.
    /// 힌트 콜백이 아직 연결되지 않았으면 finish 하지 않아 다음 실행(updates)에서 재처리된다.
    private func redeem(_ transaction: Transaction) async {
        let id = String(transaction.id)
        var done = Set(defaults.stringArray(forKey: Self.redeemedKey) ?? [])
        if done.contains(id) {
            await transaction.finish()
            return
        }
        if transaction.productID == Self.removeAdsID {
            setPurchased(true)
        } else if let n = hintCount(for: transaction.productID) {
            guard let credit = onHintsPurchased else { return }   // 미연결: finish 보류 → 재처리
            credit(n)
        }
        done.insert(id)
        defaults.set(Array(done), forKey: Self.redeemedKey)
        await transaction.finish()
    }

    private func hintCount(for productID: String) -> Int? {
        Self.hintTiers.first { Self.hintsID($0) == productID }
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
                    await self?.redeem(transaction)
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
