import SwiftUI

struct InquiryView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(\.dismiss) private var dismiss

    @State private var content = ""
    @State private var inquiries: [Inquiry] = []
    @State private var loading = false
    @State private var sending = false
    @State private var errorText: String?

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    composer
                    if !inquiries.isEmpty {
                        SectionHeader(title: String.loc("my_inquiries"))
                        ForEach(inquiries) { inquiry in
                            inquiryCard(inquiry)
                        }
                    } else if !loading {
                        Text(String.loc("inquiry_empty"))
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle(String.loc("contact"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(String.loc("close")) { dismiss() }.tint(Theme.accent)
            }
        }
        .task { await reload() }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: String.loc("inquiry_new"))
            ZStack(alignment: .topLeading) {
                if content.isEmpty {
                    Text(String.loc("inquiry_placeholder"))
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.textTertiary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 14)
                }
                TextEditor(text: $content)
                    .scrollContentBackground(.hidden)
                    .frame(minHeight: 120)
                    .padding(8)
                    .foregroundStyle(Theme.textPrimary)
            }
            .card(cornerRadius: 14)

            if let errorText {
                Text(errorText).font(.system(size: 13)).foregroundStyle(Theme.danger)
            }

            PrimaryButton(String.loc(sending ? "sending" : "inquiry_send"),
                          systemImage: "paperplane.fill",
                          enabled: canSend) {
                send()
            }
        }
    }

    private func inquiryCard(_ inquiry: Inquiry) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(String.loc(inquiry.isReplied ? "status_replied" : "status_pending"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(inquiry.isReplied ? Theme.success : Theme.star)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background((inquiry.isReplied ? Theme.success : Theme.star).opacity(0.15), in: Capsule())
                Spacer()
                Text(Self.day(inquiry.createdAt))
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.textTertiary)
            }
            Text(inquiry.content)
                .font(.system(size: 15))
                .foregroundStyle(Theme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let reply = inquiry.reply, !reply.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrowshape.turn.up.left.fill")
                            .font(.system(size: 11))
                        Text(String.loc("admin_reply")).font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(Theme.accent2)
                    Text(reply)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(12)
                .frame(maxWidth: .infinity)
                .background(Theme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(16)
        .card()
    }

    private var canSend: Bool {
        !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !sending
    }

    private func send() {
        let text = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        sending = true
        errorText = nil
        Task {
            do {
                try await InquiryService.shared.submit(
                    content: text,
                    nickname: progress.nickname.isEmpty ? nil : progress.nickname
                )
                content = ""
                await reload()
            } catch {
                errorText = String.loc("inquiry_send_failed")
            }
            sending = false
        }
    }

    private func reload() async {
        loading = true
        inquiries = (try? await InquiryService.shared.myInquiries()) ?? inquiries
        loading = false
    }

    private static func day(_ iso: String?) -> String {
        guard let iso else { return "" }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = f.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return "" }
        let out = DateFormatter()
        out.dateFormat = "yyyy.MM.dd"
        return out.string(from: date)
    }
}
