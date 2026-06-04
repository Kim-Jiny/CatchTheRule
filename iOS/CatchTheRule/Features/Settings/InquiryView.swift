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
                        SectionHeader(title: "내 문의")
                        ForEach(inquiries) { inquiry in
                            inquiryCard(inquiry)
                        }
                    } else if !loading {
                        Text("보낸 문의가 여기에 표시됩니다.")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("문의하기")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("닫기") { dismiss() }.tint(Theme.accent)
            }
        }
        .task { await reload() }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "새 문의")
            ZStack(alignment: .topLeading) {
                if content.isEmpty {
                    Text("궁금한 점이나 의견을 남겨주세요")
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

            PrimaryButton(sending ? "보내는 중..." : "문의 보내기",
                          systemImage: "paperplane.fill",
                          enabled: canSend) {
                send()
            }
        }
    }

    private func inquiryCard(_ inquiry: Inquiry) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(inquiry.isReplied ? "답변완료" : "대기중")
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
                        Text("운영자 답변").font(.system(size: 12, weight: .semibold))
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
                errorText = "전송에 실패했어요. 잠시 후 다시 시도해주세요."
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
