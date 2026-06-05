#!/usr/bin/env python3
# UI 로컬라이즈 생성기 (단일 소스 → 두 플랫폼 리소스)
#
# 마스터 테이블 T(아래) 하나만 수정하면 아래가 한 번에 재생성됩니다.
#   - iOS  : iOS/CatchTheRule/Localizable.xcstrings   (UI 문자열, 키별 7개국어)
#   - iOS  : iOS/CatchTheRule/InfoPlist.xcstrings      (CFBundleDisplayName = 앱 이름/아이콘 라벨)
#   - AOS  : Android/app/src/main/res/values-*/strings.xml (7개 로케일)
#
# 사용법:  python3 scripts/gen_l10n.py   (저장소 루트 어디서든 실행 가능)
# 문자열 추가:  T 에 "key": [en, ko, ja, zh, es, fr, de] 한 줄 추가 후 재실행.
#   - 정수 포맷은 %d / 위치인자 %1$d %2$d (iOS 는 자동으로 %lld 변환)
#   - 코드에서 iOS: String.loc("key") / Android: stringResource(R.string.key)
import json, os, html

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["en", "ko", "ja", "zh", "es", "fr", "de"]
IOS_CODE = {"en":"en","ko":"ko","ja":"ja","zh":"zh-Hans","es":"es","fr":"fr","de":"de"}
AND_DIR  = {"en":"values","ko":"values-ko","ja":"values-ja","zh":"values-zh-rCN","es":"values-es","fr":"values-fr","de":"values-de"}

