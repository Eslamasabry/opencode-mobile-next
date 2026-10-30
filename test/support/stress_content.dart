// Extreme content for capacity tests (docs/ux-system/kit-v2.md §8.6): the
// words a part must carry without breaking, whatever slot they land in.
//
// Each fixture is what real use produces sooner or later: a pasted
// paragraph, a URL or hash with no break opportunity, a language with no
// spaces, emoji built from several code points, a counter that ran for a
// year, a server that sent nothing. Pseudo-localization is the standard i18n
// check: every letter accented and every string made ~40% longer inside
// brackets, so a translation that grows (German, Finnish, Russian) and text
// that is cut at either end both show up in English builds.

/// One kind of extreme content.
enum StressFixture {
  /// About 1,000 characters of prose (Arabic in right to left).
  prose,

  /// One unbroken 300-character token: a URL with a path and a hash.
  token,

  /// Chinese and Japanese with no spaces to break at.
  cjk,

  /// Emoji: ZWJ families, flags, skin tones, keycaps.
  emoji,

  /// Huge numbers and counts.
  numbers,

  /// The empty string.
  empty,

  /// The original words, pseudo-localized (accented, ~40% longer,
  /// bracketed).
  pseudo,
}

abstract final class StressContent {
  static const String prose =
      'The agent finished the migration of the billing service to the new '
      'database cluster, but three of the forty-two integration checks are '
      'still failing because the staging environment uses an older schema '
      'that does not have the invoice_adjustments table yet. It tried to '
      'apply the pending migrations on staging, stopped when the permission '
      'request for the production read replica came back unanswered, and '
      'left a summary of every file it changed together with the commands it '
      'ran, the output of each command, the time each one took and the exact '
      'error messages it saw, so that whoever picks this up tomorrow morning '
      'can continue from the same place without repeating any of the work '
      'that already succeeded. Before you approve the remaining steps, read '
      'the list of changes to the payment webhooks, because two of them '
      'change how refunds are retried when the payment provider times out, '
      'and one of them removes a retry that was added last year after an '
      'incident that charged some customers twice in the same minute.';

  static const String proseArabic =
      'أنهى الوكيل نقل خدمة الفواتير إلى مجموعة قواعد البيانات الجديدة، لكن '
      'ثلاثة من أصل اثنين وأربعين فحصًا للتكامل ما زالت تفشل لأن بيئة '
      'الاختبار تستخدم مخططًا أقدم لا يحتوي بعد على جدول تعديلات الفواتير. '
      'حاول تطبيق الترحيلات المعلقة على بيئة الاختبار، وتوقف عندما عاد طلب '
      'الإذن الخاص بنسخة القراءة في بيئة الإنتاج دون إجابة، وترك ملخصًا لكل '
      'ملف غيّره مع الأوامر التي شغّلها ومخرجات كل أمر والوقت الذي استغرقه '
      'كل واحد منها ورسائل الخطأ كما ظهرت تمامًا، حتى يتمكن من يتابع العمل '
      'صباح الغد من الاستمرار من المكان نفسه دون تكرار أي عمل نجح بالفعل. '
      'قبل أن توافق على الخطوات المتبقية، اقرأ قائمة التغييرات على خطافات '
      'الدفع، لأن اثنين منها يغيّران طريقة إعادة محاولة الاسترداد عندما '
      'تنتهي مهلة مزود الدفع، وواحدًا منها يزيل إعادة محاولة أضيفت العام '
      'الماضي بعد حادثة خصمت من بعض العملاء مرتين في الدقيقة نفسها. '
      'وإذا لم تكن متأكدًا من أي خطوة فاطلب من الوكيل أن يشرح سببها قبل '
      'تنفيذها، فهو يحتفظ بسجل كامل لكل ما فعله ولماذا فعله.';

  /// 300 characters, no space, hyphen or other break opportunity a line
  /// breaker prefers.
  static final String token = _token();

  static String _token() {
    const head =
        'https://git.example.com/acme/billing/blob/'
        '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08/'
        'services/payments/webhooks/refund_retry_policy_v2_final_REALLY_final.dart'
        '?plain=1&line=4096#';
    final b = StringBuffer(head);
    const hex = '0123456789abcdef';
    var i = 0;
    while (b.length < 300) {
      b.write(hex[(i * 7 + 3) % 16]);
      i++;
    }
    return b.toString().substring(0, 300);
  }

