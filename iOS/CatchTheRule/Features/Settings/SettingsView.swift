import SwiftUI

struct SettingsView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(StoreManager.self) private var store
    @State private var version = VersionService()
    @State private var showResetConfirm = false
    @State private var editingNickname = false
    @State private var draftNickname = ""
    @State private var showInquiry = false
    @State private var showHintShop = false
    @State private var iapMessage: String?

    private let termsURL = "https://duo.jiny.shop/ctr/terms"
    private let privacyURL = "https://duo.jiny.shop/ctr/privacy"
    private let supportURL = "https://duo.jiny.shop/ctr/support"
    private var langCode: String { Locale.current.language.languageCode?.identifier ?? "en" }

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground(animated: true)
                ScrollView {
                    VStack(spacing: 20) {
                        header.staggerOnAppear(order: 0, dy: -24)
                        profileCard.staggerOnAppear(order: 1)
                        profileSection.staggerOnAppear(order: 2)
                        storeSection.staggerOnAppear(order: 3)
                        preferencesSection.staggerOnAppear(order: 4)
                        BannerAd(unitID: BannerUnits.settings)
                            .staggerOnAppear(order: 5)
                        supportSection.staggerOnAppear(order: 6)
                        dangerSection.staggerOnAppear(order: 7)
                        footer.staggerOnAppear(order: 8)
                    }
                    .padding(20)
                }
            }
            .neonScreen()
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(isPresented: $showInquiry) {
            NavigationStack { InquiryView() }
                .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $showHintShop) {
            HintShopSheet()
                .preferredColorScheme(.dark)
        }
        .alert(String.loc("reset_confirm_title"), isPresented: $showResetConfirm) {
            Button(String.loc("cancel"), role: .cancel) {}
            Button(String.loc("reset"), role: .destructive) { progress.resetProgress() }
        } message: {
            Text(String.loc("reset_confirm_msg"))
        }
        .alert(String.loc("change_nickname"), isPresented: $editingNickname) {
            TextField(String.loc("nickname"), text: $draftNickname)
            Button(String.loc("cancel"), role: .cancel) {}
            Button(String.loc("save")) {
                let t = draftNickname.trimmingCharacters(in: .whitespaces)
                if !t.isEmpty { progress.nickname = t }
            }
        }
        .alert(iapMessage ?? "", isPresented: Binding(
            get: { iapMessage != nil },
            set: { if !$0 { iapMessage = nil } }
        )) {
            Button(String.loc("close"), role: .cancel) {}
        }
    }

    private var header: some View {
        Text(String.loc("settings"))
            .font(.system(size: 30, weight: .heavy, design: .rounded))
            .foregroundStyle(LinearGradient(colors: [.white, Theme.accent2],
                                            startPoint: .leading, endPoint: .trailing))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }

    private var profileCard: some View {
        let name = progress.nickname.trimmingCharacters(in: .whitespaces)
        return HStack(spacing: 16) {
            ZStack {
                Circle().fill(Theme.bgElevated)
                Circle().stroke(Theme.accentGradient, lineWidth: 3)
                    .shadow(color: Theme.accent.opacity(0.6), radius: 8)
                if let first = name.first {
                    Text(String(first).uppercased())
                        .font(.system(size: 30, weight: .heavy, design: .rounded))
                        .foregroundStyle(Theme.accentGradient)
                } else {
                    Image(systemName: "person.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(Theme.accentGradient)
                }
            }
            .frame(width: 68, height: 68)

            VStack(alignment: .leading, spacing: 8) {
                Text(name.isEmpty ? String.loc("not_set") : name)
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                HStack(spacing: 5) {
                    Image(systemName: "star.fill").foregroundStyle(Theme.star)
                    AnimatedValue(target: Double(progress.totalStars), duration: 0.8) { v in
                        Text(String.loc("home_total_stars", Int(v.rounded())))
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(Theme.textPrimary)
                    }
                }
                .font(.system(size: 13))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(Theme.star.opacity(0.12)))
                .overlay(Capsule().stroke(Theme.star.opacity(0.35), lineWidth: 1))
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .neonCard(Theme.accent, strong: true)
        .accessibilityElement(children: .combine)
    }

    private var profileSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "person.fill", color: SettingsColor.cyan, title: String.loc("nickname"),
                        value: progress.nickname.isEmpty ? String.loc("not_set") : progress.nickname) {
                draftNickname = progress.nickname
                editingNickname = true
            }
            SettingsDivider()
            SettingsRow(icon: "lightbulb.fill", color: SettingsColor.amber, title: String.loc("hints_left"),
                        value: String.loc("hints_value", progress.hintsRemaining), showChevron: false)
        }
        .settingsGroup()
    }

    private var storeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(spacing: 0) {
                // 광고 제거 — 구매 전에만 노출(구매 시 자동으로 사라짐)
                if !store.removeAdsPurchased {
                    SettingsRow(icon: "rectangle.slash", color: SettingsColor.pink, title: String.loc("iap_remove_ads"),
                                value: store.priceText.isEmpty ? String.loc("iap_loading") : store.priceText) {
                        Task { _ = await store.purchase() }
                    }
                    SettingsDivider()
                }
                // 힌트 구매 — 단일 버튼(누르면 5·10·20·50 팝업)
                SettingsRow(icon: "lightbulb.fill", color: SettingsColor.amber, title: String.loc("iap_buy_hints"), value: nil) {
                    showHintShop = true
                }
                SettingsDivider()
                // 구매 복원
                SettingsRow(icon: "arrow.clockwise", color: SettingsColor.slate, title: String.loc("iap_restore"), value: nil) {
                    Task {
                        let restored = await store.restore()
                        iapMessage = String.loc(restored ? "iap_restore_done" : "iap_restore_none")
                    }
                }
            }
            .settingsGroup()

            // 광고 제거 범위 고지(배너·전면만 제거, 보상형 힌트 광고는 유지)
            if !store.removeAdsPurchased {
                Text(String.loc("iap_remove_ads_note"))
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
            }
        }
    }

    private var preferencesSection: some View {
        VStack(spacing: 0) {
            SettingsToggleRow(icon: "speaker.wave.2.fill", color: SettingsColor.cyan, title: String.loc("sound"),
                              isOn: bindingSound)
            SettingsDivider()
            SettingsToggleRow(icon: "iphone.radiowaves.left.and.right", color: SettingsColor.violet, title: String.loc("haptics"),
                              isOn: bindingHaptics)
        }
        .settingsGroup()
    }

    private var supportSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "envelope.fill", color: SettingsColor.green, title: String.loc("contact"), value: nil) {
                showInquiry = true
            }
            SettingsDivider()
            SettingsRow(icon: "questionmark.circle.fill", color: SettingsColor.green, title: String.loc("support"), value: nil) {
                openURL("\(supportURL)?lang=\(langCode)")
            }
            SettingsDivider()
            SettingsRow(icon: "doc.text.fill", color: SettingsColor.slate, title: String.loc("terms"), value: nil) {
                openURL("\(termsURL)?lang=\(langCode)")
            }
            SettingsDivider()
            SettingsRow(icon: "hand.raised.fill", color: SettingsColor.slate, title: String.loc("privacy"), value: nil) {
                openURL("\(privacyURL)?lang=\(langCode)")
            }
        }
        .settingsGroup()
    }

    private var dangerSection: some View {
        Button { showResetConfirm = true } label: {
            HStack(spacing: 14) {
                IconChip(icon: "trash.fill", color: Theme.danger)
                Text(String.loc("reset_progress")).fontWeight(.medium)
                Spacer()
            }
            .font(.system(size: 16))
            .foregroundStyle(Theme.danger)
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Theme.danger.opacity(0.08)))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.danger.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(NeonPressStyle())
    }

    private var footer: some View {
        VStack(spacing: 12) {
            // 새 문제(스토어 최신) 안내
            if version.updateAvailable {
                Button { openURL(version.storeURL ?? "https://apps.apple.com/") } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "sparkles")
                        Text(String.loc("version_new_available"))
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(String.loc("version_update_action"))
                            .fontWeight(.bold)
                    }
                    .font(.system(size: 14))
                    .foregroundStyle(.white)
                    .padding(14)
                    .frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.accentGradient))
                    .shadow(color: Theme.accent.opacity(0.5), radius: 12, y: 2)
                }
                .buttonStyle(NeonPressStyle())
            }

            VStack(spacing: 6) {
                HStack {
                    Text(String.loc("my_version")).foregroundStyle(Theme.textTertiary)
                    Spacer()
                    versionChip(version.current, color: Theme.accent)
                }
                HStack {
                    Text(String.loc("store_version")).foregroundStyle(Theme.textTertiary)
                    Spacer()
                    if let s = version.store {
                        versionChip(s, color: version.updateAvailable ? Theme.accent2 : Theme.textSecondary)
                    } else {
                        Text(version.checked ? "—" : String.loc("version_checking"))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                if version.checked, version.store != nil, !version.updateAvailable {
                    Text(String.loc("version_up_to_date"))
                        .foregroundStyle(Theme.success)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 2)
                }
            }
            .font(.system(size: 13))
            .padding(16)
            .frame(maxWidth: .infinity)
            .settingsGroup()
        }
        .padding(.top, 8)
        .task { await version.checkOnce() }
    }

    private func versionChip(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .bold, design: .rounded))
            .monospacedDigit()
            .foregroundStyle(color)
            .padding(.horizontal, 10).padding(.vertical, 3)
            .background(Capsule().fill(color.opacity(0.14)))
            .overlay(Capsule().stroke(color.opacity(0.35), lineWidth: 1))
    }

    // MARK: - Bindings (avoid @Bindable on Environment)

    private var bindingSound: Binding<Bool> {
        Binding(get: { progress.soundOn }, set: { progress.soundOn = $0 })
    }
    private var bindingHaptics: Binding<Bool> {
        Binding(get: { progress.hapticsOn }, set: { progress.hapticsOn = $0 })
    }

    private func openURL(_ string: String) {
        if let url = URL(string: string) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Rows

enum SettingsColor {
    static let cyan = Color(hex: 0x18C8FF)
    static let violet = Color(hex: 0x7C5CFF)
    static let amber = Color(hex: 0xFFB020)
    static let slate = Color(hex: 0x8DA2C0)
    static let green = Color(hex: 0x3DDC97)
    static let pink = Color(hex: 0xFF5C8A)
}

struct IconChip: View {
    let icon: String
    let color: Color

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(color)
            .frame(width: 34, height: 34)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(color.opacity(0.16)))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(color.opacity(0.4), lineWidth: 1))
            .shadow(color: color.opacity(0.35), radius: 6)
    }
}

struct SettingsDivider: View {
    var body: some View {
        Rectangle()
            .fill(Color.white.opacity(0.06))
            .frame(height: 1)
            .padding(.leading, 62)
    }
}

private extension View {
    /// 둥근 그룹 카드(은은한 네온 테두리).
    func settingsGroup() -> some View {
        self
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Theme.card))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Color.white.opacity(0.09), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct SettingsRow: View {
    let icon: String
    var color: Color = SettingsColor.cyan
    let title: String
    var value: String?
    var showChevron: Bool = true
    var action: (() -> Void)? = nil

    var body: some View {
        Button { action?() } label: {
            HStack(spacing: 14) {
                IconChip(icon: icon, color: color)
                Text(title)
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(2)
                Spacer()
                if let value {
                    Text(value)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.textTertiary)
                        .lineLimit(1)
                }
                if showChevron && action != nil {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(NeonPressStyle())
        .disabled(action == nil)
    }
}

struct SettingsToggleRow: View {
    let icon: String
    var color: Color = SettingsColor.cyan
    let title: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: 14) {
            IconChip(icon: icon, color: color)
            Text(title)
                .font(.system(size: 16))
                .foregroundStyle(Theme.textPrimary)
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.accent2)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }
}
