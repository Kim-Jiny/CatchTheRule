import SwiftUI

struct SettingsView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(StoreManager.self) private var store
    @State private var showResetConfirm = false
    @State private var editingNickname = false
    @State private var draftNickname = ""
    @State private var showInquiry = false
    @State private var iapMessage: String?

    private let termsURL = "https://duo.jiny.shop/ctr/terms"
    private let privacyURL = "https://duo.jiny.shop/ctr/privacy"
    private let supportURL = "https://duo.jiny.shop/ctr/support"
    private var langCode: String { Locale.current.language.languageCode?.identifier ?? "en" }
    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground()
                ScrollView {
                    VStack(spacing: 20) {
                        header
                        profileSection
                        storeSection
                        preferencesSection
                        supportSection
                        dangerSection
                        footer
                    }
                    .padding(20)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(isPresented: $showInquiry) {
            NavigationStack { InquiryView() }
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
            .font(.system(size: 28, weight: .bold, design: .rounded))
            .foregroundStyle(Theme.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }

    private var profileSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "person.fill", title: String.loc("nickname"),
                        value: progress.nickname.isEmpty ? String.loc("not_set") : progress.nickname) {
                draftNickname = progress.nickname
                editingNickname = true
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "lightbulb.fill", title: String.loc("hints_left"),
                        value: String.loc("hints_value", progress.hintsRemaining), showChevron: false)
        }
        .card()
    }

    private var storeSection: some View {
        VStack(spacing: 0) {
            // 광고 제거 (비소모성)
            if store.removeAdsPurchased {
                SettingsRow(icon: "checkmark.seal.fill", title: String.loc("iap_remove_ads"),
                            value: String.loc("iap_purchased"), showChevron: false)
            } else {
                SettingsRow(icon: "nosign", title: String.loc("iap_remove_ads"),
                            value: store.priceText.isEmpty ? String.loc("iap_loading") : store.priceText) {
                    Task { await store.purchase() }
                }
            }
            // 힌트 구매 (소모성, 5·10·20·50)
            ForEach(StoreManager.hintTiers, id: \.self) { n in
                Divider().overlay(Theme.stroke)
                SettingsRow(icon: "lightbulb.fill", title: String.loc("iap_hints_n", n),
                            value: store.hintsPrice(n).isEmpty ? String.loc("iap_loading") : store.hintsPrice(n)) {
                    Task {
                        if await store.purchaseHints(n) {   // 지급은 콜백(durable)
                            iapMessage = String.loc("iap_hints_added")
                        }
                    }
                }
            }
            Divider().overlay(Theme.stroke)
            // 구매 복원
            SettingsRow(icon: "arrow.clockwise", title: String.loc("iap_restore"), value: nil) {
                Task {
                    let restored = await store.restore()
                    iapMessage = String.loc(restored ? "iap_restore_done" : "iap_restore_none")
                }
            }
        }
        .card()
    }

    private var preferencesSection: some View {
        VStack(spacing: 0) {
            SettingsToggleRow(icon: "speaker.wave.2.fill", title: String.loc("sound"),
                              isOn: bindingSound)
            Divider().overlay(Theme.stroke)
            SettingsToggleRow(icon: "iphone.radiowaves.left.and.right", title: String.loc("haptics"),
                              isOn: bindingHaptics)
        }
        .card()
    }

    private var supportSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "envelope.fill", title: String.loc("contact"), value: nil) {
                showInquiry = true
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "questionmark.circle.fill", title: String.loc("support"), value: nil) {
                openURL("\(supportURL)?lang=\(langCode)")
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "doc.text.fill", title: String.loc("terms"), value: nil) {
                openURL("\(termsURL)?lang=\(langCode)")
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "hand.raised.fill", title: String.loc("privacy"), value: nil) {
                openURL("\(privacyURL)?lang=\(langCode)")
            }
        }
        .card()
    }

    private var dangerSection: some View {
        Button { showResetConfirm = true } label: {
            HStack {
                Image(systemName: "trash.fill")
                Text(String.loc("reset_progress")).fontWeight(.medium)
                Spacer()
            }
            .font(.system(size: 16))
            .foregroundStyle(Theme.danger)
            .padding(16)
            .frame(maxWidth: .infinity)
            .card()
        }
        .buttonStyle(.plain)
    }

    private var footer: some View {
        Text("CatchTheRule v\(appVersion)")
            .font(.system(size: 13))
            .foregroundStyle(Theme.textTertiary)
            .padding(.top, 8)
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

struct SettingsRow: View {
    let icon: String
    let title: String
    var value: String?
    var showChevron: Bool = true
    var action: (() -> Void)? = nil

    var body: some View {
        Button { action?() } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.accent2)
                    .frame(width: 24)
                Text(title)
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.textPrimary)
                Spacer()
                if let value {
                    Text(value)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.textTertiary)
                }
                if showChevron && action != nil {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(16)
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
    }
}

struct SettingsToggleRow: View {
    let icon: String
    let title: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 15))
                .foregroundStyle(Theme.accent2)
                .frame(width: 24)
            Text(title)
                .font(.system(size: 16))
                .foregroundStyle(Theme.textPrimary)
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.accent)
        }
        .padding(16)
    }
}
