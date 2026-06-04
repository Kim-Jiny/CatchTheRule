import SwiftUI

struct SettingsView: View {
    @Environment(ProgressStore.self) private var progress
    @State private var showResetConfirm = false
    @State private var editingNickname = false
    @State private var draftNickname = ""
    @State private var showInquiry = false

    private let contactEmail = "kjinyz@naver.com"
    private let termsURL = "https://duo.jiny.shop/ctr/terms"
    private let privacyURL = "https://duo.jiny.shop/ctr/privacy"
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
        .alert("진행도를 초기화할까요?", isPresented: $showResetConfirm) {
            Button("취소", role: .cancel) {}
            Button("초기화", role: .destructive) { progress.resetProgress() }
        } message: {
            Text("모든 단계 진행과 별, 기록이 삭제됩니다. 되돌릴 수 없어요.")
        }
        .alert("닉네임 변경", isPresented: $editingNickname) {
            TextField("닉네임", text: $draftNickname)
            Button("취소", role: .cancel) {}
            Button("저장") {
                let t = draftNickname.trimmingCharacters(in: .whitespaces)
                if !t.isEmpty { progress.nickname = t }
            }
        }
    }

    private var header: some View {
        Text("설정")
            .font(.system(size: 28, weight: .bold, design: .rounded))
            .foregroundStyle(Theme.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }

    private var profileSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "person.fill", title: "닉네임",
                        value: progress.nickname.isEmpty ? "미설정" : progress.nickname) {
                draftNickname = progress.nickname
                editingNickname = true
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "lightbulb.fill", title: "남은 힌트",
                        value: "\(progress.hintsRemaining)개", showChevron: false)
        }
        .card()
    }

    private var preferencesSection: some View {
        VStack(spacing: 0) {
            SettingsToggleRow(icon: "speaker.wave.2.fill", title: "효과음",
                              isOn: bindingSound)
            Divider().overlay(Theme.stroke)
            SettingsToggleRow(icon: "iphone.radiowaves.left.and.right", title: "햅틱",
                              isOn: bindingHaptics)
        }
        .card()
    }

    private var supportSection: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "envelope.fill", title: "문의하기", value: nil) {
                showInquiry = true
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "doc.text.fill", title: "이용약관", value: nil) {
                openURL(termsURL)
            }
            Divider().overlay(Theme.stroke)
            SettingsRow(icon: "hand.raised.fill", title: "개인정보처리방침", value: nil) {
                openURL(privacyURL)
            }
        }
        .card()
    }

    private var dangerSection: some View {
        Button { showResetConfirm = true } label: {
            HStack {
                Image(systemName: "trash.fill")
                Text("진행도 초기화").fontWeight(.medium)
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

    private func openMail() {
        let subject = "CatchTheRule 문의"
        let body = "\n\n----\n앱 버전: \(appVersion)"
        var comps = URLComponents()
        comps.scheme = "mailto"
        comps.path = contactEmail
        comps.queryItems = [
            URLQueryItem(name: "subject", value: subject),
            URLQueryItem(name: "body", value: body),
        ]
        if let url = comps.url {
            UIApplication.shared.open(url)
        }
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