# key: [en, ko, ja, zh, es, fr, de]   (정수 포맷은 %d / %1$d / %2$d)
T = {
 "app_name": ["Catch the Rule","규칙찾기","ルールを探せ","发现规律","Encuentra la Regla","Trouve le Motif","Finde die Regel"],
 "tab_home": ["Home","홈","ホーム","主页","Inicio","Accueil","Start"],
 "tab_challenge": ["Challenge","도전","チャレンジ","挑战","Reto","Défi","Challenge"],
 "tab_settings": ["Settings","설정","設定","设置","Ajustes","Réglages","Einstellungen"],
 "home_subtitle": ["Spot the pattern and guess what comes next","패턴을 발견하고 다음을 맞혀보세요","パターンを見つけて次を当てよう","发现规律，猜出下一个","Descubre el patrón y adivina lo siguiente","Repère le motif et devine la suite","Erkenne das Muster und errate das Nächste"],
 "home_current": ["Current","현재 도전","現在の挑戦","当前挑战","Actual","En cours","Aktuell"],
 "home_all_clear": ["All Cleared","전체 클리어","全クリア","全部通关","¡Todo completado!","Tout terminé","Alles gelöst"],
 "home_congrats": ["Congratulations! 🎉","축하해요! 🎉","おめでとう！🎉","恭喜！🎉","¡Felicidades! 🎉","Félicitations ! 🎉","Glückwunsch! 🎉"],
 "home_continue": ["Continue","이어하기","つづきから","继续","Continuar","Continuer","Weiter"],
 "home_retry": ["Play Again","다시 도전","もう一度","再玩一次","Jugar de nuevo","Rejouer","Nochmal"],
 "chapters": ["Chapters","챕터","チャプター","章节","Capítulos","Chapitres","Kapitel"],
 "chapter_stages": ["%d stages","%d단계","%dステージ","%d关","%d niveles","%d niveaux","%d Stufen"],
 "stars_of_max": ["/ %d stars","/ %d 별","/ %d スター","/ %d 星","/ %d estrellas","/ %d étoiles","/ %d Sterne"],
 "chapter_label": ["Chapter %d","챕터 %d","チャプター %d","第%d章","Capítulo %d","Chapitre %d","Kapitel %d"],
 "stage_label": ["Stage %d","스테이지 %d","ステージ %d","第%d关","Nivel %d","Niveau %d","Stufe %d"],
 "chapter_1": ["Basic Patterns","기초 패턴","基本パターン","基础规律","Patrones básicos","Motifs de base","Grundmuster"],
 "chapter_2": ["Multiply & Square","곱셈과 제곱","掛け算と平方","乘法与平方","Multiplicar y cuadrados","Multiplication et carrés","Multiplikation & Quadrate"],
 "chapter_3": ["Math Sequences","수학 수열","数列","数学数列","Secuencias matemáticas","Suites mathématiques","Zahlenfolgen"],
 "chapter_4": ["Letter Patterns","문자 패턴","文字パターン","字母规律","Patrones de letras","Motifs de lettres","Buchstabenmuster"],
 "chapter_5": ["Shape Finder","모양 찾기","形さがし","图形规律","Buscar formas","Trouver les formes","Formen finden"],
 "chapter_6": ["Advanced","고급","上級","进阶","Avanzado","Avancé","Fortgeschritten"],
 "chapter_7": ["Master","마스터","マスター","大师","Maestro","Maître","Meister"],
 "chapter_8": ["Mensa","멘사","メンサ","门萨","Mensa","Mensa","Mensa"],
 "chapter_9": ["Genius","천재","天才","天才","Genio","Génie","Genie"],
 "chapter_10": ["Legend","레전드","レジェンド","传奇","Leyenda","Légende","Legende"],
 "chapter_11": ["Mensa Challenge","멘사 챌린지","メンサチャレンジ","门萨挑战","Reto Mensa","Défi Mensa","Mensa-Challenge"],
 "challenge_title": ["Time Attack","타임어택","タイムアタック","限时挑战","Contrarreloj","Contre-la-montre","Zeitangriff"],
 "challenge_subtitle": ["Solve as many as you can in 60s and climb the ranking","60초 동안 최대한 많이 풀고 랭킹에 도전하세요","60秒でできるだけ多く解いてランキングに挑戦","60秒内尽量多答，冲击排行榜","Resuelve todo lo posible en 60 s y sube en el ranking","Résolvez-en un max en 60 s et grimpez au classement","Löse in 60 s so viele wie möglich und steige im Ranking"],
 "challenge_best": ["My Best Score","내 최고 점수","自己ベスト","我的最高分","Mi mejor puntuación","Mon meilleur score","Meine Bestpunktzahl"],
 "challenge_start": ["Start Time Attack","타임어택 시작","タイムアタック開始","开始限时挑战","Iniciar contrarreloj","Démarrer","Starten"],
 "ranking": ["Ranking","랭킹","ランキング","排行榜","Ranking","Classement","Rangliste"],
 "ranking_empty": ["No records yet. Be the first!","아직 기록이 없어요. 첫 주자가 되어보세요!","まだ記録がありません。最初の挑戦者になろう！","还没有记录，来当第一名吧！","Aún no hay registros. ¡Sé el primero!","Aucun record. Soyez le premier !","Noch keine Einträge. Sei der Erste!"],
 "me": ["Me","나","あなた","我","Yo","Moi","Ich"],
 "play_prompt": ["Find the rule and fill the blank","규칙을 찾아 빈칸을 채워보세요","ルールを見つけて空欄を埋めよう","找出规律，填上空格","Encuentra la regla y rellena el hueco","Trouvez la règle et remplissez le vide","Finde die Regel und fülle die Lücke"],
 "correct": ["Correct!","정답!","正解！","答对了！","¡Correcto!","Correct !","Richtig!"],
 "campaign_complete": ["You cleared every stage!","모든 단계를 클리어했어요!","全ステージクリア！","你通关了所有关卡！","¡Has superado todos los niveles!","Vous avez fini tous les niveaux !","Du hast alle Stufen geschafft!"],
 "stars_earned": ["%1$d / %2$d stars earned","별 %1$d / %2$d 획득","%1$d / %2$d スター獲得","获得 %1$d / %2$d 星","%1$d / %2$d estrellas obtenidas","%1$d / %2$d étoiles obtenues","%1$d / %2$d Sterne erhalten"],
 "go_home": ["Home","홈으로","ホームへ","回主页","Inicio","Accueil","Zur Startseite"],
 "ta_end": ["Time's Up","타임어택 종료","終了","时间到","Tiempo agotado","Temps écoulé","Zeit abgelaufen"],
 "ta_score": ["%d solved","%d문제","%d問正解","答对 %d 题","%d resueltos","%d résolus","%d gelöst"],
 "ta_rank_registered": ["You're ranked #%d!","현재 %d위에 등록됐어요!","現在%d位に登録されました！","已登记为第 %d 名！","¡Estás en el puesto #%d!","Vous êtes %de !","Du bist auf Platz #%d!"],
 "nickname_placeholder": ["Enter nickname","닉네임 입력","ニックネーム入力","输入昵称","Escribe un apodo","Entrez un pseudo","Spitzname eingeben"],
 "register_ranking": ["Register Score","랭킹 등록","ランキング登録","提交成绩","Registrar puntuación","Enregistrer","Eintragen"],
 "registering": ["Registering...","등록 중...","登録中...","提交中...","Registrando...","Enregistrement...","Wird eingetragen..."],
 "close": ["Close","닫기","閉じる","关闭","Cerrar","Fermer","Schließen"],
 "settings": ["Settings","설정","設定","设置","Ajustes","Réglages","Einstellungen"],
 "nickname": ["Nickname","닉네임","ニックネーム","昵称","Apodo","Pseudo","Spitzname"],
 "not_set": ["Not set","미설정","未設定","未设置","Sin definir","Non défini","Nicht gesetzt"],
 "hints_left": ["Hints left","남은 힌트","残りヒント","剩余提示","Pistas restantes","Indices restants","Verbleibende Tipps"],
 "hints_value": ["%d","%d개","%d個","%d个","%d","%d","%d"],
 "sound": ["Sound","효과음","効果音","音效","Sonido","Son","Ton"],
 "haptics": ["Haptics","햅틱","触覚","振动","Vibración","Vibrations","Haptik"],
 "contact": ["Contact Us","문의하기","お問い合わせ","联系我们","Contacto","Nous contacter","Kontakt"],
 "terms": ["Terms of Service","이용약관","利用規約","服务条款","Términos del servicio","Conditions d'utilisation","Nutzungsbedingungen"],
 "privacy": ["Privacy Policy","개인정보처리방침","プライバシーポリシー","隐私政策","Política de privacidad","Politique de confidentialité","Datenschutz"],
 "reset_progress": ["Reset Progress","진행도 초기화","進行状況をリセット","重置进度","Restablecer progreso","Réinitialiser","Fortschritt zurücksetzen"],
 "reset_confirm_title": ["Reset progress?","진행도를 초기화할까요?","進行状況をリセットしますか？","要重置进度吗？","¿Restablecer el progreso?","Réinitialiser le progrès ?","Fortschritt zurücksetzen?"],
 "reset_confirm_msg": ["All stage progress, stars, and records will be deleted. This cannot be undone.","모든 단계 진행과 별, 기록이 삭제됩니다. 되돌릴 수 없어요.","すべての進行・スター・記録が削除されます。元に戻せません。","所有进度、星星和记录都会被删除，无法恢复。","Se borrarán todo el progreso, las estrellas y los registros. No se puede deshacer.","Toute la progression, les étoiles et les records seront supprimés. Irréversible.","Aller Fortschritt, Sterne und Einträge werden gelöscht. Nicht umkehrbar."],
 "cancel": ["Cancel","취소","キャンセル","取消","Cancelar","Annuler","Abbrechen"],
 "reset": ["Reset","초기화","リセット","重置","Restablecer","Réinitialiser","Zurücksetzen"],
 "change_nickname": ["Change Nickname","닉네임 변경","ニックネーム変更","修改昵称","Cambiar apodo","Changer de pseudo","Spitznamen ändern"],
 "save": ["Save","저장","保存","保存","Guardar","Enregistrer","Speichern"],
 "inquiry_new": ["New Inquiry","새 문의","新しい問い合わせ","新建咨询","Nueva consulta","Nouvelle demande","Neue Anfrage"],
 "inquiry_placeholder": ["Tell us your question or feedback","궁금한 점이나 의견을 남겨주세요","ご質問・ご意見をお書きください","请留下你的问题或建议","Cuéntanos tu duda o sugerencia","Posez votre question ou avis","Frage oder Feedback eingeben"],
 "inquiry_send": ["Send Inquiry","문의 보내기","送信","发送咨询","Enviar consulta","Envoyer","Senden"],
 "sending": ["Sending...","보내는 중...","送信中...","发送中...","Enviando...","Envoi...","Wird gesendet..."],
 "inquiry_send_failed": ["Failed to send. Please try again later.","전송에 실패했어요. 잠시 후 다시 시도해주세요.","送信に失敗しました。後でもう一度お試しください。","发送失败，请稍后再试。","Error al enviar. Inténtalo más tarde.","Échec de l'envoi. Réessayez plus tard.","Senden fehlgeschlagen. Bitte später erneut."],
 "my_inquiries": ["My Inquiries","내 문의","マイ問い合わせ","我的咨询","Mis consultas","Mes demandes","Meine Anfragen"],
 "inquiry_empty": ["Your inquiries will appear here.","보낸 문의가 여기에 표시됩니다.","送信した問い合わせがここに表示されます。","你发送的咨询会显示在这里。","Tus consultas aparecerán aquí.","Vos demandes apparaîtront ici.","Deine Anfragen erscheinen hier."],
 "admin_reply": ["Reply from us","운영자 답변","運営からの返信","官方回复","Respuesta del equipo","Réponse de l'équipe","Antwort vom Team"],
 "status_replied": ["Replied","답변완료","返信済み","已回复","Respondido","Répondu","Beantwortet"],
 "status_pending": ["Pending","대기중","対応中","待回复","Pendiente","En attente","Ausstehend"],
 "support": ["Support","고객지원","サポート","客户支持","Soporte","Assistance","Support"],
 "iap_remove_ads": ["Remove Ads","광고 제거","広告を削除","移除广告","Quitar anuncios","Supprimer les pubs","Werbung entfernen"],
 "iap_remove_ads_desc": ["Enjoy ad-free play forever","광고 없이 평생 즐기기","ずっと広告なしで楽しむ","永久无广告畅玩","Disfruta sin anuncios para siempre","Profitez sans pub pour toujours","Für immer werbefrei spielen"],
 "iap_purchased": ["Purchased","구매 완료","購入済み","已购买","Comprado","Acheté","Gekauft"],
 "iap_restore": ["Restore Purchases","구매 복원","購入を復元","恢复购买","Restaurar compras","Restaurer les achats","Käufe wiederherstellen"],
 "iap_restore_done": ["Purchases restored","구매를 복원했어요","購入を復元しました","已恢复购买","Compras restauradas","Achats restaurés","Käufe wiederhergestellt"],
 "iap_restore_none": ["No purchases to restore","복원할 구매가 없어요","復元できる購入がありません","没有可恢复的购买","No hay compras que restaurar","Aucun achat à restaurer","Keine Käufe zum Wiederherstellen"],
 "iap_failed": ["Purchase failed. Please try again.","구매에 실패했어요. 다시 시도해주세요.","購入に失敗しました。もう一度お試しください。","购买失败，请重试。","La compra falló. Inténtalo de nuevo.","Achat échoué. Réessayez.","Kauf fehlgeschlagen. Bitte erneut versuchen."],
 "iap_loading": ["Loading…","불러오는 중…","読み込み中…","加载中…","Cargando…","Chargement…","Wird geladen…"],
 "iap_buy_hints": ["Buy Hints","힌트 구매","ヒントを購入","购买提示","Comprar pistas","Acheter des indices","Tipps kaufen"],
 "iap_hints_n": ["%d Hints","힌트 %d개","ヒント%d個","%d个提示","%d pistas","%d indices","%d Tipps"],
 "iap_need_hints_title": ["Out of hints","힌트가 떨어졌어요","ヒントがありません","提示用完了","Sin pistas","Plus d'indices","Keine Tipps mehr"],
 "iap_need_hints_msg": ["Watch an ad or buy hints to keep going.","광고를 보거나 힌트를 구매해 이어가세요.","広告を見るかヒントを購入して続けよう。","看广告或购买提示来继续。","Mira un anuncio o compra pistas para continuar.","Regardez une pub ou achetez des indices pour continuer.","Sieh dir Werbung an oder kaufe Tipps, um weiterzuspielen."],
 "iap_watch_ad": ["Watch an ad","광고 보고 받기","広告を見て獲得","看广告获取","Ver un anuncio","Regarder une pub","Werbung ansehen"],
 "iap_coming_soon": ["Coming soon","준비중","準備中","即将推出","Próximamente","Bientôt","Demnächst"],
 "iap_hints_desc": ["Get a pack of hints","힌트 묶음 받기","ヒントパックを入手","获取提示包","Consigue un paquete de pistas","Obtenez un pack d'indices","Ein Tipp-Paket erhalten"],
 "iap_hints_added": ["Hints added!","힌트가 추가됐어요!","ヒントを追加しました！","已添加提示！","¡Pistas añadidas!","Indices ajoutés !","Tipps hinzugefügt!"],
}