  static const String cjk =
      '代理已将计费服务迁移到新的数据库集群但是四十二个集成检查中仍有三个失败'
      '因为测试环境使用的是旧的架构エージェントは請求サービスの移行を完了しましたが'
      'ステージング環境では古いスキーマが使われているため四十二件の統合チェックのうち'
      '三件がまだ失敗しています本番環境の読み取りレプリカへの許可を待っています';

  static const String emoji =
      '👨‍👩‍👧‍👦👩🏽‍💻🧑🏿‍🚀🏳️‍🌈🇪🇬🇯🇵🇧🇷1️⃣2️⃣#️⃣ '
      '👨‍👩‍👧‍👦👩🏽‍💻🧑🏿‍🚀🏳️‍🌈🇪🇬🇯🇵🇧🇷1️⃣2️⃣#️⃣ '
      '👨‍👩‍👧‍👦👩🏽‍💻🧑🏿‍🚀🏳️‍🌈🇪🇬🇯🇵🇧🇷1️⃣2️⃣#️⃣ '
      '❤️‍🔥🫶🏾🤌🏻🧔🏻‍♀️👁️‍🗨️🐕‍🦺🧑‍🤝‍🧑🏳️‍⚧️🏴‍☠️';

  static const String numbers =
      '1,234,567,890,123 files · 99,999,999+ tokens · −4,294,967,295 · '
      '1.7976931348623157e308 · 18,446,744,073,709,551,615 bytes';

  /// [count] distinct short labels for a list-like slot.
  static List<String> items(int count, {bool rtl = false}) => [
    for (var i = 1; i <= count; i++)
      rtl ? 'العنصر رقم $i في القائمة' : 'Item number $i in the list',
  ];

  /// The text [fixture] puts in a slot whose real words are [original].
  static String forSlot(
    StressFixture fixture,
    String original, {
    bool rtl = false,
  }) => switch (fixture) {
    StressFixture.prose => rtl ? proseArabic : prose,
    StressFixture.token => token,
    StressFixture.cjk => cjk,
    StressFixture.emoji => emoji,
    StressFixture.numbers => numbers,
    StressFixture.empty => '',
    StressFixture.pseudo => pseudo(original),
  };

  static const _accents = <String, String>{
    'a': 'á',
    'b': 'ƀ',
    'c': 'ç',
    'd': 'ð',
    'e': 'é',
    'f': 'ƒ',
    'g': 'ĝ',
    'h': 'ĥ',
    'i': 'î',
    'j': 'ĵ',
    'k': 'ķ',
    'l': 'ļ',
    'm': 'ɱ',
    'n': 'ñ',
    'o': 'ö',
    'p': 'þ',
    'q': 'ǫ',
    'r': 'ŕ',
    's': 'š',
    't': 'ţ',
    'u': 'û',
    'v': 'ṽ',
    'w': 'ŵ',
    'x': 'ẋ',
    'y': 'ý',
    'z': 'ž',
    'A': 'Å',
    'B': 'Ɓ',
    'C': 'Ç',
    'D': 'Ð',
    'E': 'É',
    'F': 'Ƒ',
    'G': 'Ĝ',
    'H': 'Ĥ',
    'I': 'Î',
    'J': 'Ĵ',
    'K': 'Ķ',
    'L': 'Ļ',
    'M': 'Ṁ',
    'N': 'Ñ',
    'O': 'Ö',
    'P': 'Þ',
    'Q': 'Ǫ',
    'R': 'Ŕ',
    'S': 'Š',
    'T': 'Ţ',
    'U': 'Û',
    'V': 'Ṽ',
    'W': 'Ŵ',
    'X': 'Ẋ',
    'Y': 'Ý',
    'Z': 'Ž',
  };

  static const _padding = 'one two three four five six seven eight nine ten';

  /// Marks a pseudo-localized string, so the same text is never expanded
  /// twice.
  static const pseudoOpen = '⟦';
  static const pseudoClose = '⟧';

  /// Pseudo-localization: letters accented, ~40% longer (at least three
  /// characters), in brackets. Empty stays empty; text already
  /// pseudo-localized is returned as it is.
  static String pseudo(String text) {
    if (text.isEmpty || text.startsWith(pseudoOpen)) return text;
    final accented = text.split('').map((c) => _accents[c] ?? c).join();
    final extra = (text.length * .4).ceil().clamp(3, 1 << 20);
    final pad = StringBuffer();
    while (pad.length < extra) {
      pad.write(pad.isEmpty ? '' : ' ');
      pad.write(_padding);
    }
    return '$pseudoOpen$accented ${pad.toString().substring(0, extra)}'
        '$pseudoClose';
  }
}