def to_ios_fmt(s):
    # 정수 포맷 %d -> %lld, %1$d -> %1$lld
    import re
    s = re.sub(r"%(\d+\$)?d", lambda m: "%" + (m.group(1) or "") + "lld", s)
    return s

# iOS xcstrings
strings = {}
for key, vals in T.items():
    loc = {}
    for i, lang in enumerate(LANGS):
        loc[IOS_CODE[lang]] = {"stringUnit": {"state": "translated", "value": to_ios_fmt(vals[i])}}
    strings[key] = {"extractionState": "manual", "localizations": loc}
xc = {"sourceLanguage": "en", "strings": strings, "version": "1.0"}
ios_path = os.path.join(ROOT, "iOS/CatchTheRule/Localizable.xcstrings")
with open(ios_path, "w", encoding="utf-8") as f:
    json.dump(xc, f, ensure_ascii=False, indent=2)
print("iOS:", ios_path, "(keys:", len(T), ")")

# iOS InfoPlist.xcstrings — 아이콘 라벨(CFBundleDisplayName) 현지화 = app_name
app = T["app_name"]
info_loc = {}
for i, lang in enumerate(LANGS):
    info_loc[IOS_CODE[lang]] = {"stringUnit": {"state": "translated", "value": app[i]}}
info = {"sourceLanguage": "en", "strings": {
    "CFBundleDisplayName": {"extractionState": "manual", "localizations": info_loc}
}, "version": "1.0"}
info_path = os.path.join(ROOT, "iOS/CatchTheRule/InfoPlist.xcstrings")
with open(info_path, "w", encoding="utf-8") as f:
    json.dump(info, f, ensure_ascii=False, indent=2)
print("iOS InfoPlist:", info_path)

# Android strings.xml per locale
def xml_escape(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    return s
for i, lang in enumerate(LANGS):
    d = os.path.join(ROOT, "Android/app/src/main/res", AND_DIR[lang])
    os.makedirs(d, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, vals in T.items():
        lines.append(f'    <string name="{key}">{xml_escape(vals[i])}</string>')
    lines.append('</resources>')
    with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
print("Android: values + 6 locales written")
