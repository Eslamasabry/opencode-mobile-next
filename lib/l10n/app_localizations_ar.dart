// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get servicesTitle => 'خدمات التطوير';

  @override
  String get servicesCopy => 'نسخ الأمر';

  @override
  String get servicesSubtitle => 'أوامر المشروع والسجلات وروابط المعاينة';

  @override
  String get servicesIntro =>
      'اجمع أوامر تطوير مشروعك وروابط معاينته في مكان واحد. حفظ الخدمة لا يشغّلها.';

  @override
  String get servicesAdd => 'إضافة خدمة';

  @override
  String get servicesName => 'اسم الخدمة';

  @override
  String get servicesCommand => 'أمر التطوير';

  @override
  String get servicesCommandHint =>
      'استخدم أمرًا يعمل في المقدمة، مثل npm run dev. لا يمكن تتبّع الأوامر التي تعمل في الخلفية أو منفصلة عن الجلسة.';

  @override
  String get servicesUrl => 'رابط المعاينة (اختياري)';

  @override
  String get servicesUrlHint =>
      'استخدم عنوانًا يمكن لهذا الهاتف الوصول إليه. يشير localhost إلى هذا الهاتف. لا يُفتح أي منفذ ولا يُعاد توجيهه تلقائيًا.';

  @override
  String get servicesSave => 'حفظ الخدمة';

  @override
  String get servicesUnavailable =>
      'لا يتيح هذا الخادم تشغيل أوامر التطوير وتتبّعها. يمكنك حفظ الأوامر ومراجعة روابط معاينتها هنا.';

  @override
  String get servicesScopeChanged =>
      'تغيّر الخادم أو المشروع. افتح خدمات التطوير مجددًا من المشروع المطلوب.';

  @override
  String get servicesNotStarted => 'لم يبدأ التشغيل';

  @override
  String get servicesRunning => 'الأمر قيد التشغيل';

  @override
  String get servicesStopped => 'متوقفة';

  @override
  String get servicesUnknown => 'الحالة غير معروفة';

  @override
  String get servicesStatusHint =>
      'حالة الأمر لا تؤكد أن تطبيقك جاهز أو يمكن الوصول إليه.';

  @override
  String get servicesStart => 'تشغيل';

  @override
  String get servicesStop => 'إيقاف';

  @override
  String get servicesRestart => 'إعادة التشغيل';

  @override
  String get servicesLogs => 'السجلات';

  @override
  String get servicesVisit => 'فتح الرابط';

  @override
  String get servicesRemove => 'إزالة الإعدادات';

  @override
  String get servicesStopHint =>
      'هل تريد إيقاف أمر هذه الخدمة المتتبَّع؟ سيزيل الخادم سجلاته المحفوظة أيضًا. لن تتأثر الأوامر الأخرى.';

  @override
  String get servicesRestartHint =>
      'هل تريد إيقاف هذا الأمر المتتبَّع وإزالة سجله من الخادم، ثم تشغيل الأمر المحفوظ مجددًا؟';

  @override
  String get servicesForget => 'مسح سجل التشغيل الأخير';

  @override
  String get servicesForgetHint =>
      'هل تريد مسح سجل التشغيل المحلي؟ لن تتوقف أي عملية على الخادم. قد يؤدي التشغيل مجددًا إلى إنشاء نسخة مكررة إذا كان الأمر السابق لا يزال يعمل.';

  @override
  String get servicesUnknownHint =>
      'تعذّر تأكيد حالة التشغيل الأخير. حدّث الحالة للتحقق منها قبل التشغيل مجددًا.';

  @override
  String get servicesLogEmpty => 'لا توجد مخرجات مسجّلة بعد.';

  @override
  String get servicesWorking => 'جارٍ تحديث الخدمة…';

  @override
  String servicesExit(int code) {
    return 'رمز الخروج المسجّل: $code';
  }

  @override
  String get servicesRefresh => 'تحديث الحالة';

  @override
  String get isolatedTaskScopeChanged =>
      'تغيّر الخادم أو المشروع أثناء فتح هذا العرض. أغلقه وابدأ مجددًا من المشروع المطلوب.';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryModelsAgentsTitle => 'النماذج والوكلاء';

  @override
  String get libraryProvidersTitle => 'مزوّدو الخدمة';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => 'الأوامر والأدوات';

  @override
  String get libraryTerminalTitle => 'الطرفية';

  @override
  String get librarySettingsTitle => 'الإعدادات';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'بصمة شهادة التوقيع SHA-256';

  @override
  String get modelSwitchSession => 'تغيير نموذج هذه المحادثة';

  @override
  String get modelNextFavorite => 'النموذج المفضّل التالي';

  @override
  String get modelChooseTitle => 'اختيار نموذج';

  @override
  String get modelTitleCompact => 'النماذج';

  @override
  String get modelSearchHint => 'البحث في النماذج';

  @override
  String get modelAll => 'كل النماذج';

  @override
  String get modelFavorites => 'المفضّلة';

  @override
  String get modelRecent => 'الأخيرة';

  @override
  String get modelOptions => 'الخيارات';

  @override
  String get modelThinkingMode => 'وضع التفكير';

  @override
  String get modelServerDefault => 'إعداد الخادم الافتراضي';

  @override
  String get modelSelectionSaving => 'جارٍ حفظ اختيار المحادثة…';

  @override
  String get modelUnavailableSelection =>
      'نموذج المحادثة غير متاح في هذا الدليل. حدّث النماذج أو اختر نموذجًا آخر.';

  @override
  String get modelScopeChanged =>
      'تغيّر الخادم. افتح قائمة اختيار النموذج مجددًا للمتابعة.';

  @override
  String get commonClearSearch => 'مسح البحث';

  @override
  String get workTitle => 'المهام';

  @override
  String get workRefresh => 'تحديث';

  @override
  String get workRetry => 'إعادة المحاولة';

  @override
  String get workCancel => 'إلغاء';

  @override
  String get workRunning => 'قيد التشغيل';

  @override
  String get workFinished => 'مكتملة';

  @override
  String get workTimedOut => 'انتهت المهلة';

  @override
  String get workStopped => 'متوقفة';

  @override
  String get workUnknown => 'الحالة غير متاحة';

  @override
  String get workOutput => 'مخرجات الأمر';

  @override
  String get workNoOutput => 'بانتظار المخرجات…';

  @override
  String get workNoFinalOutput => 'لم يُنتج هذا الأمر أي مخرجات.';

  @override
  String get workMoreOutput => 'تحميل المزيد من المخرجات';

  @override
  String get workTrimmed => 'تُعرض أحدث المخرجات. اقتُطع النص الأقدم.';

  @override
  String get workStop => 'إيقاف الأمر';

  @override
  String get workStopTitle => 'هل تريد إيقاف هذا الأمر؟';

  @override
  String get workStopDescription =>
      'سيتوقف الأمر وتُحذف مخرجاته المحفوظة من الخادم. سيبقى النص المحمّل هنا ظاهرًا حتى تغلقه.';

  @override
  String get workTimeout => 'تغيير المهلة';

  @override
  String get workTimeoutDescription => 'تبدأ المهلة الجديدة الآن.';

  @override
  String get workTimeoutOneMinute => 'دقيقة واحدة';

  @override
  String get workTimeoutFiveMinutes => '5 دقائق';

  @override
  String get workTimeoutFifteenMinutes => '15 دقيقة';

  @override
  String get workTimeoutOneHour => 'ساعة واحدة';

  @override
  String get workTimeoutNone => 'بلا مهلة';

  @override
  String get workUnavailable =>
      'لم يعد هذا الأمر متاحًا. ربما أُزيل أو أُلغي عند إعادة تشغيل الخادم.';

  @override
  String get workRestarted =>
      'أُعيد تشغيل الخادم ولم يعد هذا الأمر متاحًا. تظهر مخرجاته المحمّلة أدناه.';

  @override
  String get workDisconnected =>
      'جارٍ إعادة الاتصال. ستُحدّث المخرجات عندما يتاح الخادم.';

  @override
  String get workContextChanged =>
      'تغيّر الخادم أو المشروع. أغلق هذا العرض وافتح المهام الجارية مجددًا.';

  @override
  String workCount(int count) {
    return 'المهام · قيد التشغيل: $count';
  }

  @override
  String workExitCode(int code) {
    return 'رمز الخروج $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => 'مسح نص المسودة';

  @override
  String get composerClearTextSubtitle => 'تبقى المرفقات · يمكن التراجع';

  @override
  String get composerDraftCleared => 'مُسح نص المسودة';

  @override
  String get composerReuseTitle => 'إعادة استخدام طلب';

  @override
  String get queueSaveFailed =>
      'تعذّر حفظ المسودة المنتظرة على هذا الجهاز. لا يزال النص هنا. تحقّق من مساحة التخزين المتاحة وحاول مجددًا.';

  @override
  String get fileSave => 'حفظ';

  @override
  String get fileReload => 'تحديث';

  @override
  String get queueRemoveFailed =>
      'تعذّرت إزالة هذه المسودة من مساحة تخزين الجهاز. لا تزال في قائمة الانتظار. تحقّق من مساحة التخزين المتاحة وحاول مجددًا.';

  @override
  String get composerReuseSubtitle =>
      'إعادة استخدام نص من هذه المحادثة والطلبات المرسلة مؤخرًا';

  @override
  String get composerReuseSearch => 'البحث في الطلبات الأخيرة';

  @override
  String get composerReuseEmpty => 'لا توجد طلبات مطابقة';

  @override
  String get backgroundWorkTitle => 'نقل العمل الجاري إلى الخلفية';

  @override
  String get backgroundWorkShortcut =>
      'متابعة هذا العمل أثناء استخدام المحادثة · Ctrl+B';

  @override
  String get backgroundWorkNoop =>
      'لا يوجد وكلاء فرعيون في المقدمة لنقلهم إلى الخلفية.';

  @override
  String get backgroundWorkPromoted =>
      'يواصل الوكلاء الفرعيون العمل في الخلفية.';

  @override
  String get libraryNoModel => 'لم يُحدّد نموذج';

  @override
  String get chatAttachmentUnsupported =>
      'يمكن إرفاق الصور وملفات PDF والملفات النصية وملفات Excel وWord ‏(‎.xlsx و‎.docx).';

  @override
  String get termuxRestartTitle => 'هل تريد إعادة تشغيل الخادم المحلي؟';

  @override
  String get termuxRestartMessage =>
      'سيتعذّر استخدام OpenCode لفترة قصيرة. سيحتفظ التطبيق بمشروعك الحالي ويعيد الاتصال تلقائيًا.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تولّد $count محادثة ردودًا. ستقاطعها إعادة التشغيل.',
      many: 'تولّد $count محادثة ردودًا. ستقاطعها إعادة التشغيل.',
      few: 'تولّد $count محادثات ردودًا. ستقاطعها إعادة التشغيل.',
      two: 'تولّد محادثتان ردودًا. ستقاطعهما إعادة التشغيل.',
      one: 'تولّد محادثة واحدة ردًا. ستقاطعها إعادة التشغيل.',
      zero: 'لا توجد محادثات تولّد ردودًا.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'إعادة التشغيل';

  @override
  String get chatCopyCompleteReply => 'نسخ الرد كاملًا';

  @override
  String get chatCopyReplySoFar => 'نسخ الرد حتى الآن';

  @override
  String commandRunTitle(String command) {
    return 'تشغيل /$command';
  }

  @override
  String get commandDestination => 'المحادثة';

  @override
  String get commandNewChat => 'محادثة جديدة';

  @override
  String get commandUntitledChat => 'محادثة بلا عنوان';

  @override
  String get commandArguments => 'المعاملات (اختياري)';

  @override
  String get commandRun => 'تشغيل';

  @override
  String get commandRunning => 'جارٍ البدء…';

  @override
  String get commandLocationChanged =>
      'تغيّر الخادم أو المشروع. أغلق هذا الحوار وافتح الأمر مجددًا.';

  @override
  String get refreshFailed => 'تعذّر التحديث';

  @override
  String get refreshRetry => 'إعادة المحاولة';

  @override
  String get globalSessionsLoadMore => 'تحميل المزيد من المحادثات';

  @override
  String get globalSessionsRefreshFailed => 'تعذّر تحديث المحادثات.';

  @override
  String get workspaceRetryProjects => 'إعادة المحاولة';

  @override
  String get historyLoadOlder => 'تحميل الرسائل الأقدم';

  @override
  String get historyReload => 'تحديث السجل الحديث';

  @override
  String get historyCursorExpired =>
      'تغيّر السجل الأقدم أو انتهت صلاحيته. حدّث السجل الحديث للمتابعة.';

  @override
  String get historyLoadedOnly =>
      'تُضمّن الرسائل المحمّلة فقط. حمّل السجل الأقدم لتضمين المزيد.';

  @override
  String get historyLoadedTotals => 'الاستخدام والسجل المحمّل';

  @override
  String get historyCopyLoadedReply => 'نسخ الرد المحمّل';

  @override
  String get historyLoadedMessages => 'الرسائل المحمّلة';

  @override
  String get historyLoadedCost => 'تكلفة الرسائل المحمّلة';

  @override
  String get historyServerTotalsNote =>
      'تشمل الصفوف الموسومة «أبلغ عنها الخادم» المحادثة كاملة. أما أعداد الرسائل والتقديرات الأخرى فتشمل السجل المحمّل.';

  @override
  String get sessionsReload => 'تحديث المحادثات الأخيرة';

  @override
  String get revertReviewChanged =>
      'تغيّرت هذه المحادثة أو التراجع المبدئي فيها. راجع أحدث حالة قبل المتابعة.';

  @override
  String get revertReviewLatest => 'مراجعة أحدث حالة';

  @override
  String get revertBusy => 'انتظر حتى ينتهي الإجراء الحالي في المحادثة.';

  @override
  String get revertClearAction => 'إلغاء التراجع المبدئي';

  @override
  String get revertPreviewUnavailable =>
      'لم يقدّم الخادم معاينة للملفات. لا يثبت ذلك ما إذا كانت الملفات قد تغيّرت.';

  @override
  String get revertStaged => 'طُبّق التراجع مبدئيًا';

  @override
  String get revertReview => 'مراجعة';

  @override
  String get revertFromHere => 'التراجع بدءًا من هذا الطلب';

  @override
  String get revertUndoDescription =>
      'تطبيق تراجع مبدئي ومراجعة الملفات المتأثرة';

  @override
  String get revertClearShortDescription => 'مراجعة التراجع المبدئي وإلغاؤه';

  @override
  String get revertPromptUnavailable =>
      'تعذّر تحميل الطلب الذي يبدأ منه التراجع.';

  @override
  String get revertPromptLoading => 'جارٍ تحميل الطلب الذي يبدأ منه التراجع…';

  @override
  String get revertAttachmentPrompt => 'طلب يتضمّن مرفقات فقط';

  @override
  String get revertResolveBeforeSending =>
      'راجع التراجع المبدئي، ثم ألغِه أو ثبّته نهائيًا قبل الإرسال. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteTitle => 'ملاحظة للوكيل';

  @override
  String get sessionNoteDescription =>
      'احفظ توجيهًا قصيرًا لهذه المحادثة. يسري حفظه أو حذفه في خطوة الوكيل التالية ويظهر عندها في سجل المحادثة. لا يؤدي ذلك إلى بدء التشغيل.';

  @override
  String get sessionNoteHint =>
      'مثلًا: اختصر الشرح ونفّذ الفحوص ذات الصلة قبل الانتهاء.';

  @override
  String get sessionNoteSave => 'حفظ الملاحظة';

  @override
  String get sessionNoteRemove => 'حذف الملاحظة المحفوظة';

  @override
  String get sessionNoteSaved => 'حُفظت الملاحظة';

  @override
  String get sessionNoteRemoved => 'حُذفت الملاحظة';

  @override
  String get sessionNotePending => 'تسري في خطوة الوكيل التالية.';

  @override
  String get sessionInstructionsUpdated => 'حُدّثت التعليمات';

  @override
  String get sessionInstructionsApplied =>
      'حُدّثت تعليمات الوكيل الخاصة بالمحادثة لهذه الخطوة.';

  @override
  String get sessionNoteUnsupported => 'لا يدعم هذا الخادم ملاحظات المحادثة.';

  @override
  String get sessionNoteAuthorization =>
      'تحقّق من كلمة مرور هذا الخادم وأذوناته، ثم حاول مجددًا. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteChanged =>
      'تغيّرت المحادثة أو تعليماتها. حدّث الملاحظة المحفوظة قبل الحفظ مجددًا. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteInvalid =>
      'صيغة الملاحظة المحفوظة لا تتيح لهذا المحرّر تعديلها بأمان.';

  @override
  String get sessionNoteTooLarge =>
      'اختصر الملاحظة لتناسب حد الحجم المسموح به على الخادم.';

  @override
  String get sessionNoteBusy =>
      'جارٍ حفظ تغيير في الملاحظة بالفعل. حاول مجددًا بعد انتهائه.';

  @override
  String get sessionNoteRefresh => 'تحديث الملاحظة المحفوظة';

  @override
  String get sessionNoteSavedVersion =>
      'الملاحظة المحفوظة حاليًا — راجعها قبل استبدالها';

  @override
  String get sessionNoteNone => 'لا توجد ملاحظة محفوظة';

  @override
  String get sessionNoteDiscard => 'هل تريد تجاهل تغييرات الملاحظة؟';

  @override
  String get sessionNoteKeepEditing => 'متابعة التحرير';

  @override
  String get sessionNoteDiscardAction => 'تجاهل التغييرات';

  @override
  String get sessionNoteDiscardDetail =>
      'ستضيع التعديلات التي أجريتها على هذه الملاحظة. لا يمكن التراجع عن ذلك.';

  @override
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit بايت';
  }

  @override
  String get usageTitle => 'الاستخدام والتكلفة';

  @override
  String get usageDescription =>
      'النشاط الذي سجّله خادم OpenCode هذا عبر محادثاتك.';

  @override
  String get usageRefresh => 'تحديث الاستخدام';

  @override
  String get usageToday => 'اليوم';

  @override
  String get usageThirtyDays => '30 يومًا';

  @override
  String get usageYear => 'هذا العام';

  @override
  String get usageAllTime => 'كل الفترات';

  @override
  String get usageScope => 'نطاق المشاريع';

  @override
  String get usageAllProjects => 'كل المشاريع';

  @override
  String get usageCurrentProject => 'المشروع الحالي';

  @override
  String get usageLoading => 'جارٍ تحميل الاستخدام';

  @override
  String get usageUnsupported =>
      'لا يدعم هذا الخادم إحصاءات الاستخدام المجمّعة.';

  @override
  String get usageProjectUnavailable =>
      'تعذّر تحديد المشروع الحالي. اختر «كل المشاريع» أو افتح مشروعًا أولًا.';

  @override
  String get usageTimezoneUnavailable =>
      'تعذّرت قراءة المنطقة الزمنية لهذا الجهاز. أعد المحاولة لتحميل الاستخدام بتواريخ صحيحة.';

  @override
  String get usageRefreshInterrupted =>
      'تغيّر الخادم أثناء تحميل الاستخدام. حدّث لإعادة المحاولة.';

  @override
  String get usageInvalidResponse =>
      'أعاد الخادم بيانات استخدام غير مكتملة. حدّث لإعادة المحاولة.';

  @override
  String get usageAuthorization =>
      'تحقّق من كلمة مرور هذا الخادم وأذوناته، ثم حدّث.';

  @override
  String get usagePreviousResult => 'تُعرض النتيجة السابقة لهذه المرشّحات.';

  @override
  String get usageLocationChanged =>
      'تغيّر الخادم النشط أو المشروع. افتح الاستخدام مجددًا من الإعدادات.';

  @override
  String get usageTinyCost => 'أقل من \$0.000001';

  @override
  String get usageSessions => 'المحادثات';

  @override
  String get usageSubagents => 'محادثات الوكلاء الفرعيين';

  @override
  String get usagePrompts => 'الطلبات';

  @override
  String get usageSteps => 'خطوات الوكيل';

  @override
  String get usageActiveDays => 'أيام النشاط';

  @override
  String get usageStreak => 'أطول فترة متواصلة · أيام';

  @override
  String get usageEmpty =>
      'لا يوجد نشاط في هذه الفترة. جرّب فترة أوسع أو «كل المشاريع».';

  @override
  String get usageTokens => 'الرموز';

  @override
  String get usageTotalTokens => 'الإجمالي';

  @override
  String get usageInput => 'الإدخال';

  @override
  String get usageOutput => 'الإخراج';

  @override
  String get usageReasoning => 'الاستدلال';

  @override
  String get usageCacheRead => 'قراءة ذاكرة التخزين المؤقت';

  @override
  String get usageCacheWrite => 'الكتابة في ذاكرة التخزين المؤقت';

  @override
  String get usageModels => 'استخدام النماذج';

  @override
  String get usageNoModels => 'لم يُسجّل استخدام للنماذج في هذه الفترة.';

  @override
  String get usageToolReliability => 'موثوقية الأدوات';

  @override
  String get usageToolsUnavailable =>
      'لا يتضمّن هذا الرد بيانات موثوقية الأدوات.';

  @override
  String get usageNoTools => 'لم تُسجّل استدعاءات للأدوات في هذه الفترة.';

  @override
  String get usageNoFinishedTools => 'لا توجد استدعاءات أدوات منتهية بعد.';

  @override
  String get usageToolCalls => 'الاستدعاءات';

  @override
  String get usageSucceeded => 'ناجحة';

  @override
  String get usageFailed => 'فاشلة';

  @override
  String get usageUnfinished => 'غير منتهية';

  @override
  String get usageCostDisclosure =>
      'التكاليف تقديرات يُبلّغ عنها OpenCode وليست فاتورة من مزوّد الخدمة. تُستبعد استدعاءات الأدوات غير المنتهية من نسبة النجاح.';

  @override
  String usagePeriod(String from, String to) {
    return '$from – $to';
  }

  @override
  String usageTimezone(String timezone) {
    return 'المنطقة الزمنية: $timezone';
  }

  @override
  String usageModelSteps(String steps) {
    return 'الخطوات: $steps';
  }

  @override
  String usageModelTokens(String tokens) {
    return 'الرموز: $tokens';
  }

  @override
  String usageSuccessRate(String rate) {
    return 'نجحت نسبة $rate من الاستدعاءات المنتهية';
  }

  @override
  String usageUpdated(String time) {
    return 'آخر تحديث في $time';
  }

  @override
  String get mcpRuntimeTitle => 'حتى إعادة تشغيل الخادم';

  @override
  String get mcpDefaultLocation => 'المجلد الافتراضي لخادم OpenCode';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return 'البيئة السحابية: $workspace';
  }

  @override
  String get mcpLocationChanged =>
      'تغيّر الخادم أو المشروع. لا تزال مسودتك هنا؛ افتح الإعداد مجددًا في المشروع المطلوب قبل الإضافة.';

  @override
  String get mcpAdding => 'جارٍ إضافة خادم MCP';

  @override
  String get mcpAdd => 'إضافة خادم MCP';

  @override
  String get mcpRuntimeEmpty =>
      'أضف أدوات للمشروع الحالي حتى إعادة تشغيل OpenCode.';

  @override
  String get mcpRuntimeAdded => 'أُضيف خادم MCP لهذا المشروع';

  @override
  String get mcpHeaderName => 'اسم الترويسة';

  @override
  String get mcpHeaderValue => 'قيمة الترويسة';

  @override
  String get mcpAddHeader => 'إضافة ترويسة أخرى';

  @override
  String get mcpRemoveHeader => 'إزالة الترويسة';

  @override
  String get sessionUnread => 'نتيجة غير مقروءة';

  @override
  String get shareSessionViewsTitle => 'مزامنة حالة القراءة';

  @override
  String get shareSessionViewsSaveError =>
      'تعذّر حفظ هذا التفضيل. مشاركة حالة القراءة معطّلة على هذا الجهاز حاليًا.';

  @override
  String get exportTitle => 'تصدير المحادثة';

  @override
  String get exportDescription => 'اختر صيغة لحفظ هذه المحادثة على جهازك.';

  @override
  String get exportJson => 'المحادثة كاملة · JSON';

  @override
  String get exportJsonDescription =>
      'تنزيل المحادثة كاملة من الخادم، بما فيها الرسائل الأقدم.';

  @override
  String get exportMarkdown => 'سجل سهل القراءة · Markdown';

  @override
  String get exportMarkdownDescription =>
      'حفظ الرسائل المحمّلة حاليًا في هذه المحادثة. حمّل الرسائل الأقدم أولًا إن أردت تضمينها.';

  @override
  String get exportRedact => 'حجب البيانات الحساسة';

  @override
  String get exportUnredacted =>
      'قد يحتوي الملف غير المحجوب على أسرار ومسارات محلية ومخرجات أدوات خاصة.';

  @override
  String get exportCancel => 'إلغاء التنزيل';

  @override
  String get exportDownloading => 'جارٍ تنزيل المحادثة كاملة…';

  @override
  String get exportSaving => 'جارٍ حفظ الملف…';

  @override
  String get exportSaved => 'حُفظت المحادثة';

  @override
  String get exportChanged =>
      'تغيّر الخادم أو المشروع. افتح التصدير مجددًا من المحادثة المطلوبة.';

  @override
  String get exportUnsupported =>
      'لا يدعم هذا الخادم التصدير بصيغة JSON. لا يزال بإمكانك حفظ السجل المحمّل بصيغة Markdown.';

  @override
  String get exportAuthorization =>
      'رفض الخادم الوصول. تحقّق من بيانات اعتماد الخادم وحاول مجددًا.';

  @override
  String get exportMissing =>
      'لم تعد هذه المحادثة موجودة على الخادم. لا يزال بإمكانك حفظ السجل المحمّل بصيغة Markdown.';

  @override
  String get exportFailed =>
      'تعذّر تصدير المحادثة. تحقّق من الاتصال ومساحة التخزين، ثم حاول مجددًا.';

  @override
  String get importTitle => 'استيراد محادثة';

  @override
  String get importDescription =>
      'استعد ملفًا مصدّرًا بصيغة JSON إلى خادم OpenCode هذا. اختر ملفًا، ثم راجع وجهة استيراده.';

  @override
  String get importChoose => 'اختيار ملف JSON';

  @override
  String get importChooseAnother => 'اختيار ملف آخر';

  @override
  String get importAction => 'استيراد المحادثة';

  @override
  String get importUntitled => 'محادثة بلا عنوان';

  @override
  String get importRedacted =>
      'يحتوي هذا الملف على نصوص بديلة لبيانات محجوبة. لا يمكن للاستيراد استعادة النص الأصلي؛ استخدم ملفًا مصدّرًا دون حجب إذا كنت تحتاجه.';

  @override
  String get importParent =>
      'بدأت هذه المحادثة من محادثة أخرى يجب أن تكون موجودة على هذا الخادم. استورد تلك المحادثة أولًا.';

  @override
  String get importArchived =>
      'هذه المحادثة مؤرشفة. ستبقى مؤرشفة بعد الاستيراد.';

  @override
  String get importDestination => 'الاستيراد إلى';

  @override
  String get importChooseDestination => 'اختيار مشروع على هذا الخادم';

  @override
  String get importNoDestinations =>
      'لا تتوفر مشاريع. افتح مشروعًا على هذا الخادم، ثم حاول مجددًا.';

  @override
  String get importDestinationFailed =>
      'تعذّر تحميل مشاريع الوجهة أو بيئاتها السحابية. حاول مجددًا؛ لا يزال ملفك محدّدًا.';

  @override
  String get importPreserves =>
      'لن يتغيّر الملف المصدر. لن تُستبدل أي محادثة موجودة، ولن يبدأ تشغيل وكيل بسبب الاستيراد.';

  @override
  String get importReading => 'جارٍ تجهيز الاستيراد…';

  @override
  String get importSending => 'جارٍ استيراد المحادثة…';

  @override
  String get importSucceeded => 'استُوردت المحادثة';

  @override
  String get importOpen => 'فتح المحادثة';

  @override
  String get importOpenFailed =>
      'استُوردت المحادثة، لكن تعذّر فتحها. ابحث عنها في «كل المحادثات» على خادم الوجهة.';

  @override
  String get importChanged =>
      'تغيّر الخادم أو المشروع. لا يزال ملفك هنا. افتح الاستيراد مجددًا على الخادم المطلوب قبل المتابعة.';

  @override
  String get importUnsupported => 'لا يدعم هذا الخادم الاستيراد بصيغة JSON.';

  @override
  String get importInvalidFile =>
      'اختر ملف OpenCode صالحًا مصدّرًا بصيغة JSON ويحتوي على معلومات المحادثة وسجلات الرسائل. لا يمكن استيراد سجلات Markdown.';

  @override
  String get importTooLarge =>
      'يتجاوز هذا الملف حد الاستيراد على الهاتف البالغ 128 MiB. لم يُرفع الملف ولم يُقتطع منه شيء. انقله باستخدام حاسوب أو خادم.';

  @override
  String get importConflict =>
      'توجد محادثة بهذا المعرّف على هذا الخادم بالفعل. لم يُستبدل شيء. ابحث عنها في «كل المحادثات»، أو استورد هذا الملف على خادم آخر.';

  @override
  String get importAuthorization =>
      'رفض الخادم الوصول. تحقّق من بيانات اعتماد الخادم. لا يزال ملفك محدّدًا.';

  @override
  String get importParentMissing =>
      'المحادثة الأم غير موجودة على هذا الخادم. استورد المحادثة الأم أولًا، ثم أعد محاولة استيراد هذا الملف.';

  @override
  String get importRejected =>
      'رفض الخادم صيغة هذا الملف المصدّر. لا يزال ملفك محدّدًا؛ تحقّق من أنه صادر عن خادم OpenCode متوافق.';

  @override
  String get importUnconfirmed =>
      'تعذّر تأكيد الاستيراد. تحقّق من «كل المحادثات» قبل إعادة المحاولة؛ فقد يكون الخادم قد استلمه. لم يتغيّر الملف المصدر.';

  @override
  String get sessionPinsLoadFailed =>
      'تعذّر تحميل بعض المحادثات المثبّتة. حدّث لإعادة المحاولة.';

  @override
  String get promptStashSaveFailed =>
      'تعذّر حفظ هذا الطلب. لم يتغيّر محتوى محرّر الرسالة. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get promptOriginalDraft => 'استعادة المسودة الأصلية';

  @override
  String get promptStashTitle => 'الطلبات المحفوظة';

  @override
  String get promptStashSearch => 'البحث في الطلبات المحفوظة';

  @override
  String get promptStashDeleteFailed =>
      'تعذّر حذف هذا الطلب المحفوظ. حاول مجددًا.';

  @override
  String get promptStashDelete => 'حذف';

  @override
  String get promptStashFull =>
      'لديك 50 طلبًا محفوظًا. احذف طلبًا محفوظًا لتوفير مساحة؛ لم يتغيّر طلبك الحالي.';

  @override
  String promptStashAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مرفق',
      many: '$count مرفقًا',
      few: '$count مرفقات',
      two: 'مرفقان',
      one: 'مرفق واحد',
      zero: 'لا توجد مرفقات',
    );
    return '$_temp0';
  }

  @override
  String promptStashReferences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مرجع',
      many: '$count مرجعًا',
      few: '$count مراجع',
      two: 'مرجعان',
      one: 'مرجع واحد',
      zero: 'لا توجد مراجع',
    );
    return '$_temp0';
  }

  @override
  String get promptHistorySaveFailed =>
      'أُرسل الطلب، لكن تعذّر حفظه في السجل على هذا الجهاز.';

  @override
  String get promptStashMigrationPending =>
      'تعذّر نقل بعض المرفقات المحفوظة إلى تخزين المرفقات المحلي حتى الآن. احتُفظ بمحتواك المحفوظ. وفّر مساحة على الجهاز وأعد المحاولة.';

  @override
  String get commonRetry => 'إعادة المحاولة';

  @override
  String get shareWaitingForServer =>
      'اتصل بخادم لفتح النص المشترك في محادثة جديدة.';

  @override
  String get webSourcesDisclosure =>
      'لا يستطيع هذا الخادم البحث في الويب. الصق رابطًا عامًا، ومقتطفًا إن شئت. لن تُفتح الصفحة، ولن يُرسل شيء قبل أن ترسل رسالتك.';

  @override
  String get webSourcesScopeChanged =>
      'تغيّر الخادم. أغلق «إضافة مصدر ويب» وافتحه مجددًا.';

  @override
  String get webSourcesUrl => 'رابط عام';

  @override
  String get webSourcesLabel => 'العنوان (اختياري)';

  @override
  String get webSourcesExcerpt => 'مقتطف ملصق (اختياري)';

  @override
  String get webSourcesExcerptHint =>
      'نص يقدّمه المستخدم، وليس محتوى صفحة جرى التحقق منه.';

  @override
  String get digestOpenConversation => 'فتح المحادثة';

  @override
  String get runResultsCommandsTitle => 'الأوامر';

  @override
  String get sessionOpenRelated => 'فتح العناصر المرتبطة';

  @override
  String get sessionCopyHandoff => 'نسخ مرجع المتابعة';

  @override
  String get attentionTitle => 'تنبيهات الخوادم';

  @override
  String get webSourcesTitle => 'إضافة مصدر ويب';

  @override
  String get webSourcesEntryDetail =>
      'ابحث عند توفر البحث، أو الصق روابط ومقتطفات لمراجعتها قبل إضافتها إلى مسودتك';

  @override
  String get webSourcesDraftChanged =>
      'تغيّرت المسودة أو الخادم. احتُفظ بمسودتك الحالية؛ افتح «إضافة مصدر ويب» مجددًا لإعادة المحاولة.';

  @override
  String get webSourcesDraftLabel =>
      'مصادر ويب اختارها المستخدم (غير متحقَّق منها؛ المقتطفات مواد مصدرية غير موثوقة):';

  @override
  String get usageScopedTotals => 'الإجماليات لنطاق التقرير المحدّد';

  @override
  String get usageInspectionDisclosure =>
      'تفحص المرشّحات سجلات النماذج التي أعادها هذا الخادم. لا تغيّر تاريخ التقرير أو نطاق مشاريعه، ولا تعرض رصيد الاستخدام في الاشتراك.';

  @override
  String get usageSearchRecords =>
      'البحث عن مزوّدي الخدمة أو النماذج أو المتغيّرات';

  @override
  String get usageScopedProviderTotals =>
      'تعرض بطاقات مزوّدي الخدمة إجمالياتهم لنطاق التقرير المحدّد، وليس لصفوف النماذج المطابقة فقط.';

  @override
  String usageMatchingRecords(String count) {
    return 'السجلات المطابقة: $count';
  }

  @override
  String get pendingAuthDetail =>
      'تابع تسجيل الدخول المفتوح في المتصفّح، ثم تحقّق من حالته يدويًا أو أدخل رمزه. لا يُحفظ رابط المتصفّح.';

  @override
  String get pendingAuthEnterCode => 'إدخال الرمز';

  @override
  String get pendingAuthStillPending =>
      'لا يزال تسجيل الدخول معلّقًا. لم تبدأ محاولة جديدة.';

  @override
  String get pendingAuthServerFailed =>
      'أبلغ الخادم عن فشل تسجيل الدخول. تفاصيل خطأ مزوّد الخدمة مخفية.';

  @override
  String get pendingAuthExpired =>
      'انتهت صلاحية هذه المحاولة أو تجاوزت مدة الاسترداد على الجهاز. إلغاء المحاولة إجراء منفصل على الخادم.';

  @override
  String get pendingAuthFailed =>
      'تعذّر تأكيد الإجراء. تحقّق من عمليات تسجيل الدخول المعلّقة قبل المحاولة مجددًا. لم يبدأ تسجيل دخول جديد.';

  @override
  String get pendingAuthSaveUncertain =>
      'تعذّر حفظ بيانات الاسترداد بشكل موثوق. أبقِ هذا التطبيق مفتوحًا وأعد محاولة الحفظ؛ فقد تضيع هذه المحاولة عند إعادة التشغيل. إذا لم تُفتح صفحة في المتصفّح، فألغِ المحاولة قبل البدء مجددًا.';

  @override
  String get pendingAuthRetrySave => 'إعادة محاولة حفظ بيانات الاسترداد';

  @override
  String get pendingAuthForget => 'إزالة السجل من هذا الجهاز';

  @override
  String get pendingAuthUnsupported =>
      'لا يتيح هذا الخادم استرداد عمليات تسجيل الدخول السابقة. تعمل عمليات تسجيل الدخول القديمة فقط ما دامت الشاشة والاتصال الأصليان متاحين.';

  @override
  String get pendingAuthOtherSource =>
      'تتبع عمليات تسجيل الدخول المعلّقة الأخرى خادمًا أو مشروعًا آخر. ارجع إلى مصدرها الأصلي لإدارتها.';

  @override
  String get connectionHelpGuideTip =>
      'أبقِ الخادم بعيدًا عن الإنترنت العام. استخدم HTTPS خاصًا أو نفقًا مشفّرًا ينتهي على الجهاز الذي يشغّل هذا التطبيق. يشير localhost على حاسوبك إلى غير ما يشير إليه على هاتفك. افتح مساعدة الاتصال أعلاه للاطّلاع على الخطوات والأمثلة.';

  @override
  String get voiceConversationTitle => 'محادثة صوتية';

  @override
  String get voiceConversationDescription =>
      'استمع وراجع، ثم أرسل. لا يبدأ الاستماع تلقائيًا؛ تُقرأ الردود بصوت عالٍ فقط إذا فعّلت ذلك.';

  @override
  String get voiceConversationReplyReviewNeeded =>
      'اكتمل الرد، لكن تعذّر التأكد من ارتباطه برسالتك. يمكنك قراءته إن أردت.';

  @override
  String get voiceConversationReplyInterrupted =>
      'احتاج الرد إلى قرار على الشاشة، لذلك لم يُقرأ تلقائيًا.';

  @override
  String get voiceConversationReplyNoProse =>
      'لا يتضمّن الرد نصًا لقراءته. لا تُقرأ الشيفرة ولا تفاصيل الأدوات بصوت عالٍ.';

  @override
  String get voiceConversationReplyFailed => 'تعذّرت قراءة الرد بصوت عالٍ.';

  @override
  String get voiceConversationPausedDetail =>
      'المحادثة الصوتية متوقفة مؤقتًا. أعد الاتصال أو انتظر الرد أو راجع القرارات المنتظرة على الشاشة.';

  @override
  String get voiceConversationDraftFirst =>
      'أرسل مسودتك الحالية أو احفظها أو امسحها قبل بدء المحادثة الصوتية.';

  @override
  String get voiceConversationCommandsOnly =>
      'استخدم محرّر الرسالة النصي للأوامر التي تبدأ بشرطة مائلة.';

  @override
  String get voiceInputUnavailable =>
      'الإدخال الصوتي غير متاح. تحقّق من إعدادات النموذج المحلي والميكروفون.';

  @override
  String get desktopDropFailedTitle => 'تعذّر إرفاق الملفات المُسقطة';

  @override
  String get desktopDropFailedRecovery =>
      'تحقّق من المرفقات المضافة بالفعل قبل المحاولة مجددًا. يمكنك أيضًا استخدام لوحة المفاتيح لفتح «إضافة» ثم «إرفاق ملف».';

  @override
  String get commandAuthMethodHint =>
      'تشغيل طريقة تسجيل الدخول الخاصة بمزوّد الخدمة على الخادم المحدّد، وليس على هذا الهاتف. قد تحتاج إلى إتمام خطوات تفاعلية على الخادم.';

  @override
  String get commandAuthStart => 'بدء تسجيل الدخول على الخادم';

  @override
  String get commandAuthPending =>
      'تسجيل الدخول معلّق على الخادم. أكمل أي تفاعل مطلوب على الخادم، ثم تحقّق من حالته. إغلاق هذه اللوحة لا يلغيه.';

  @override
  String get commandAuthCancel => 'إلغاء تسجيل الدخول';

  @override
  String get commandAuthFailed =>
      'تعذّر إتمام تسجيل الدخول على الخادم أو تأكيده. تحقّق من المحاولة الحالية قبل بدء أخرى.';

  @override
  String get commandAuthComplete =>
      'أبلغ الخادم عن اكتمال تسجيل الدخول. حدّث مزوّدي الخدمة للاطّلاع على الاتصالات الحالية.';

  @override
  String get commandAuthExpired =>
      'انتهت صلاحية محاولة تسجيل الدخول هذه. يمكنك بدء محاولة جديدة.';

  @override
  String get commandAuthScopeChanged =>
      'تغيّر الخادم أو المشروع. ارجع إلى المشروع الأصلي وافتح تسجيل الدخول مجددًا لإدارة محاولته.';

  @override
  String get commandAuthUncertainStart =>
      'ربما بدأ الخادم تسجيل الدخول، لكن تعذّر على التطبيق استرداد محاولته بأمان. تحقّق على الخادم قبل إعادة المحاولة؛ مُنع البدء التلقائي مجددًا لتجنّب تكرار العمليات.';

  @override
  String get readAloudAction => 'قراءة نص الرد بصوت عالٍ';

  @override
  String get readAloudStop => 'إيقاف القراءة بصوت عالٍ';

  @override
  String get readAloudOtherVoice => 'القراءة بصوت آخر';

  @override
  String get readAloudChooseVoice => 'اختيار صوت للقراءة';

  @override
  String get readAloudConsentTitle => 'هل تريد استخدام محرّك النطق في النظام؟';

  @override
  String get readAloudConsentDetail =>
      'سيُرسل نص الرد المحمّل إلى محرّك النطق في نظامك. تُعرض فقط الأصوات الموسومة بأنها تعمل دون اتصال، لكن المحرّك برنامج مستقل وتسري ممارسات الخصوصية الخاصة به. تُستبعد كتل الشيفرة وتفاصيل الأدوات. قد يسمع الآخرون الصوت. تتوقف القراءة عند فتح شاشة تغطي هذه المحادثة أو انتقال التطبيق إلى الخلفية.';

  @override
  String get readAloudContinue => 'اختيار صوت';

  @override
  String get readAloudUnsupported =>
      'القراءة بصوت عالٍ غير متاحة على هذه المنصة.';

  @override
  String get readAloudNoVoice =>
      'لا يتوفر صوت مثبّت موسوم بأنه يعمل دون اتصال. أعدّ صوتًا يعمل دون اتصال في إعدادات النطق بالنظام وحاول مجددًا.';

  @override
  String get readAloudUnavailable =>
      'تعذّر على محرّك النطق قراءة هذا الرد. حاول مجددًا أو اختر صوتًا آخر.';

  @override
  String get readAloudTooLong =>
      'هذا الرد أطول من أن يُقرأ بصوت عالٍ. اختر ردًا أقصر.';

  @override
  String get readAloudBusy =>
      'لا تتاح القراءة الصوتية أثناء التقاط الصوت أو مقاطعة صوتية أخرى.';

  @override
  String get readAloudNoProse =>
      'لا يوجد نص رد لقراءته. لا تُقرأ الشيفرة ولا تفاصيل الأدوات بصوت عالٍ.';

  @override
  String get credentialMetadataOnly =>
      'تُعرض تسميات الحسابات المحفوظة فقط. تبقى مفاتيح API ورموز تسجيل الدخول على خادمك.';

  @override
  String get credentialActiveUpdated => 'حُدّث الحساب النشط من الخادم.';

  @override
  String get credentialSwitchRequested =>
      'طُلب تبديل الحساب. لم يؤكَّد هذا الطلب بحدث من الخادم بعد.';

  @override
  String get credentialActive => 'نشط';

  @override
  String get credentialRename => 'إعادة تسمية الحساب';

  @override
  String get credentialLabel => 'تسمية الحساب';

  @override
  String get credentialSave => 'حفظ التسمية';

  @override
  String get credentialScopeChanged =>
      'تغيّر الخادم أو المشروع. أغلق إدارة الحسابات وافتحها مجددًا قبل إجراء تغييرات.';

  @override
  String get credentialProviderMissing =>
      'لم يعد مزوّد الخدمة هذا في قائمة تكاملات الخادم.';

  @override
  String get credentialLoadFailed =>
      'تعذّر تحديث الحسابات المحفوظة. حاول مجددًا.';

  @override
  String get credentialMutationFailed =>
      'تعذّر تأكيد تغيير الحساب. حدّث قبل إعادة المحاولة؛ فقد يكون الخادم قد طبّقه بالفعل.';

  @override
  String get credentialRefresh => 'تحديث الحسابات';

  @override
  String get credentialEmpty => 'لم يُبلّغ عن حسابات محفوظة لمزوّد الخدمة هذا.';

  @override
  String get credentialEnvironment => 'تديره بيئة الخادم. لا يمكن إزالته هنا.';

  @override
  String credentialUnnamed(int index) {
    return 'حساب محفوظ $index';
  }

  @override
  String get mcpRemoveFailed =>
      'تعذّر تأكيد إزالة MCP. حدّث القائمة قبل المحاولة مجددًا؛ فقد يكون الخادم قد طبّق التغيير بالفعل.';

  @override
  String get mcpLoadFailed => 'تعذّر تحديث بيانات MCP. حاول مجددًا.';

  @override
  String get mcpSavedStatus => 'محفوظ في OpenCode';

  @override
  String get mcpRetryReconnect => 'إعادة محاولة الاتصال';

  @override
  String get mcpReconnecting => 'جارٍ إعادة الاتصال';

  @override
  String get mcpStillDisconnected => 'لا يزال OpenCode غير متصل. حاول مجددًا.';

  @override
  String get mcpScopeChanged =>
      'تغيّر الخادم أو المشروع. حدّث لتحميل خوادم MCP الخاصة به قبل إجراء تغييرات.';

  @override
  String get promptStashRestoreFailed =>
      'تعذّر إتمام استعادة الطلب. لا تزال النسخ المحفوظة متاحة؛ تحقّق من محرّر الرسالة قبل المحاولة مجددًا.';

  @override
  String get promptStashContextOnly => 'المرفقات والمراجع';

  @override
  String get promptDefaultLocation => 'المجلد الافتراضي للخادم';

  @override
  String get promptStashed => 'أُضيف الطلب إلى طلباتك المحفوظة.';

  @override
  String get promptStashedDraftPending =>
      'أُضيف الطلب إلى طلباتك المحفوظة. لا تزال مسودة محرّر الرسالة بحاجة إلى الحفظ؛ أعد المحاولة من تنبيه المسودة.';

  @override
  String get promptStashReadFailed =>
      'تعذّرت قراءة الطلبات المحفوظة. احتُفظ ببياناتها المخزّنة.';

  @override
  String get promptStashDescription =>
      'حفظ النص والمرفقات والمراجع لاستخدامها لاحقًا';

  @override
  String get promptRestored => 'استُعيد الطلب المحفوظ';

  @override
  String promptStashLocation(String directory) {
    return 'يشير هذا الطلب إلى ملفات في $directory. انتقل إلى مشروعه الأصلي قبل استعادته.';
  }

  @override
  String get promptStashScopeChanged =>
      'تغيّر الخادم أو المشروع. أغلق الطلبات المحفوظة وافتحها مجددًا.';

  @override
  String get transcriptFindHint => 'البحث في المحادثة';

  @override
  String get transcriptFindClose => 'إغلاق البحث';

  @override
  String get transcriptFindPrevious => 'التطابق السابق';

  @override
  String get transcriptFindNext => 'التطابق التالي';

  @override
  String get transcriptFindNone => 'لا توجد تطابقات';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: 'التطابق $current من $total',
      many: 'التطابق $current من $total',
      few: 'التطابق $current من $total',
      two: 'التطابق $current من $total',
      one: 'تطابق واحد',
      zero: 'لا توجد تطابقات',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'الرسائل المحمّلة فقط. حمّل الرسائل الأقدم لتوسيع البحث.';

  @override
  String get transcriptFindReasoning => 'الاستدلال';

  @override
  String get transcriptFindTool => 'بيانات الأدوات';

  @override
  String get transcriptFindFile => 'اسم الملف';

  @override
  String get transcriptFindAll => 'البحث في السجل كاملًا';

  @override
  String get skillUse => 'إضافة إلى المحادثة';

  @override
  String get skillActivationHelp =>
      'تُضاف تعليمات هذه المهارة إلى المحادثة. تبقى مسودتك غير المرسلة في محرّر الرسالة.';

  @override
  String get skillRunNow => 'تشغيل الوكيل الآن';

  @override
  String get skillRunHelp => 'عطّل هذا الخيار لإضافة المهارة دون بدء رد آخر.';

  @override
  String get skillLocationChanged =>
      'تغيّر الخادم أو المشروع. افتح المهارات مجددًا من المحادثة.';

  @override
  String get skillUnsupported =>
      'تفعيل المهارات غير متاح على هذا الخادم. لا يزال بإمكانك معاينتها.';

  @override
  String get skillStaged => 'احسم التراجع المبدئي في المحادثة قبل إضافة مهارة.';

  @override
  String get skillBusy => 'جارٍ إضافة مهارة إلى هذه المحادثة بالفعل.';

  @override
  String get skillUncertain =>
      'لم يؤكّد الخادم النتيجة. قد تكون المهارة قد أُضيفت. أغلق هذه اللوحة وتحقّق من المحادثة قبل المحاولة مجددًا.';

  @override
  String get skillApplied => 'أُضيفت المهارة إلى هذه المحادثة.';

  @override
  String get skillAppliedOriginal =>
      'أُضيفت المهارة إلى المحادثة الأصلية. أغلق هذه اللوحة للعودة.';

  @override
  String get activeContextTitle => 'السياق النشط';

  @override
  String get activeContextSubtitle => 'فحص الرسائل بعد اختصار السياق';

  @override
  String get activeContextRefresh => 'تحديث السياق النشط';

  @override
  String get activeContextSearch => 'البحث في الرسائل النشطة';

  @override
  String get activeContextEmpty => 'لم يُعد الخادم أي رسائل سياق نشط.';

  @override
  String get activeContextNoText => 'لا يوجد محتوى نصي مدعوم في هذا العنصر.';

  @override
  String get activeContextUnsupported =>
      'فحص السياق النشط غير متاح على هذا الخادم.';

  @override
  String get activeContextChanged =>
      'تغيّر الخادم أو المشروع أو المحادثة. افتح أداة الفحص هذه مجددًا من المحادثة.';

  @override
  String get activeContextInvalid =>
      'أعاد الخادم لقطة سياق غير صالحة. حدّث لإعادة المحاولة.';

  @override
  String activeContextRefreshFailed(String error) {
    return 'تُعرض اللقطة السابقة. تعذّر التحديث: $error';
  }

  @override
  String get activeContextContentHelp =>
      'لقطة لمحتوى الرسائل المتاح. لا تُعرض محتويات المرفقات الثنائية أو الروابط أو البيانات الوصفية الداخلية. لا يمثّل ذلك الطلب الكامل المرسل إلى مزوّد الخدمة.';

  @override
  String get activeContextUser => 'طلب المستخدم';

  @override
  String get activeContextAssistant => 'المساعد';

  @override
  String get activeContextSystem => 'تعليمات النظام';

  @override
  String get activeContextSynthetic => 'رسالة مُنشأة آليًا';

  @override
  String get activeContextSkill => 'مهارة';

  @override
  String get activeContextShell => 'الصدفة';

  @override
  String get activeContextCompaction => 'اختصار السياق';

  @override
  String get activeContextChange => 'تغيير المحادثة';

  @override
  String get activeContextText => 'نص';

  @override
  String get activeContextToolInput => 'مدخلات الأداة';

  @override
  String get activeContextToolOutput => 'مخرجات الأداة';

  @override
  String get activeContextFile => 'ملف مرفق';

  @override
  String get activeContextNotice => 'إشعار من الخادم';

  @override
  String get activeContextPruned => 'حذف الخادم المحتوى';

  @override
  String get activeContextTruncated => 'اقتطع الخادم المخرجات';

  @override
  String get draftSaveFailed => 'لم تُحفظ المسودة. انسخ نصك أو أعد المحاولة.';

  @override
  String get draftStorageFull =>
      'مساحة تخزين المسودات ممتلئة. انسخ نصك قبل المغادرة.';

  @override
  String get draftProfileRemoved =>
      'أُزيل الخادم الأصلي. انسخ مسودتك للاحتفاظ بها.';

  @override
  String get draftRetrySave => 'إعادة محاولة حفظ المسودة';

  @override
  String get draftClearFailed =>
      'تعذّر مسح المسودة المحفوظة. أعد المحاولة قبل المغادرة.';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => 'تعذّر حفظ المسودة';

  @override
  String get draftLeaveMessage =>
      'تابع التحرير لنسخ نصك أو إعادة محاولة الحفظ. قد تؤدي المغادرة الآن إلى فقدان تغييراتك غير المحفوظة.';

  @override
  String get draftLeaveAction => 'مغادرة دون حفظ';

  @override
  String get draftKeepEditing => 'متابعة التحرير';

  @override
  String get draftUnsaved => 'غير محفوظة';

  @override
  String get draftAttachmentsLocal =>
      'تُحفظ المرفقات مع هذه المسودة على هذا الجهاز.';

  @override
  String get draftAttachmentsFailed =>
      'تحتاج المرفقات إلى استرداد أو تعذّر حفظها. أعد المحاولة قبل الإرسال.';

  @override
  String get draftAttachmentRecoveryTitle => 'بعض المرفقات بحاجة إلى مراجعة';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return 'هذه المرفقات المحفوظة مفقودة أو غير قابلة للقراءة أو تتبع مشروعًا آخر: $names. استخدم المرفقات المتاحة وأزل هذه من المسودة، أو احتفظ بالمسودة المحفوظة وحاول مجددًا لاحقًا.';
  }

  @override
  String get draftUseAvailableAttachments => 'استخدام المرفقات المتاحة';

  @override
  String get draftKeepSavedAttachments => 'الاحتفاظ بالمسودة المحفوظة';

  @override
  String get photoLibraryAction => 'مكتبة الصور';

  @override
  String get photoLibraryDescription => 'اختيار صورة أو لقطة شاشة';

  @override
  String get photoCameraAction => 'التقاط صورة';

  @override
  String get photoTooLarge => 'اختر صورة أصغر من 10 MB.';

  @override
  String get photoStorageFailed =>
      'تعذّر حفظ الصورة على هذا الجهاز. وفّر بعض المساحة وأعد المحاولة.';

  @override
  String get photoPendingOther =>
      'لا تزال صورة تنتظر محادثة أخرى. أضفها أو تجاهلها هناك، ثم حاول مجددًا.';

  @override
  String get photoUnavailable =>
      'تعذّر فتح الصورة. حاول إضافتها مجددًا من مكتبة الصور أو عبر التقاط صورة.';

  @override
  String get photoPermissionDenied =>
      'رُفض الوصول إلى الصور. اسمح بالوصول إلى الكاميرا أو الصور في إعدادات تطبيق Android، ثم حاول مجددًا.';

  @override
  String get photoPendingTitle => 'صورة منتظرة';

  @override
  String get photoDiscard => 'تجاهل الصورة المنتظرة';

  @override
  String get photoAddToDraft => 'إضافة الصورة المستردّة إلى المسودة';

  @override
  String get photoOtherLocation =>
      'ارجع إلى الخادم والمشروع الأصليين للصورة قبل إضافتها.';

  @override
  String get photoDraftFull =>
      'أزل مرفقًا أولًا. تتسع المسودة لما يصل إلى 5 ملفات بإجمالي 20 MB.';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

  @override
  String get iosRemoteSummary =>
      'عميل اتصال بخادم OpenCode الذي تختاره. استضافة الخادم على الجهاز والمراقبة في الخلفية غير متاحتين في إصدار iOS هذا.';

  @override
  String get iosKeychainGuide =>
      'تُحفظ كلمات مرور الخوادم في Keychain على هذا الجهاز، ولا تُخزَّن في تفضيلات التطبيق العادية.';

  @override
  String get platformSecureStorageGuide =>
      'تُحفظ كلمات مرور الخوادم في مخزن بيانات الاعتماد الآمن لهذه المنصة، ولا تُخزَّن في تفضيلات التطبيق العادية.';

  @override
  String get usageProviders => 'مزوّدو الخدمة';

  @override
  String get usageProviderScope =>
      'إجماليات سجلات النماذج التي أعادها هذا الخادم للنطاق المحدد. لا تمثل فوترة مزوّد الخدمة أو أرصدة الاشتراكات.';

  @override
  String usageProviderModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نموذج',
      many: '$count نموذجًا',
      few: '$count نماذج',
      two: 'نموذجان',
      one: 'نموذج واحد',
      zero: 'لا نماذج',
    );
    return '$_temp0';
  }

  @override
  String get usageProviderCostUnavailable => 'الإجمالي الفرعي للتكلفة غير متاح';

  @override
  String usageProviderCostShare(String percent) {
    return '$percent من التكلفة المبلّغ عنها';
  }

  @override
  String get setupOutputWaiting => 'بانتظار مخرجات Termux…';

  @override
  String get uncertainAuthDetail =>
      'ربما بدأ الخادم تسجيل الدخول، لكن لم يصل معرّف المحاولة. تحقّق على الخادم قبل البدء مجددًا.';

  @override
  String get uncertainAuthCloseHint =>
      'أغلق هذه اللوحة واستخدم صف تسجيل الدخول غير المؤكد لإزالة منع إعادة المحاولة المحلي بعد التحقق من الخادم.';

  @override
  String get pluginsUnsupported => 'لا يدعم هذا الخادم فحص الإضافات.';

  @override
  String get pluginsDisconnected => 'اتصل بخادم لفحص إضافاته.';

  @override
  String get pluginsEmpty => 'لم يُبلّغ عن إضافات لهذا المشروع.';

  @override
  String get pluginsLoadFailed => 'تعذّر تحميل الإضافات. أعد المحاولة.';

  @override
  String get pluginsRetry => 'إعادة المحاولة';

  @override
  String get pluginsUnnamed => 'إضافة بلا معرّف';

  @override
  String get pluginsStatusActive => 'نشطة';

  @override
  String get pluginsStatusUnknown => 'حالة غير معروفة';

  @override
  String get pluginsSourceBuiltin => 'مدمجة';

  @override
  String get pluginsSourcePackage => 'حزمة';

  @override
  String get pluginsSourceLocal => 'ملف محلي (المسار مخفي)';

  @override
  String get pluginsSourceSdk => 'SDK';

  @override
  String get pluginsSourceUnknown => 'مصدر غير معروف';

  @override
  String get pluginsTerminalUi => 'تعلن عن واجهة طرفية';

  @override
  String get pluginsFailureDetail =>
      'تفاصيل الفشل مخفية لأنها قد تتضمن بيانات اعتماد.';

  @override
  String get demoReviewChanges => 'مراجعة التغييرات';

  @override
  String get demoSetUpServer => 'إعداد خادمك الخاص';

  @override
  String get handoffCopyCommand => 'نسخ الأمر';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get managedHealthLifetime =>
      'قد يوقف Android أيًا من التطبيقين. إبقاء اتصال تطبيق الهاتف نشطًا لا يضمن استمرار خادم Termux طوال الليل.';

  @override
  String get quotaBudgetOff => 'متوقف';

  @override
  String get quotaBudgetSaveFailed =>
      'تعذّر حفظ تغيير الميزانية. تظل آخر إعدادات محفوظة سارية.';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get usageBudgetTitle => 'ميزانيات الاستهلاك الشخصية';

  @override
  String get usageBudgetDescription =>
      'تستخدم الميزانيات كل الاستهلاك المبلّغ عنه للخادم والمشروع والمنطقة الزمنية وبداية الفترة المحددة. لا تغيّرها مرشحات النماذج. تحتاج بداية فترة جديدة إلى ميزانية جديدة. لا تغيّر هذه الميزانيات أرصدة الاشتراك ولا توقف الطلبات.';

  @override
  String get usageBudgetUsd => 'تحديد ميزانية بالدولار الأمريكي';

  @override
  String get usageBudgetTokens => 'تحديد ميزانية الرموز';

  @override
  String get usageBudgetAmount => 'قيمة الميزانية';

  @override
  String get usageBudgetRemove => 'إزالة الميزانية';

  @override
  String usageBudgetProgress(String used, String limit, String unit) {
    return '$used من $limit $unit';
  }

  @override
  String get usageBudgetTokenUnit => 'رمز';

  @override
  String get usageBudgetReached => 'بُلغت الميزانية الشخصية في هذه القراءة.';

  @override
  String get usageBudgetPrevious =>
      'بلغت القراءة السابقة هذه الميزانية. حدّث للتحقق من الاستهلاك الحالي.';

  @override
  String get usageBudgetClearAll => 'مسح ميزانيات الاستهلاك المحفوظة';

  @override
  String get usageBudgetClearDescription =>
      'هل تريد إزالة جميع ميزانيات الاستهلاك الحالية والسابقة لهذا الخادم المحفوظ؟ ستبقى حدود مزوّدي الخدمة.';

  @override
  String get usageBudgetClearTitle => 'مسح ميزانيات الاستهلاك؟';

  @override
  String get monitorScope =>
      'تشمل الأعداد آخر مشروع محدد لكل خادم، وليس جميع مشاريعه.';

  @override
  String get monitorDisclosure =>
      'المراقبة متوقفة حتى تفعّلها لخادم. تُجرى الفحوص مرة كل دقيقة تقريبًا ما دام التطبيق مفتوحًا. وتُجرى فحوص الخلفية بفاصل لا يقل عن خمس دقائق، وفقط حين يكون البقاء متصلًا في الخلفية مفعّلًا وخدمة Android تعمل. قد يوقف Android هذه الخدمة؛ لا يُضمن وقت تشغيل متبقٍّ.';

  @override
  String get monitorOptIn => 'مراقبة هذا الخادم';

  @override
  String get monitorOptInDetail =>
      'فحص الأذونات والأسئلة والنماذج المعلّقة في آخر مشروع محدد له.';

  @override
  String get monitorNotifications => 'الإشعار عند الحاجة إلى انتباه';

  @override
  String get monitorWifiDetail => 'يتوقف الفحص دون Wi-Fi.';

  @override
  String get monitorQuiet => 'ساعات الهدوء';

  @override
  String get monitorQuietStart => 'بداية ساعات الهدوء';

  @override
  String get monitorQuietEnd => 'نهاية ساعات الهدوء';

  @override
  String get monitorSaveFailed => 'تعذّر حفظ إعدادات المراقبة. أعد المحاولة.';

  @override
  String get monitorOpenFailed =>
      'تغيّر هذا الطلب أو مشروعه. حدّث صندوق الوارد وأعد المحاولة.';

  @override
  String get monitorSession => 'محادثة';

  @override
  String get monitorUnknown => 'غير معروف';

  @override
  String get monitorLastChecked => 'آخر تحقق';

  @override
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  ) {
    return '$profile · $kind\n$lastChecked: $time';
  }

  @override
  String get monitorCheckIn => 'الاطمئنان على العمل الطويل';

  @override
  String get monitorCheckInDetail => 'تذكير بعد المدة التي تحددها.';

  @override
  String get monitorCheckInDetailForeground =>
      'صف تذكير بعد المدة التي تحددها.';

  @override
  String get monitorCheckInAfter => 'التذكير بالمتابعة بعد';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes دقيقة',
      many: '$minutes دقيقة',
      few: '$minutes دقائق',
      two: 'دقيقتين',
      one: 'دقيقة واحدة',
      zero: '$minutes دقيقة',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => 'حان وقت المتابعة';

  @override
  String managedRecoveryAttempts(int attempts) {
    return 'المحاولات المستخدمة: $attempts من 3. يبقى الحد محفوظًا بعد إعادة تشغيل التطبيق.';
  }

  @override
  String get managedRecoveryExhausted =>
      'بُلغ حد الاستعادة. افحص الخادم وشغّله يدويًا قبل إعادة ضبط عدد المحاولات.';

  @override
  String get managedRecoveryBackground =>
      'تنتظر الاستعادة أثناء وجود التطبيق في الخلفية.';

  @override
  String get managedRecoveryChecking =>
      'جارٍ فحص عملية الاستعادة التي يديرها التطبيق…';

  @override
  String managedRecoveryNext(String time) {
    return 'لن تبدأ محاولة الاستعادة التالية قبل $time.';
  }

  @override
  String get managedRecoveryCheck => 'التحقق من حالة الاستعادة';

  @override
  String get managedRecoveryReset => 'إعادة ضبط عدد المحاولات';

  @override
  String get managedRecoverySaveFailed =>
      'تعذّر حفظ إعدادات الاستعادة. أعد المحاولة.';

  @override
  String get managedRecoveryRevokeFailed =>
      'تعذّر حفظ الاستعادة أو إلغاؤها. احتفظ بهذا الخادم وأعد المحاولة قبل إزالته.';

  @override
  String get managedRecoverySettingsUnreadable =>
      'تعذّرت قراءة إعدادات الاستعادة. افحص الخادم قبل تفعيل الاستعادة.';

  @override
  String get managedRecoveryEnableFailed =>
      'تعذّر تفعيل الاستعادة. شغّل الخادم الذي يديره التطبيق، ثم أعد المحاولة.';

  @override
  String get managedRecoveryOwnershipChanged =>
      'تغيّرت العملية التي يديرها التطبيق. افحص الخادم قبل تفعيل الاستعادة مجددًا.';

  @override
  String get managedRecoveryUncertain =>
      'توقفت الاستعادة مؤقتًا لأن Termux لم يؤكد النتيجة. اضغط «تحديث» للمتابعة.';

  @override
  String get managedRecoveryRetryDisable => 'إعادة محاولة تعطيل الاستعادة';

  @override
  String get pluginMappingUnavailable =>
      'لم تعد هذه الإضافة أو هذا الأمر متاحًا هنا. حدّث وراجع روابطك.';

  @override
  String get mobileTasksUnfinished => 'إظهار غير المكتملة فقط';

  @override
  String get mobileTasksNoUnfinished => 'لا مهام غير مكتملة في هذه القائمة.';

  @override
  String get mobileTaskPending => 'معلّقة';

  @override
  String get mobileTaskInProgress => 'قيد التنفيذ';

  @override
  String get mobileTaskCompleted => 'مكتملة';

  @override
  String get mobileTaskCancelled => 'ملغاة';

  @override
  String mobileTasksProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get mobileTasksCopyAll => 'نسخ جميع المهام';

  @override
  String get mobileTaskPriorityHigh => 'أولوية عالية';

  @override
  String get mobileTaskPriorityMedium => 'أولوية متوسطة';

  @override
  String get mobileTaskPriorityLow => 'أولوية منخفضة';

  @override
  String get usageWidgetTitle => 'أداة الاستخدام';

  @override
  String get usageWidgetIntro =>
      'تعرض ما تبقّى في خططك على أداة الاستخدام في الشاشة الرئيسية. تتحدّث أثناء عمل التطبيق.';

  @override
  String get usageWidgetPhoneTitle => 'هذا الهاتف';

  @override
  String get usageWidgetSearchAliases =>
      'أداة الاستخدام الشاشة الرئيسية claude codex خطة حد المتبقي paseo';

  @override
  String get quotaMonitorSourceChanged =>
      'تغيّر الحساب على هذا الخادم، لذا توقفت الفحوص. افتح الاستخدام المتبقي على ذلك الخادم واقرأه مجددًا.';

  @override
  String get webSearchDisclosure =>
      'يرسل البحث استعلامك إلى مزوّد البحث المحدد على هذا الخادم. راجع النتائج قبل إضافتها إلى مسودتك القابلة للتعديل. لا يُرسل شيء إلى النموذج هنا.';

  @override
  String get webSearchManual => 'أو الصق مصدرًا';

  @override
  String get webSearchUnavailable =>
      'البحث على الويب غير متاح. اضبط مزوّد بحث على هذا الخادم، ثم حدّث مزوّدي الخدمة. لا يزال بإمكانك لصق مصدر أدناه.';

  @override
  String get webSearchAuthentication =>
      'لم يسمح الخادم بالبحث على الويب. تحقّق من بيانات اعتماد هذا الخادم.';

  @override
  String get webSearchInvalidResponse =>
      'لم تطابق استجابة البحث هذا الخادم أو التنسيق المدعوم. حدّث مزوّدي الخدمة أو الصق مصدرًا.';

  @override
  String get webSearchFailed =>
      'تعذّر إكمال البحث على الويب. أعد المحاولة أو الصق مصدرًا.';

  @override
  String get webSearchRefresh => 'تحديث مزوّدي الخدمة';

  @override
  String get webSearchProvider => 'مزوّد البحث';

  @override
  String get webSearchQuery => 'استعلام البحث';

  @override
  String get webSearchSubmit => 'بحث';

  @override
  String get webSearchEmpty => 'لا نتائج صالحة لهذا الاستعلام.';

  @override
  String get webSearchOmitted =>
      'أُخفيت بعض النتائج لأن روابطها أو مقتطفاتها تجاوزت حدود المراجعة.';

  @override
  String get queueStorageUnreadable =>
      'تعذّرت قراءة الطلبات المحفوظة في قائمة الانتظار. لا يمكن إضافة طلبات جديدة إلى القائمة حتى تُمسح بيانات الجهاز هذه.';

  @override
  String get queueStorageDiscardUnreadable =>
      'يحذف هذا نهائيًا الطلبات غير المقروءة في قائمة الانتظار ومرفقاتها من هذا الجهاز. محتواها وعددها غير معروفين. لا يتأثر أي شيء على الخادم.';

  @override
  String get filesViewerScopeChanged =>
      'تغيّر الخادم. أغلق هذا الملف وافتحه مجددًا.';

  @override
  String get queueStorageCountUnknown =>
      'تعذّرت قراءة بيانات قائمة الانتظار المحفوظة. عدد الطلبات في القائمة غير معروف.';

  @override
  String get codexConnectionVerified =>
      'تم التحقق من الاتصال. احفظ واتصل للمتابعة.';

  @override
  String get codexApprovalRecoveryNotice =>
      'بعد إعادة الاتصال، راجع أي موافقات معلّقة على الكمبيوتر.';

  @override
  String connectionPasswordUnreadable(String server) {
    return 'تعذّرت قراءة كلمة المرور المحفوظة لـ $server';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return 'تعذّرت قراءة الرمز المحفوظ لـ $server';
  }

  @override
  String get connectionEnterPassword => 'إدخال كلمة المرور';

  @override
  String get connectionEnterToken => 'إدخال الرمز';

  @override
  String get connectionPasswordUnreadableDetails =>
      'تعذّر على مساحة التخزين الآمنة في هذا الهاتف فتح كلمة المرور المحفوظة لهذا الخادم. قد يحدث ذلك بعد استعادة الهاتف من نسخة احتياطية أو تغيير قفل شاشته. لم تتغيّر كلمة المرور نفسها: أدخلها مجددًا للاتصال.';

  @override
  String get connectionTokenUnreadableDetails =>
      'تعذّر على مساحة التخزين الآمنة في هذا الهاتف فتح الرمز المحفوظ لهذا الخادم. قد يحدث ذلك بعد استعادة الهاتف من نسخة احتياطية أو تغيير قفل شاشته. لم يتغيّر الرمز نفسه: أدخله مجددًا للاتصال.';

  @override
  String get updateConnectionToken => 'تحديث الرمز';

  @override
  String get codexTextOnlyPrompt =>
      'يدعم هذا الخادم النص فقط. أزل المرفقات قبل الإرسال.';

  @override
  String get codexOfflineDraftSaved =>
      'أعد الاتصال قبل الإرسال. مسودتك محفوظة على هذا الجهاز.';

  @override
  String get codexReconnectBeforeSending => 'أعد الاتصال قبل الإرسال.';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 أو wss://paseo.example';

  @override
  String get paseoPasswordLabel => 'كلمة مرور الخدمة (اختيارية)';

  @override
  String get paseoPasswordHelp =>
      'عيّنها بالأمر \"paseo daemon set-password\". تُحفظ في التخزين الآمن لهذا الجهاز.';

  @override
  String get paseoSetupNotice =>
      'شغّل \"paseo start --no-relay\" على الحاسوب الذي ثُبّت عليه Claude Code أو Pi. لا يستخدم هذا التطبيق مرحّل Paseo أبدًا: اتصل من هذا الجهاز أو عبر شبكتك الخاصة.';

  @override
  String get connectionDisplayName => 'الاسم المعروض (اختياري)';

  @override
  String get connectionDisplayNameHint => 'يُستخدم اسم مضيف الخادم افتراضيًا';

  @override
  String get connectionServerAddress => 'عنوان الخادم';

  @override
  String get codexAddressHint => 'wss://codex.example أو ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => 'مجلد المشروع على الخادم';

  @override
  String get codexTokenReentry => 'أعد إدخال رمز الاتصال';

  @override
  String get codexTokenLabel => 'رمز الاتصال';

  @override
  String get codexTokenStorageHelp =>
      'يُحفظ بأمان على هذا الجهاز، ويُرسل إلى خادم Codex هذا فقط.';

  @override
  String get projectConfiguredFolder => 'المجلد المضبوط';

  @override
  String get termuxGuideTitle => 'ربط Termux مرة واحدة';

  @override
  String get termuxGuideOpenFailed =>
      'تم نسخ الأمر، لكن تعذّر فتح Termux. افتحه بنفسك أو حاول «نسخ وفتح Termux» مجددًا.';

  @override
  String get termuxGuideCopyOpenFailed => 'تعذّر نسخ الأمر أو فتح Termux.';

  @override
  String get termuxPermissionDenied =>
      'رفض Android إذن أوامر Termux. اسمح به في إعدادات تطبيق OpenCode.';

  @override
  String get launchShortcutWaiting =>
      'جارٍ الاتصال بالخادم المحفوظ. تُفتح المحادثة الجديدة عندما يصبح جاهزًا.';

  @override
  String get launchShortcutNoServer => 'اختر خادمًا، ثم ابدأ محادثة جديدة.';

  @override
  String get launchShortcutReentry =>
      'أدخل بيانات اعتماد الخادم المحفوظ، ثم ابدأ محادثة جديدة.';

  @override
  String get launchShortcutConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. اختر خادمًا أو أصلح اتصاله، ثم ابدأ محادثة جديدة.';

  @override
  String get launchUiPinnedUntitled => 'محادثة بلا عنوان';

  @override
  String get launchUiSessionWaiting =>
      'جارٍ الاتصال بالخادم المحفوظ. ستُفتح المحادثة عندما يصبح جاهزًا.';

  @override
  String get launchUiSessionNoServer =>
      'اختر خادمًا، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionReentry =>
      'أدخل بيانات اعتماد الخادم المحفوظ، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. اختر خادمًا أو أصلحه، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionOtherServer =>
      'هذا الاختصار يخص خادمًا آخر. اتصل بذلك الخادم، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiActivityNoServer =>
      'اختر خادمًا لترى ما يحتاج إلى انتباهك.';

  @override
  String get launchUiUsageNoServer => 'احفظ خادمًا لترى ما تبقّى في خططك.';

  @override
  String get queuedResendTitle => 'إرسال هذه المسودة مجددًا؟';

  @override
  String get queuedResendMessage =>
      'ربما وصلت إلى OpenCode بالفعل. قد يكررها الإرسال مجددًا.';

  @override
  String get queuedResendConfirm => 'إرسال مجددًا';

  @override
  String get queuedKeepForReview => 'الاحتفاظ للمراجعة';

  @override
  String get queuedDiscardUnconfirmedMessage =>
      'لم يُؤكَّد إرسالها السابق؛ ربما توجد في المحادثة بالفعل.';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      many: '$count مسودة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      few: '$count مسودات بإرسال غير مؤكد تحتاج إلى مراجعة.',
      two: 'مسودتان بإرسال غير مؤكد تحتاجان إلى مراجعة.',
      one: 'مسودة واحدة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      zero: 'لا مسودات بإرسال غير مؤكد تحتاج إلى مراجعة.',
    );
    return '$_temp0';
  }

  @override
  String get isolatedTaskTitle => 'البدء في نسخة منفصلة';

  @override
  String get isolatedTaskIntro =>
      'يعمل على فرع خاص به، فلا يتعارض مع محادثاتك الأخرى.';

  @override
  String get isolatedTaskNameLabel => 'اسم النسخة (اختياري)';

  @override
  String get isolatedTaskNameHelper => 'اتركه فارغًا ليُختار اسم لك.';

  @override
  String get isolatedTaskStart => 'بدء المحادثة';

  @override
  String get isolatedTaskCreating => 'جارٍ إنشاء النسخة…';

  @override
  String get isolatedTaskCreatingHint =>
      'إذا توقفت عن الانتظار، فقد تُنشأ النسخة رغم ذلك. ستجدها ضمن المشروع › نسخ العمل.';

  @override
  String isolatedTaskPreparing(String name) {
    return 'جارٍ إعداد $name…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name جاهزة. جارٍ فتح المحادثة…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name جاهزة، لكن المحادثة لم تُفتح.';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return 'أُنشئت $name، لكن لم تصل نتيجة إعدادها بعد.';
  }

  @override
  String get isolatedTaskUnconfirmedHint =>
      'قد يكون الإعداد لا يزال جاريًا. تابع الانتظار أو ابدأ فيها الآن.';

  @override
  String isolatedTaskFailed(String name) {
    return 'فشل الإعداد في $name';
  }

  @override
  String get isolatedTaskCreateFailed => 'تعذّر إنشاء النسخة';

  @override
  String get isolatedTaskCancelled => 'توقف الانتظار.';

  @override
  String isolatedTaskOpening(String name) {
    return 'جارٍ فتح المحادثة في $name…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return 'المحادثة في $name جاهزة.';
  }

  @override
  String get isolatedTaskStopWaiting => 'إيقاف الانتظار';

  @override
  String get isolatedTaskKeepWaiting => 'متابعة الانتظار';

  @override
  String get isolatedTaskRetryOpen => 'إعادة المحاولة';

  @override
  String get isolatedTaskClose => 'إغلاق';

  @override
  String get returnBriefContinue => 'متابعة';

  @override
  String get capsuleError => 'خطأ';

  @override
  String get capsuleRemove => 'إزالة';

  @override
  String get markdownWrapCode => 'التفاف الأسطر';

  @override
  String get markdownScrollCode => 'تمرير الأسطر';

  @override
  String get markdownReaderTitle => 'قارئ الشيفرة';

  @override
  String get tailscaleTitle => 'الاتصال عبر Tailscale';

  @override
  String get tailscaleIntro =>
      'اتصل بـ OpenCode على كمبيوتر آخر عبر شبكة Tailscale الخاصة بك. تتحكم في تسجيل الدخول والوصول عبر VPN من تطبيق Tailscale الرسمي.';

  @override
  String get tailscaleAppStep => '1. افتح شبكتك الخاصة';

  @override
  String get tailscaleChecking => 'جارٍ التحقق من تطبيق Tailscale…';

  @override
  String get tailscaleInstalled => 'Tailscale مثبّت. اتصال VPN غير مؤكد.';

  @override
  String get tailscaleMissing =>
      'Tailscale غير مثبّت. ثبّت التطبيق الرسمي، ثم عد وتحقّق مجددًا.';

  @override
  String get tailscaleUnknown =>
      'تعذّر فحص التطبيق. أعد المحاولة، أو افتح Tailscale من هاتفك.';

  @override
  String get tailscaleUnsupported =>
      'لا يستطيع هذا الجهاز فتح تطبيق Android. اضبط Tailscale على هذا الجهاز بنفسك، ثم راجع عنوان HTTPS أدناه.';

  @override
  String get tailscaleVpnHandoff =>
      'في Tailscale، سجّل الدخول إلى الشبكة التي تصل إلى خادمك، ووافق على طلب VPN من Android إن ظهر، ثم فعّل الاتصال. لا يستطيع OpenCode رؤية حالة VPN أو تغييرها.';

  @override
  String get tailscaleReturned =>
      'مرحبًا بعودتك. أُعيد التحقق من وجود التطبيق؛ استخدم «اختبار الاتصال» في الشاشة التالية لفحص خادمك.';

  @override
  String get tailscaleOpen => 'فتح Tailscale';

  @override
  String get tailscaleCheckAgain => 'فحص التطبيق مجددًا';

  @override
  String get tailscaleAddressStep => '2. راجع عنوان خادمك';

  @override
  String get tailscaleAddressLabel => 'عنوان الخادم الخاص';

  @override
  String get tailscaleAddressDetail =>
      'الصق عنوان HTTPS الكامل من Tailscale Serve، أو عنوان HTTP ومنفذ خادمك على Tailscale. يتطلب HTTP إذنك قبل الاتصال.';

  @override
  String get tailscaleAddressError =>
      'أدخل عنوان خادم HTTP أو HTTPS كاملًا مع المنفذ عند الحاجة، دون إضافة مسار بعده.';

  @override
  String get tailscaleReviewDetail =>
      'تابع فقط بعنوان تعرفه. تراجع الشاشة التالية بيانات اعتماد خادمك قبل أن تختبره أو تحفظه صراحةً. لا يستطيع التطبيق تأكيد خصوصية عنوان من اسمه وحده.';

  @override
  String get tailscaleContinue => 'المتابعة إلى المصادقة';

  @override
  String get tailscaleHelp => 'إعداد Tailscale واستعادة الاتصال';

  @override
  String get tailscaleServeHelp =>
      'على كمبيوتر الخادم، يستطيع Tailscale Serve توفير HTTPS خاص لمنفذ OpenCode محلي. استخدم Serve، وليس Funnel العام. تظل قواعد الوصول إلى شبكة tailnet سارية. ينشر تفعيل HTTPS اسمي الجهاز وشبكة tailnet في سجل شهادات عام، مع بقاء الوصول خاصًا. راجع الدليل الرسمي قبل تغيير خادمك.';

  @override
  String get tailscaleServeDocs => 'قراءة دليل Serve الرسمي';

  @override
  String get tailscaleAndroidDocs => 'قراءة دليل Android الرسمي';

  @override
  String get tailscaleRecovery =>
      'إذا تعذّر الوصول إلى الخادم، فتحقق من Tailscale على الجهازين والعنوان والمنفذ وقواعد الوصول للشبكة. لعنوان Serve، تحقق أيضًا من Serve على الخادم. صحّح كلمة المرور إذا رُفض تسجيل الدخول، ثم أعد اختبار الاتصال.';

  @override
  String get tailscaleEditorDetail =>
      'تُدار شبكة اتصالك في Tailscale. يفحص «اختبار الاتصال» خادم OpenCode هذا، لا شبكة VPN. أدخل هنا اسم مستخدم الخادم وكلمة مروره، لا بيانات دخول Tailscale. تحافظ مساعدة الإعداد على هذه الحقول.';

  @override
  String get a2aDraftSaveError =>
      'تعذّر حفظ تعديلات المسودة. أبقِ هذه الشاشة مفتوحة وأعد المحاولة قبل المغادرة.';

  @override
  String get a2aRetryDraftSave => 'إعادة محاولة حفظ المسودة';

  @override
  String get a2aSavingDraft => 'جارٍ حفظ تعديلات المسودة…';

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · مهام نصية';

  @override
  String get a2aTitle => 'الوكلاء الخارجيون';

  @override
  String get a2aAdd => 'إضافة وكيل';

  @override
  String get a2aDeleteLocal => 'إزالة من هذا الهاتف';

  @override
  String get a2aAddress => 'عنوان الوكيل';

  @override
  String get a2aUnsupported =>
      'غير متاح: لا تعلن هذه البطاقة عن المجموعة المدعومة من A2A 1.0 وJSON-RPC والنص والمصادقة على الأصل نفسه، أو تتطلب امتدادًا غير مدعوم. لا يمكن إرسال أي مهمة.';

  @override
  String get a2aBearer => 'بيانات اعتماد bearer للوكيل';

  @override
  String get a2aSkills => 'المهارات المعلنة';

  @override
  String get a2aTaskPrompt => 'نص المهمة';

  @override
  String get a2aDeliveryUnconfirmed => 'التسليم غير مؤكد';

  @override
  String get a2aDraft => 'لم تُرسل';

  @override
  String get a2aCancelDetail =>
      'اطلب من هذا الوكيل إيقاف هذه المهمة. ربما اكتمل العمل بالفعل؛ ويقرر الوكيل ما إذا كان الإيقاف ممكنًا.';

  @override
  String get a2aYourReply => 'ردك';

  @override
  String get a2aAgentOutput => 'مخرجات الوكيل';

  @override
  String get a2aBlockedLink => 'رابط غير مدعوم';

  @override
  String get a2aReviewLink => 'مراجعة الرابط الخارجي';

  @override
  String get a2aOmittedContent =>
      'حُجب بعض المخرجات. يعرض هذا المشهد نصًا وروابط ضمن حدود محددة؛ ولا تُنزَّل المخرجات الثنائية أو المنظّمة ولا تُنفَّذ.';

  @override
  String get a2aSubmitted => 'تم تقديمها';

  @override
  String get a2aWorking => 'قيد التنفيذ';

  @override
  String get a2aInputRequired => 'يلزم ردك';

  @override
  String get a2aAuthRequired => 'الوكيل يتطلب المصادقة';

  @override
  String get a2aCompleted => 'مكتملة';

  @override
  String get a2aFailed => 'فشلت';

  @override
  String get a2aCanceled => 'ملغاة';

  @override
  String get a2aRejected => 'مرفوضة';

  @override
  String get a2aUnknown => 'حالة مهمة غير مدعومة';

  @override
  String get a2aAddressError =>
      'استخدم أصل HTTPS أو رابط بطاقة وكيل عامة دون بيانات اعتماد أو استعلام أو جزء ملحق. يُدعم HTTP على عنوان الاسترجاع المحلي لهذا الجهاز فقط.';

  @override
  String get a2aAuthenticationError =>
      'رفض الوكيل بيانات الاعتماد هذه أو تعذّر عليه استخدامها. ارجع إلى الوكيل لتحديثها، ثم أعد فتح المهمة المحفوظة.';

  @override
  String get a2aUnavailable =>
      'تعذّر الوصول إلى الوكيل أو رفض هذه العملية. حدّث مهمة معروفة للتحقق من حالتها.';

  @override
  String get a2aInvalidResponse =>
      'أعاد الوكيل استجابة غير مدعومة أو أكبر من الحد أو غير مطابقة. لم تُستبدل المهمة المحفوظة.';

  @override
  String get a2aUncertain =>
      'ربما استلم الوكيل هذه الرسالة. لن تُرسل مجددًا. إذا تأكد معرّف المهمة، فحدّث للتحقق من التقدم؛ وإلا فتحقّق مع الوكيل قبل بدء مهمة أخرى.';

  @override
  String get a2aStorageError =>
      'تعذّر حفظ البيانات المحلية أو إزالتها. تحقّق من مساحة الجهاز وأعد العملية المحلية. لا تُرسل رسالة دون حفظ علامة تسليم لها.';

  @override
  String get a2aScopeError =>
      'تغيّر هذا الوكيل أو بيانات اعتماده أو المهمة المحفوظة. أغلق هذا العرض وأعد فتح الوكيل للمتابعة.';

  @override
  String get a2aCancelUnconfirmed =>
      'الإلغاء غير مؤكد. لا يزال الوكيل يبلّغ عن مهمة نشطة؛ حدّث للتحقق مجددًا.';

  @override
  String get a2aAuthRequiredDetail =>
      'طلب هذا الوكيل مسار مصادقة إضافيًا لا يدعمه هذا العميل. لن يحدث تسجيل دخول أو استئناف للمهمة تلقائيًا.';

  @override
  String get a2aUnknownDetail =>
      'حالة هذه المهمة غير مدعومة. يمكنك تحديث السجل المحلي أو نسيانه؛ بينما يظل الإرسال والإلغاء غير متاحين.';

  @override
  String get fileTable => 'جدول';

  @override
  String get fileSource => 'المصدر';

  @override
  String fileLineOutsidePreview(int line) {
    return 'السطر $line خارج هذه المعاينة. احفظ الملف الأصلي لقراءة ذلك الموضع.';
  }

  @override
  String get fileTableMalformed =>
      'علامات الاقتباس في هذا الملف غير مكتملة أو غير متسقة. اقرأ مصدره بدلًا من ذلك.';

  @override
  String get fileTableTooLarge =>
      'تدعم معاينة الجدول ملفات حتى 256 KB. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileTableTooWide =>
      'يحتوي هذا الملف على أكثر من 32 عمودًا. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileTableFieldTooLong =>
      'تتجاوز إحدى الخلايا 4,096 حرفًا. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileImage => 'صورة';

  @override
  String get fileSvgUnsupported =>
      'لا يمكن عرض SVG هذا كصورة محلية ثابتة. اقرأ مصدره أو احفظ الملف الأصلي. لا تُدعم الموارد الخارجية أو الحركة أو ميزات SVG المعقدة.';

  @override
  String get filePdfEncrypted =>
      'يتطلب PDF هذا كلمة مرور أو يستخدم حماية غير مدعومة. احفظ الملف الأصلي لفتحه في تطبيق PDF.';

  @override
  String get filePdfLimit =>
      'تدعم معاينة PDF ملفات حتى 10 MB وأول 200 صفحة. احفظ الملف الأصلي لقراءة المستند كاملًا.';

  @override
  String get filePdfUnavailable =>
      'عرض PDF متاح على Android 10 أو أحدث. لا يزال بإمكانك حفظ الملف الأصلي.';

  @override
  String get filePdfCancelled =>
      'أُلغي تحميل PDF. أعد المحاولة عندما تكون جاهزًا.';

  @override
  String get filePdfFailed =>
      'تعذّر عرض صفحة PDF هذه. أعد المحاولة أو احفظ الملف الأصلي.';

  @override
  String get agentAccountTitle => 'حساب Codex';

  @override
  String get agentAccountScopeLost =>
      'تغيّر هذا الخادم. ارجع إلى «الخوادم» وافتح الحساب للخادم المتصل.';

  @override
  String get agentAccountRefresh => 'تحديث الحساب';

  @override
  String get agentAccountLoading => 'جارٍ فحص حساب المضيف';

  @override
  String get agentAccountUnavailable => 'لوحة الحساب غير متاحة';

  @override
  String get agentAccountReadFailed => 'تعذّرت قراءة الحساب';

  @override
  String get agentAccountDisconnected => 'انقطع الاتصال';

  @override
  String get agentAccountConnected => 'تم تسجيل الدخول على المضيف';

  @override
  String get agentAccountSignedOut => 'جاهز لتسجيل الدخول';

  @override
  String get agentAccountInProgress => 'جارٍ تسجيل الدخول';

  @override
  String get agentAccountNeedsAttention => 'تسجيل الدخول يحتاج إلى انتباه';

  @override
  String get agentAccountNoAuth => 'المضيف لا يتطلب تسجيل دخول';

  @override
  String get agentAccountApiKey => 'مفتاح API';

  @override
  String get agentAccountHostAuth => 'مصادقة المضيف';

  @override
  String get agentAccountHostNote =>
      'تحتفظ بيئة Codex الرسمية ببيانات اعتماد مزوّد الخدمة. تنطبق تغييرات الحساب على هذا المضيف، بما في ذلك الخوادم المحفوظة الأخرى المتصلة به.';

  @override
  String get agentAccountUnsupportedDetail =>
      'تم التحقق من هذه اللوحة مع Codex 0.153.4. قد لا تدعم بيئة التشغيل المتصلة طرق الحساب هذه.';

  @override
  String get agentAccountReconnectDetail =>
      'مُسحت بيانات الحساب ورمز تسجيل الدخول. أعد الاتصال للتحديث. لن يُعاد بدء تسجيل الدخول تلقائيًا.';

  @override
  String get agentAccountSignIn => 'تسجيل الدخول باستخدام ChatGPT';

  @override
  String get agentAccountSignInNote =>
      'ابدأ تسجيل دخول رسميًا برمز جهاز على هذا المضيف. أكمله في متصفحك؛ ولا يتلقى التطبيق رموز مزوّد الخدمة مطلقًا.';

  @override
  String get agentAccountLimits => 'حدود معدل الطلبات';

  @override
  String get agentAccountLimitsUnavailable =>
      'حدود معدل الطلبات غير متاحة لهذا الحساب أو المضيف.';

  @override
  String get agentAccountUsage => 'استخدام الرموز';

  @override
  String get agentAccountUsageUnavailable =>
      'استخدام الرموز غير متاح لهذا الحساب أو المضيف.';

  @override
  String get agentAccountLifetimeTokens => 'إجمالي الرموز منذ البدء';

  @override
  String get agentAccountPeakTokens => 'أعلى استخدام يومي للرموز';

  @override
  String get agentAccountUsageNote =>
      'يبلّغ المضيف عن القيم. القيم المفقودة مجهولة، وليست صفرًا. لا تمثل أعداد الرموز فاتورة أو رصيد رسائل متبقيًا.';

  @override
  String agentAccountUpdated(String time) {
    return 'آخر تحقق $time';
  }

  @override
  String get agentAccountStarting => 'جارٍ طلب رمز تسجيل الدخول';

  @override
  String get agentAccountWaiting => 'أكمل تسجيل الدخول في متصفحك';

  @override
  String get agentAccountCancelling => 'جارٍ إلغاء تسجيل الدخول';

  @override
  String get agentAccountCancelled => 'أُلغي تسجيل الدخول';

  @override
  String get agentAccountLoginFailed =>
      'لم يكتمل تسجيل الدخول. تحقّق من المضيف وأعد المحاولة.';

  @override
  String get agentAccountLoginUncertain =>
      'تعذّر على المضيف تأكيد تسجيل الدخول أو الإلغاء. ربما لا يزال ينتظر. افحص بيئة التشغيل الرسمية على المضيف قبل البدء مجددًا.';

  @override
  String get agentAccountLoginCompleted =>
      'اكتمل تسجيل الدخول. جارٍ فحص الحساب.';

  @override
  String get agentAccountCodeHint =>
      'أدخل هذا الرمز الذي يُستخدم مرة واحدة في صفحة تسجيل الدخول الرسمية. احتفظ به سريًا.';

  @override
  String get agentAccountOpenSignIn => 'فتح تسجيل الدخول الرسمي';

  @override
  String get agentAccountCancel => 'إلغاء تسجيل الدخول';

  @override
  String get agentAccountAllowance => 'رصيد الاستخدام المبلّغ عنه';

  @override
  String agentAccountPercentUsed(int percent) {
    return 'تم استخدام $percent%';
  }

  @override
  String get agentAccountWindowUnknown => 'مدة الفترة غير متاحة';

  @override
  String agentAccountWindowMinutes(int minutes) {
    return 'مدة الفترة بالدقائق: $minutes';
  }

  @override
  String agentAccountWindowHours(int hours) {
    return 'مدة الفترة بالساعات: $hours';
  }

  @override
  String agentAccountWindowDays(int days) {
    return 'مدة الفترة بالأيام: $days';
  }

  @override
  String get agentAccountResetUnknown => 'موعد التجديد غير متاح';

  @override
  String get projectFolderCreate => 'إنشاء مجلد جديد';

  @override
  String get projectFolderOpen => 'فتح مجلد مشروع';

  @override
  String get projectFolderNameLabel => 'اسم المجلد';

  @override
  String get projectFolderNameHint => 'my-app';

  @override
  String get projectFolderCreateAction => 'إنشاء';

  @override
  String get projectFolderCancel => 'إلغاء';

  @override
  String get projectFolderOpenMessage =>
      'أدخل المسار الكامل لمجلد على الخادم. لا يمكن استخدام المجلد الرئيسي نفسه؛ اختر مشروعًا داخله.';

  @override
  String get projectFolderPathLabel => 'مسار المجلد';

  @override
  String projectFolderPathHint(String directory) {
    return '$directory/my-app';
  }

  @override
  String get projectFolderOpenAction => 'فتح';

  @override
  String projectFolderCreateSubtitle(String directory) {
    return 'في $directory على هذا الجهاز';
  }

  @override
  String get projectFolderOpenSubtitle => 'أدخل المسار الكامل لمجلد على الخادم';

  @override
  String get globalSessionsTitle => 'جميع المحادثات';

  @override
  String get globalSessionsSearchLabel => 'البحث في عناوين المحادثات';

  @override
  String get globalSessionsArchivedShort => 'مؤرشفة';

  @override
  String get globalSessionsUnknownLocation => 'مشروع غير معروف';

  @override
  String get globalSessionsEmptyTitle => 'لا محادثات بعد';

  @override
  String get globalSessionsEmptyMessage =>
      'ستظهر هنا المحادثات من كل مشاريع هذا الخادم.';

  @override
  String get globalSessionsNoMatchTitle => 'لا محادثات مطابقة';

  @override
  String get globalSessionsNoMatchMessage =>
      'جرّب البحث بعنوان أقصر أو تضمين المحادثات المؤرشفة.';

  @override
  String get globalSessionsRefresh => 'تحديث';

  @override
  String get globalSessionsLoadMoreFailed => 'تعذّر تحميل المزيد من المحادثات';

  @override
  String get globalSessionsOpen => 'فتح';

  @override
  String get globalSessionsContinueHere => 'المتابعة هنا';

  @override
  String get globalSessionsActions => 'إجراءات المحادثة';

  @override
  String get globalSessionsWorking => 'قيد التنفيذ';

  @override
  String get globalSessionsUntitled => 'محادثة بلا عنوان';

  @override
  String get workspaceNewSession => 'محادثة جديدة';

  @override
  String get workspaceDismissNotice => 'تجاهل';

  @override
  String get reviewCopyFile => 'نسخ الملف المحدّث';

  @override
  String get reviewCopyPatch => 'نسخ الرقعة';

  @override
  String get onboardingPrivateNetwork => 'الوصول إلى خادم عبر شبكتك الخاصة';

  @override
  String get onboardingSetupGuide => 'دليل الإعداد';

  @override
  String get onboardingSaveConnect => 'حفظ واتصال';

  @override
  String get onboardingSaveChanges => 'حفظ التغييرات';

  @override
  String get onboardingTermuxSetup => 'على هذا الهاتف';

  @override
  String get activitySavedServers => 'الخوادم المحفوظة';

  @override
  String get demoTaskTitle => 'جرّب تغييرًا بسيطًا';

  @override
  String get demoTaskInstruction =>
      'أرسل الطلب النموذجي أدناه، ثم راجع التعديل المقترح.';

  @override
  String get reviewTitle => 'مراجعة';

  @override
  String get modelChoiceReloadProviders => 'إعادة تحميل مزوّدي الخدمة';

  @override
  String get modelChoiceDone => 'تم';

  @override
  String get modelChoicePartialSaveError =>
      'حُفظ النموذج. لم يُؤكَّد اختيار الوكيل. أعد المحاولة.';

  @override
  String get modelChoiceModelSaveError =>
      'تعذّر تأكيد اختيار النموذج. تحقّق من اختيارك وأعد المحاولة.';

  @override
  String get workIdle => 'خامل';

  @override
  String get workStartedInBackground => 'بدأ في الخلفية';

  @override
  String get workBackgroundPending => 'جارٍ طلب العمل في الخلفية…';

  @override
  String get workBackgroundRequested =>
      'طُلب العمل في الخلفية. ستُحدّث الحالة عندما يبلّغ عنها الخادم.';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String get setupSwitchPending =>
      'لم يكتمل التحديث إلى OpenCode 2. محادثاتك المحفوظة باقية. حاول مجددًا.';

  @override
  String setupSwitchRetry(String runtime) {
    return 'إعادة محاولة $runtime';
  }

  @override
  String get setupSwitchFailed =>
      'تعذّر إكمال التحديث إلى OpenCode 2. محادثاتك المحفوظة باقية. حاول مجددًا.';

  @override
  String setupSwitchProfileName(String runtime) {
    return 'هذا الهاتف · $runtime';
  }

  @override
  String get setupSwitchMissingCredential =>
      'تعذّرت قراءة كلمة مرور الخادم المحفوظة. محادثاتك وملفاتك باقية. استعد الوصول إلى كلمة المرور المحفوظة ثم حاول مجددًا.';

  @override
  String setupSwitchProgressTitle(String runtime) {
    return 'جارٍ التبديل إلى $runtime';
  }

  @override
  String e7ConnectionFailure1(int attempts) {
    return 'عدد المحاولات: $attempts. لن تؤدي إعادة المحاولة إلى تشغيل خادم متوقف.';
  }

  @override
  String get e7ConnectionFailure2 => 'يلزم رمز اتصال';

  @override
  String get e7ConnectionFailure3 =>
      'يتطلب خادم Codex هذا رمز اتصال قبل أن يتمكن التطبيق من الاتصال به.';

  @override
  String get e7ConnectionFailure4 =>
      'افتح إعدادات الخادم وأدخل رمز اتصال Codex.';

  @override
  String get e7ConnectionFailure5 => 'رُفض رمز الاتصال';

  @override
  String get e7ConnectionFailure6 =>
      'استجاب خادم Codex، لكنه لم يقبل رمز الاتصال المحفوظ.';

  @override
  String get e7ConnectionFailure7 =>
      'افتح إعدادات الخادم وأدخل رمز اتصال Codex ساريًا.';

  @override
  String get e7ConnectionFailure8 => 'خدمة استقبال اتصالات Codex غير متاحة';

  @override
  String get e7ConnectionFailure9 => 'تعذّر الوصول إلى نقطة اتصال Codex';

  @override
  String get e7ConnectionFailure12 =>
      'شغّل خدمة استقبال اتصالات Codex على هذا الجهاز.';

  @override
  String get e7ConnectionFailure13 =>
      'إذا كان الاتصال عبر نفق، فأبقِ النفق قيد التشغيل وتحقّق من نقطة اتصاله المحلية.';

  @override
  String get e7ConnectionFailure14 =>
      'استخدم نقطة اتصال Codex عبر wss:// أو نفقًا آمنًا نشطًا.';

  @override
  String get e7ConnectionFailure15 =>
      'تأكّد من إمكانية الوصول إلى خدمة استقبال اتصالات Codex البعيدة من هذا الجهاز.';

  @override
  String get e7ConnectionFailure16 => 'رُفضت كلمة المرور';

  @override
  String get e7ConnectionFailure17 =>
      'استجاب الخادم، لكنه لم يقبل كلمة المرور المحفوظة. يحدث هذا عند إعادة تشغيل الخادم بكلمة مرور جديدة.';

  @override
  String get e7ConnectionFailure18 =>
      'شغّل opencode2 pair على الكمبيوتر والصق الرمز الجديد.';

  @override
  String get e7ConnectionFailure19 =>
      'إذا ضبطت OPENCODE_SERVER_PASSWORD يدويًا، فانسخه مجددًا.';

  @override
  String get e7ConnectionFailure20 => 'الشهادة غير موثوقة';

  @override
  String get e7ConnectionFailure21 =>
      'الخادم موجود، لكن هذا الجهاز لا يثق بشهادة HTTPS الخاصة به، لذلك رفض التطبيق إرسال كلمة المرور.';

  @override
  String get e7ConnectionFailure22 =>
      'استخدم شهادة من جهة موثوقة، أو عنوان Tailscale Serve.';

  @override
  String get e7ConnectionFailure23 =>
      'للشهادة الموقّعة ذاتيًا، عدّل هذا الخادم وتحقق من بصمتها قبل الوثوق بها.';

  @override
  String get e7ConnectionFailure24 => 'لا خدمة تستقبل الاتصالات على هذا الجهاز';

  @override
  String get e7ConnectionFailure26 =>
      'هل تشغّل OpenCode في Termux؟ افتح Termux وتأكّد من أن الخادم ما زال قيد التشغيل.';

  @override
  String get e7ConnectionFailure27 =>
      'هل تستخدم adb reverse أو إعادة توجيه SSH؟ تأكّد من أن النفق ما زال متصلًا، ثم أعد المحاولة.';

  @override
  String get e7ConnectionFailure28 =>
      'هل تريد الاتصال بكمبيوتر آخر؟ غيّر عنوان الخادم إلى عنوان HTTPS الخاص به أو أعد الاقتران.';

  @override
  String get e7ConnectionFailure29 => 'لم يستجب الخادم في الوقت المحدد';

  @override
  String get e7ConnectionFailure31 =>
      'هل أنت متصل بالشبكة نفسها أو بشبكة VPN نفسها (مثل Tailscale) التي يتصل بها الكمبيوتر؟';

  @override
  String e7ConnectionFailure32(int port) {
    return 'هل يحجب جدار حماية أو بوابة تسجيل دخول الشبكة المنفذ $port؟';
  }

  @override
  String get e7ConnectionFailure33 => 'تعذّر الوصول إلى الخادم';

  @override
  String get e7ConnectionFailure35 =>
      'هل ما زال opencode serve قيد التشغيل على الكمبيوتر؟';

  @override
  String get e7ConnectionFailure36 =>
      'هل أنت متصل بالشبكة نفسها أو بشبكة VPN نفسها التي يتصل بها الكمبيوتر؟';

  @override
  String get e7ConnectionFailure37 =>
      'هل تغيّر العنوان؟ أعد الاقتران للحصول على العنوان الجديد.';

  @override
  String get e7ConnectionFailure38 => 'استجاب الخادم بخطأ';

  @override
  String get e7ConnectionFailure39 =>
      'الخادم قيد التشغيل، لكنه أبلغ عن خلل في حالته. سيوضح سجله السبب.';

  @override
  String get e7ConnectionFailure40 => 'أعد تشغيل opencode serve وراقب مخرجاته.';

  @override
  String get e7ConnectionFailure41 =>
      'تأكّد من أن هذا التطبيق يدعم إصدار الخادم.';

  @override
  String get e7ConnectionFailure42 => 'تعذّر الاتصال';

  @override
  String get e7ConnectionFailure44 =>
      'هل خادم الوكيل يعمل، وهل هذا هو العنوان الصحيح؟';

  @override
  String get e7ConnectionFailure45 =>
      'هل opencode serve قيد التشغيل، وهل هذا هو العنوان الصحيح؟';

  @override
  String get e7PermissionAction1 => 'تشغيل أمر في الطرفية';

  @override
  String get e7PermissionAction2 => 'تعديل ملف';

  @override
  String get e7PermissionAction3 => 'قراءة ملف';

  @override
  String get e7PermissionAction4 => 'الوصول إلى مجلد خارجي';

  @override
  String get e7PermissionAction5 => 'المتابعة بعد إخفاقات متكررة';

  @override
  String get e7PermissionAction6 => 'يلزم إذن';

  @override
  String e7PermissionAction7(String permission) {
    return 'استخدام $permission';
  }

  @override
  String get e7GlossaryMcpExplanation =>
      'بروتوكول سياق النموذج (Model Context Protocol). خوادم إضافية صغيرة تمنح الوكيل أدوات أخرى، مثل متصفح أو قاعدة بيانات أو أداة تصميم. تربطها مرة واحدة ويمكن لكل محادثة استخدامها.';

  @override
  String get e7GlossaryWorktreeExplanation =>
      'نسخة عمل منفصلة من المستودع نفسه. استخدمها عندما تريد أن يجرّب الوكيل شيئًا على فرع خاص به دون المساس بالشيفرة التي تعمل عليها.';

  @override
  String get e7GlossaryGotIt => 'فهمت';

  @override
  String get e7BannerUpdatePassword => 'تحديث كلمة المرور';

  @override
  String get e7BannerLost => 'فُقد الاتصال';

  @override
  String get e7BannerDetails => 'التفاصيل';

  @override
  String e7BannerReconnectingServer(String server) {
    return 'جارٍ إعادة الاتصال بـ $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return 'جارٍ إعادة الاتصال بـ $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      'يبقى المحتوى المعروض متاحًا أثناء فحص OpenCode. تُستأنف التحديثات المباشرة تلقائيًا.';

  @override
  String get e7BannerStaleExplanation =>
      'قد يكون المحتوى المعروض قديمًا حتى يصبح OpenCode متاحًا مجددًا.';

  @override
  String e7BannerCheckingExplanationAgent(String agent) {
    return 'يبقى المحتوى المعروض متاحًا أثناء فحص $agent. تُستأنف التحديثات المباشرة تلقائيًا.';
  }

  @override
  String e7BannerStaleExplanationAgent(String agent) {
    return 'قد يكون المحتوى المعروض قديمًا حتى يصبح $agent متاحًا مجددًا.';
  }

  @override
  String get e7SharedThreeStepsToYourFirstSession =>
      'ثلاث خطوات لبدء أول محادثة';

  @override
  String get e7SharedOpenCodeRunsOnYourComputerThisApp =>
      'يعمل OpenCode على حاسوبك، وهذا التطبيق هو جهاز التحكم عن بُعد. الاقتران يربط بينهما بأمر واحد، دون كتابة عناوين أو كلمات مرور.';

  @override
  String get e7SharedOnYourComputerRunOneCommand =>
      'على حاسوبك، شغّل أمرًا واحدًا';

  @override
  String get e7SharedInATerminalOnTheComputerWhere =>
      'في طرفية على الحاسوب المثبَّت عليه OpenCode:';

  @override
  String get e7SharedItStartsTheServerAndPrintsA =>
      'يبدأ الخادم ويطبع رمز اقتران، بالإضافة إلى رمز QR يمكنك مسحه.';

  @override
  String get e7SharedScanTheQROrPasteTheCode =>
      'امسح رمز QR أو الصق الرمز في هذا التطبيق';

  @override
  String get e7SharedPasteTheCodeInThisApp => 'الصق الرمز في هذا التطبيق';

  @override
  String get e7SharedStartTalking => 'ابدأ الحديث';

  @override
  String get e7SharedPickAProjectAndSendYourFirst =>
      'اختر مشروعًا وأرسل رسالتك الأولى. يجري العمل على حاسوبك، ويعرضه هذا التطبيق ويتيح لك توجيهه.';

  @override
  String get e7SharedAdvanced => 'خيارات متقدمة';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServersTermuxInternals =>
      'HTTPS وأنفاق SSH والخوادم الأقدم وتفاصيل Termux';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServers =>
      'HTTPS وأنفاق SSH والخوادم الأقدم';

  @override
  String get e7SharedReachAServerOverHTTPSOrA =>
      'الوصول إلى خادم عبر HTTPS أو نفق';

  @override
  String get e7SharedPairingWorksWhenTheAddressTheServer =>
      'يعمل الاقتران عندما يستطيع هذا الجهاز الوصول إلى عنوان الخادم. يطلب عنوان HTTP البعيد إذنك قبل إرسال كلمة المرور. يُفضّل HTTPS أو اتصال Tailscale خاص.';

  @override
  String get e7SharedOlderServersWithoutPairing => 'الخوادم الأقدم بدون اقتران';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      'للاتصال محليًا، شغّل OpenCode 2 على هذا الكمبيوتر واستخدم كلمة المرور التي يعرضها:';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      'ثم أضف الخادم يدويًا باسم المستخدم opencode وكلمة المرور تلك.';

  @override
  String get e7SharedOnDeviceViaTermuxAutomated =>
      'على الجهاز عبر Termux (آلي)';

  @override
  String get e7SharedUseTheOnDeviceTermuxCardOn =>
      'استخدم بطاقة «على الجهاز (Termux)» في شاشة الخوادم. يثبّت التطبيق Termux، ويفتح الجسر، ويجهّز opencode، ويشغّل الخادم ويتصل، كل ذلك بإرشاد خطوة بخطوة.';

  @override
  String get e7SharedOnlyTwoTapsNeedYouPersonallyDownloading =>
      'خطوتان فقط تحتاجان إليك شخصيًا: تنزيل حزمة APK الخاصة بـ Termux، ولصق سطر فتح واحد داخل Termux مرة واحدة. كلاهما يفرضه نموذج أمان Android، لا هذا التطبيق.';

  @override
  String get e7SharedPreferManualInsideTermuxRun =>
      'تفضّل الطريقة اليدوية؟ داخل Termux شغّل:';

  @override
  String get e7SharedTheChrootSharesTheNetworkStackSo =>
      'يتشارك chroot مكدّس الشبكة، لذا يعمل http://127.0.0.1:4096 من هذا التطبيق. شغّل `termux-wake-lock` لإبقائه نشطًا.';

  @override
  String get e7SharedSecurityNotes => 'ملاحظات أمنية';

  @override
  String get e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding =>
      'اضبط OPENCODE_SERVER_PASSWORD دائمًا عند الربط خارج localhost.';

  @override
  String get e7SharedPasswordsAreStoredInTheAndroidKeystore =>
      'تُخزَّن كلمات المرور في Android Keystore على هذا الجهاز فقط.';

  @override
  String get e7SharedTheServerCanExecuteCommandsOnIts =>
      'يستطيع الخادم تنفيذ أوامر على مضيفه، فتعامل مع الوصول إليه كما تتعامل مع وصول SSH.';

  @override
  String get e7SharedOpenCodeIsReconnectingTryAgain =>
      'يعيد OpenCode الاتصال. حاول مرة أخرى.';

  @override
  String get e7SharedSessionContext => 'سياق المحادثة';

  @override
  String get e7SharedRefreshContext => 'تحديث السياق';

  @override
  String get e7SharedNoContextUsageYet => 'لا يوجد استخدام للسياق بعد';

  @override
  String get e7SharedSendAPromptAndWaitForAn =>
      'أرسل طلبًا وانتظر رد المساعد. سيبلّغ OpenCode بعدها عن استخدام الرموز في هذه المحادثة.';

  @override
  String get e7SharedEstimatedInputMakeup => 'التركيب التقديري للمدخلات';

  @override
  String get e7SharedSessionTotals => 'إجماليات المحادثة';

  @override
  String get e7SharedUsageComesFromTheLatestCompletedAssistant =>
      'يُؤخذ الاستخدام من آخر رسالة مكتملة للمساعد. التركيب تقدير مبني على نص الطلب والرد والأدوات الظاهر؛ ويشمل «أخرى» تعليمات النظام وتعريفات الأدوات وحمل المزوّد الإضافي.';

  @override
  String get e7SharedModelUnavailable => 'النموذج غير متاح';

  @override
  String get e7SharedContextLimitUnavailable => 'حد السياق غير متاح';

  @override
  String get e7SharedLatestAssistantRequestIncludingCacheActivity =>
      'آخر طلب للمساعد، بما في ذلك نشاط ذاكرة التخزين المؤقت';

  @override
  String get e7SharedContextLimit => 'حد السياق';

  @override
  String get e7SharedUnavailable => 'غير متاح';

  @override
  String get e7SharedMessages => 'الرسائل';

  @override
  String get e7SharedAccumulatedCostReportedByServer =>
      'التكلفة المتراكمة · حسب تقرير الخادم';

  @override
  String get e7SharedAccumulatedCost => 'التكلفة المتراكمة';

  @override
  String get e7SharedSessionTokensReportedByServer =>
      'رموز المحادثة · حسب تقرير الخادم';

  @override
  String get e7SharedUserPrompts => 'طلبات المستخدم';

  @override
  String get e7SharedAssistantText => 'نص المساعد';

  @override
  String get e7SharedToolCallsAndResults => 'استدعاءات الأدوات ونتائجها';

  @override
  String get e7SharedOtherContext => 'سياق آخر';

  @override
  String get e7SharedSessionLocationChangedCloseAndReopenThis =>
      'تغيّر مشروع المحادثة. أغلق هذه الورقة وأعد فتحها.';

  @override
  String get e7SharedOpenCodeIsReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get e7SharedTheSessionProjectIsNotAvailableOn =>
      'مشروع المحادثة غير متاح على هذا الخادم.';

  @override
  String get e7SharedLocalProject => 'مشروع محلي';

  @override
  String get e7SharedTheAppCouldNotInspectWorkingChanges =>
      'تعذّر على التطبيق فحص تغييرات العمل. للسلامة، ستتم المتابعة دون نقل التغييرات.';

  @override
  String get e7SharedMoveSession => 'نقل المحادثة';

  @override
  String get e7SharedChooseAnotherDirectoryInThisProject =>
      'اختر مجلدًا آخر في هذا المشروع.';

  @override
  String get e7SharedChooseAConnectedWorkspaceOrReturnTo =>
      'اختر بيئة سحابية متصلة، أو عُد إلى المشروع المحلي.';

  @override
  String get e7SharedFilterDestinations => 'تصفية الوجهات';

  @override
  String get e7SharedCurrent => 'الحالي';

  @override
  String get e7SharedSwitchOrganization => 'تبديل المؤسسة؟';

  @override
  String get e7SharedSwitchOrganization462 => 'تبديل المؤسسة';

  @override
  String get e7SharedNoSwitchableOpenCodeConsoleOrganizationsWereReturned =>
      'لم تُرجَع أي مؤسسات قابلة للتبديل في OpenCode Console.';

  @override
  String get e7SharedSessionLocationChangedReturnAndReopenRelated =>
      'تغيّر مشروع المحادثة. عُد وأعد فتح المحادثات المرتبطة.';

  @override
  String get e7SharedSessionIsNoLongerRelatedToThis =>
      'لم تعد تلك المحادثة مرتبطة بهذه المحادثة.';

  @override
  String get e7SharedSessionLocationChangedReturnAndTryAgain =>
      'تغيّر مشروع المحادثة. عُد وحاول مرة أخرى.';

  @override
  String get e7SharedSessionUnavailableOrLocationChangedReturnOr =>
      'المحادثة غير متاحة أو تغيّر مشروعها. عُد أو حدّث للمحاولة مرة أخرى.';

  @override
  String get e7SharedCouldNotUpdateThePinReturnAnd =>
      'تعذّر تحديث التثبيت. عُد وحاول مرة أخرى.';

  @override
  String get e7SharedRefreshSubagentSessions =>
      'تحديث محادثات الوكلاء الفرعيين';

  @override
  String get e7SharedParentSession => 'المحادثة الأصل';

  @override
  String get e7SharedNoSubagentSessionsYet =>
      'لا توجد محادثات وكلاء فرعيين بعد';

  @override
  String get e7SharedDelegatedWorkWillAppearHereWithoutMixing =>
      'سيظهر العمل المُفوَّض هنا دون خلط محادثات الوكلاء الفرعيين بقائمتك الرئيسية.';

  @override
  String get e7SharedOpenInsecureHTTPLink => 'فتح رابط HTTP غير آمن؟';

  @override
  String get e7SharedOpenExternalLink => 'فتح رابط خارجي؟';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP غير مشفّر. قد تتمكن أجهزة أخرى على الشبكة من قراءة ما ترسله وتستقبله أو تغييره.';

  @override
  String get e7SharedOpenHTTPLink => 'فتح رابط HTTP';

  @override
  String get e7SharedOpenLink => 'فتح الرابط';

  @override
  String get e7SharedNoAppCouldOpenThisLink =>
      'لم يتمكن أي تطبيق من فتح هذا الرابط.';

  @override
  String get e7SharedRequired => 'مطلوب';

  @override
  String get e7SharedDoesNotMatchTheExpectedFormat =>
      'لا يطابق التنسيق المتوقع';

  @override
  String get e7SharedEnterAWholeNumber => 'أدخل عددًا صحيحًا';

  @override
  String get e7SharedEnterANumber => 'أدخل رقمًا';

  @override
  String get e7SharedDismissThisRequest => 'تجاهل هذا الطلب؟';

  @override
  String get e7SharedTheAgentContinuesWithoutYourAnswers =>
      'سيواصل الوكيل دون إجاباتك.';

  @override
  String get e7SharedAskedByAnMCPServer => 'سؤال من خادم MCP';

  @override
  String get e7SharedAskedByTheAgentInThisSession =>
      'سؤال من الوكيل في هذه المحادثة';

  @override
  String get e7SharedInputRequested => 'مطلوب إدخال';

  @override
  String get e7SharedOther => 'أخرى…';

  @override
  String get e7SharedYourAnswer => 'إجابتك';

  @override
  String get e7SharedAddYourOwn => 'أضف إجابتك';

  @override
  String get e7SharedAddAnswer => 'إضافة إجابة';

  @override
  String get e7SharedThisServerSentALinkThisApp =>
      'أرسل هذا الخادم رابطًا لن يفتحه هذا التطبيق.';

  @override
  String get e7SharedSendAnswers => 'إرسال الإجابات';

  @override
  String e7SharedDetail307(int step) {
    return 'الخطوة $step من 3';
  }

  @override
  String e7SharedDetail385(String count, String limit) {
    return '$count من $limit رمزًا';
  }

  @override
  String e7SharedDetail386(String count) {
    return '$count رمزًا · الحد غير متاح';
  }

  @override
  String e7SharedDetail429(String destination) {
    return 'هل تريد نقل المحادثة إلى $destination؟';
  }

  @override
  String e7SharedDetail514(String error) {
    return 'فشل التحديث: $error';
  }

  @override
  String e7SharedDetail714(int count) {
    return 'يجب ألا يقل عن $count حرفًا';
  }

  @override
  String e7SharedDetail715(int count) {
    return 'يجب ألا يزيد عن $count حرفًا';
  }

  @override
  String e7SharedDetail721(String minimum, String maximum) {
    return 'يجب أن يكون بين $minimum و$maximum';
  }

  @override
  String e7SharedDetail722(String minimum) {
    return 'يجب ألا يقل عن $minimum';
  }

  @override
  String e7SharedDetail723(String maximum) {
    return 'يجب ألا يزيد عن $maximum';
  }

  @override
  String e7SharedDetail726(int count) {
    return 'اختر $count على الأكثر';
  }

  @override
  String e7SharedDetail753(int minimum, int maximum) {
    return 'اختر $minimum–$maximum';
  }

  @override
  String e7SharedDetail754(int count) {
    return 'اختر $count على الأقل';
  }

  @override
  String e7SharedDetail755(int count) {
    return 'اختر حتى $count';
  }

  @override
  String e7SharedDetail756(String range, int count) {
    return '$range · تم تحديد $count';
  }

  @override
  String e7SharedDetail764(String host) {
    return 'يفتح $host في متصفحك';
  }

  @override
  String e7SharedDetail765(String field) {
    return 'أرسل هذا الخادم نوع حقل لا يفهمه هذا التطبيق (\"$field\").';
  }

  @override
  String get e7LocaleUiLanguage => 'اللغة';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => 'استخدام لغة النظام';

  @override
  String get e7LocaleUiClose => 'إغلاق';

  @override
  String get e7LocaleUiDescription =>
      'اختر لغة التطبيق. تبقى رسائل الخادم والنصوص التي تكتبها بلغتها الأصلية.';

  @override
  String get e7LocaleUiSaving => 'جارٍ حفظ اللغة…';

  @override
  String get e7LocaleUiSaveFailed =>
      'تعذّر حفظ اللغة. لا يزال اختيارك السابق مفعّلًا. اختر لغة للمحاولة مجددًا.';

  @override
  String get e7LocaleUiNewSession => 'محادثة جديدة';

  @override
  String get e7LocaleUiNewSessionHint => 'ابدأ محادثة في المشروع الحالي';

  @override
  String get e7LocaleUiWorkspaceHint => 'كل المحادثات عبر المشاريع';

  @override
  String get e7LocaleUiFilesHint =>
      'الملفات والتغييرات والطرفية وأدوات المشروع الأخرى';

  @override
  String get e7LocaleUiMoreHint =>
      'النماذج ومزوّدو الخدمة والإشعارات والإعدادات';

  @override
  String get e7LocaleUiSettings => 'الإعدادات';

  @override
  String get e7LocaleUiKeyboardShortcuts => 'اختصارات لوحة المفاتيح';

  @override
  String get e7LocaleUiRefreshSessions => 'تحديث المحادثات';

  @override
  String get e7LocaleUiDiagnostics => 'التشخيص';

  @override
  String get e7LocaleUiDiagnosticsHint => 'الأخطاء الأخيرة وتفاصيل الاتصال';

  @override
  String get e7LocaleUiCommandLauncher => 'قائمة الأوامر';

  @override
  String get e7LocaleUiFindSurface => 'البحث في هذه الصفحة';

  @override
  String get e7LocaleUiDestinations => 'تبديل التبويب';

  @override
  String get e7LocaleUiTerminal => 'الطرفية';

  @override
  String get e7LocaleUiCloseScreen => 'إغلاق هذه الصفحة';

  @override
  String get e7LocaleUiSendPrompt => 'إرسال الطلب';

  @override
  String get e7LocaleUiCopyTranscript => 'نسخ النص المحدّد من المحادثة';

  @override
  String get e7LocaleUiRecentModel =>
      'النموذج الأخير التالي / السابق في هذه المحادثة';

  @override
  String get e7LocaleUiThisList => 'هذه القائمة';

  @override
  String get e7LocaleUiCloseOverlay => 'إغلاق لوحة أو مربع حوار أو قائمة';

  @override
  String get e7LocaleUiContextActions => 'إجراءات الرسائل والملفات والمحادثات';

  @override
  String get e7LocaleUiContextKeys => 'نقرة بالزر الأيمن / Shift + F10 / Menu';

  @override
  String get e7LocaleUiConnectionChanged => 'تغيّر الخادم.';

  @override
  String get e7AppearanceFollowAndroid => 'اتباع إعداد Android';

  @override
  String get e7AppearanceFollowSystem => 'اتباع إعداد النظام';

  @override
  String get e7AppearanceLight => 'فاتح';

  @override
  String get e7AppearanceDark => 'داكن';

  @override
  String get e7AppearanceFollowPhoneDescription =>
      'مطابقة إعداد المظهر الفاتح أو الداكن لهذا الهاتف';

  @override
  String get e7AppearanceFollowDeviceDescription =>
      'مطابقة إعداد المظهر الفاتح أو الداكن لهذا الجهاز';

  @override
  String get e7AppearanceLightDescription => 'استخدام المظهر الفاتح';

  @override
  String get e7AppearanceDarkDescription => 'استخدام المظهر الداكن';

  @override
  String get e7AppearanceTitle => 'المظهر';

  @override
  String get e7AppearancePreviewHint =>
      'عاين المظهر أولاً. لن يتغيّر إلا عند تطبيقه.';

  @override
  String get e7AppearanceDynamicUnavailable =>
      'ألوان Material You غير متاحة على هذا الجهاز.';

  @override
  String e7AppearanceUsesMode(String mode) {
    return 'يبقى إعداد المظهر الفاتح أو الداكن كما هو: $mode.';
  }

  @override
  String get e7AppearanceSaveFailed =>
      'تعذّر حفظ المظهر. لم يتغيّر إعدادك السابق. حاول مجددًا.';

  @override
  String get e7AppearanceSaving => 'جارٍ الحفظ…';

  @override
  String get e7AppearanceApply => 'تطبيق';

  @override
  String get e7AppearanceCurrent => 'المظهر الحالي';

  @override
  String get e7AppearanceClose => 'إغلاق';

  @override
  String get e7AppearancePreviewTitle => 'النص وعناصر التحكّم';

  @override
  String get e7AppearancePreviewBody =>
      'شاهد تناسق النص والشيفرة والإجراءات المحدّدة.';

  @override
  String get e7AppearanceSelection => 'خيار محدّد';

  @override
  String get e7AppearanceTryControl => 'جرّب التحكّم';

  @override
  String get e7AppearanceSampleHint =>
      'تغيّر عناصر التحكّم التجريبية هذه المعاينة فقط.';

  @override
  String get e7SettingsUi1 => 'الخادم';

  @override
  String get e7SettingsUi8 => 'قطع الاتصال';

  @override
  String get e7SettingsUi9 => 'خادم OpenCode';

  @override
  String get e7SettingsUi11 => 'جارٍ فحص حالة الخادم…';

  @override
  String get e7SettingsUi12 => 'أوقفه Android';

  @override
  String get e7SettingsUi14 => 'مفعّل · يعمل الآن';

  @override
  String get e7SettingsUi15 => 'مفعّل · جارٍ البدء';

  @override
  String get e7SettingsUi16 => 'هذا الخادم';

  @override
  String get e7SettingsUi17 => 'غير معروف';

  @override
  String get e7SettingsUi18 => 'جارٍ إعادة الاتصال بـ OpenCode.';

  @override
  String get e7SettingsUi19 => 'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا.';

  @override
  String get e7SettingsUi22 => 'لم يفعّل Android وضع الخلفية.';

  @override
  String get e7SettingsUi23 => 'أوقف Android الاتصال المباشر';

  @override
  String get e7SettingsUi24 =>
      'تم استنفاد الحد اليومي لمزامنة البيانات في الخلفية، فتوقف الوضع المباشر تلقائيًا. فعّله لإعادة الاتصال؛ يتجدد الحد خلال 24 ساعة.';

  @override
  String get e7SettingsUi25 => 'البقاء متصلاً في الخلفية';

  @override
  String get e7SettingsUi26 =>
      'يواصل تحديث المهام عند إغلاق التطبيق ويُشعرك عندما تتطلب إحداها تدخلك. يستهلك طاقة إضافية ويعرض إشعارًا دائمًا.';

  @override
  String get e7SettingsUi27 => 'استخدام البطارية دون قيود مسموح';

  @override
  String get e7SettingsUi28 => 'السماح باستخدام البطارية دون قيود';

  @override
  String get e7SettingsUi29 =>
      'قد يستمر Android في تطبيق الحد الزمني لخدمة المقدّمة.';

  @override
  String get e7SettingsUi30 =>
      'اختياري. يساعد على إبقاء الاتصال المباشر أثناء وضع السكون. يحدّ Android 15 والإصدارات الأحدث مزامنة البيانات في الخلفية بست ساعات لكل 24 ساعة.';

  @override
  String get e7SettingsUi31 => 'أوقفه Android — اضغط لإعادة التشغيل';

  @override
  String get e7SettingsUi32 => 'يعمل الآن';

  @override
  String get e7SettingsUi34 =>
      'يوقف Android هذا بعد 6 ساعات يوميًا. سيخبرك التطبيق عند حدوث ذلك.';

  @override
  String get e7SettingsUi35 => 'الصدفة الافتراضية';

  @override
  String get e7SettingsUi36 =>
      'تستخدمها الطرفيات الجديدة وأوامر الصدفة المتوافقة على خادم OpenCode هذا.';

  @override
  String get e7SettingsUi37 =>
      'للطرفية فقط؛ يستخدم OpenCode بديلاً متوافقًا لأدوات الصدفة.';

  @override
  String get e7SettingsUi39 => 'تلقائي (إعداد الخادم الافتراضي)';

  @override
  String get e7SettingsUi41 => 'جارٍ تحميل الصدف من OpenCode…';

  @override
  String get e7SettingsUi46 => 'أعد تشغيل OpenCode على الجهاز المضيف';

  @override
  String get e7SettingsUi48 => 'تحديث OpenCode البعيد؟';

  @override
  String get e7SettingsUi49 => 'تغيّر الخادم النشط قبل اكتمال التحديث';

  @override
  String get e7SettingsUi50 => 'تحديث OpenCode المُدار';

  @override
  String get e7SettingsUi51 =>
      'تثبيت أحدث إصدار مستقر للخادم وتحديث النماذج وإعادة التشغيل بأمان ثم إعادة الاتصال.';

  @override
  String get e7SettingsUi52 => 'الإصدار السابق';

  @override
  String get e7SettingsUi53 => 'إصدار غير معروف';

  @override
  String get e7SettingsUi56 => 'غير متصل';

  @override
  String get e7SettingsUi57 => 'فحص حالة الخادم';

  @override
  String get e7SettingsUi58 => 'جارٍ الاستعلام عن حالة الخادم';

  @override
  String get e7SettingsUi59 => 'الخادم يعمل جيدًا';

  @override
  String get e7SettingsUi60 => 'حالة الخادم غير متاحة';

  @override
  String get e7SettingsUi62 => 'لم تُحفظ كلمة مرور للخادم';

  @override
  String get e7SettingsUi65 => 'إعداد OpenCode 2';

  @override
  String get e7SettingsUi66 =>
      'أبقِ OpenCode قيد التشغيل على حاسوبك بعد إغلاق الطرفية؛ انسخ أوامر الإعداد والحالة وإعادة التشغيل والسجلات والتحديث';

  @override
  String get e7SettingsUi67 => 'تحديثات الخادم';

  @override
  String get e7SettingsUi68 => 'رقِّ من الجهاز الذي يشغّل الخادم';

  @override
  String get e7SettingsUi69 => 'فاتح أو داكن';

  @override
  String get e7SettingsUi70 => 'السمة';

  @override
  String get e7SettingsUi74 => 'الإجراءات المسموح بها دائمًا';

  @override
  String get e7SettingsUi76 => 'على هذا الجهاز';

  @override
  String get e7SettingsUi77 => 'مساحة التخزين المستخدمة';

  @override
  String get e7SettingsUi78 => 'حذف الطلبات في قائمة الانتظار';

  @override
  String get e7SettingsUi79 => 'لا يوجد شيء ينتظر الإرسال';

  @override
  String get e7SettingsUi80 => 'حذف الطلبات في قائمة الانتظار؟';

  @override
  String get e7SettingsUi81 => 'تم حذف الطلبات في قائمة الانتظار';

  @override
  String get e7SettingsUi82 =>
      'تعذّر حذف الطلبات في قائمة الانتظار. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get e7SettingsUi83 => 'حذف المسودات';

  @override
  String get e7SettingsUi84 => 'لا يوجد نص محفوظ في محرّر الرسائل';

  @override
  String get e7SettingsUi85 => 'حذف المسودات؟';

  @override
  String get e7SettingsUi86 => 'تم حذف المسودات';

  @override
  String get e7SettingsUi87 =>
      'تعذّر حذف المسودات. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get e7SettingsUi88 => 'تشخيص التطبيق';

  @override
  String get e7SettingsUi92 => 'الخصوصية واستخدام البيانات';

  @override
  String get e7SettingsUi93 =>
      'الخوادم والمزوّدون والصوت والملفات وTermux والتحديثات';

  @override
  String get e7SettingsUi94 => 'تراخيص الصوت ومصادره';

  @override
  String get e7SettingsUi96 => 'حول التطبيق وإشعارات المصادر المفتوحة';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return 'قطع الاتصال بـ $server؟';
  }

  @override
  String e7SettingsVersion(String version) {
    return 'الإصدار $version';
  }

  @override
  String get e7AppearancePackOpencode => 'أخضر الطرفية، السمة الافتراضية';

  @override
  String get e7AppearancePackCatppuccin =>
      'Mocha وLatte بدرجات البنفسجي الفاتح';

  @override
  String get e7AppearancePackGruvbox => 'مظهر كلاسيكي دافئ بدرجات البرتقالي';

  @override
  String get e7AppearancePackSolarized =>
      'لوحة الألوان الثنائية الكلاسيكية بدرجات الأزرق';

  @override
  String get e7AppearancePackDynamic => 'ألوان Material You لهذا الهاتف';

  @override
  String e7SettingsStorageSummary(
    String total,
    int queued,
    String queueBytes,
    int drafts,
    String draftBytes,
    int days,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued طلب في قائمة الانتظار',
      many: '$queued طلبًا في قائمة الانتظار',
      few: '$queued طلبات في قائمة الانتظار',
      two: 'طلبان في قائمة الانتظار',
      one: 'طلب واحد في قائمة الانتظار',
      zero: 'لا طلبات في قائمة الانتظار',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts مسودة',
      many: '$drafts مسودة',
      few: '$drafts مسودات',
      two: 'مسودتان',
      one: 'مسودة واحدة',
      zero: 'لا مسودات',
    );
    return '$total من العمل غير المرسل — $_temp0 ($queueBytes) و$_temp1 ($draftBytes). تُحذف الطلبات في قائمة الانتظار بعد $days يومًا.';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يحذف كل الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم',
      many: 'يحذف كل الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم',
      few: 'يحذف كل الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم',
      two: 'يحذف الطلبين غير المرسلين ومرفقاتهما، لكل الخوادم',
      one: 'يحذف الطلب غير المرسل ومرفقاته، لكل الخوادم',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'يحذف هذا الإجراء الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم. لن تُرسل أبدًا. لا يتأثر أي شيء على الخادم.',
      two:
          'يحذف هذا الإجراء الطلبين غير المرسلين ومرفقاتهما، لكل الخوادم. لن يُرسلا أبدًا. لا يتأثر أي شيء على الخادم.',
      one:
          'يحذف هذا الإجراء الطلب غير المرسل ومرفقاته، لكل الخوادم. لن يُرسل أبدًا. لا يتأثر أي شيء على الخادم.',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يحذف نص محرّر الرسائل المحفوظ في $count محادثة',
      few: 'يحذف نص محرّر الرسائل المحفوظ في $count محادثات',
      two: 'يحذف نص محرّر الرسائل المحفوظ في محادثتين',
      one: 'يحذف نص محرّر الرسائل المحفوظ في محادثة واحدة',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'يحذف هذا الإجراء نص محرّر الرسائل المحفوظ في $count محادثة. لا يتأثر أي شيء على الخادم.',
      few:
          'يحذف هذا الإجراء نص محرّر الرسائل المحفوظ في $count محادثات. لا يتأثر أي شيء على الخادم.',
      two:
          'يحذف هذا الإجراء نص محرّر الرسائل المحفوظ في محادثتين. لا يتأثر أي شيء على الخادم.',
      one:
          'يحذف هذا الإجراء نص محرّر الرسائل المحفوظ في محادثة واحدة. لا يتأثر أي شيء على الخادم.',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsRestartBody(String version, String current) {
    return 'تم تثبيت OpenCode $version، لكن عملية الخادم هذه ما زالت تشغّل $current. أعد تشغيل العملية على الجهاز المضيف؛ سيعيد التطبيق الاتصال ويتحقق من الإصدار الجاري تشغيله.';
  }

  @override
  String e7SettingsInstallVersion(String version) {
    return 'تثبيت $version';
  }

  @override
  String e7SettingsInstalledVersion(String version) {
    return 'تم تثبيت OpenCode $version. أعد تشغيل عملية الخادم لاستخدامه.';
  }

  @override
  String e7SettingsRestartVersion(String version) {
    return 'أعد تشغيل OpenCode لاستخدام $version';
  }

  @override
  String e7SettingsRetryError(String error) {
    return '$error اضغط للمحاولة مجددًا.';
  }

  @override
  String e7SettingsUpdateVersion(String version) {
    return 'تحديث OpenCode إلى $version';
  }

  @override
  String get e7SettingsDetailUi17 => 'الخصوصية';

  @override
  String get e7SettingsDetailUi18 => 'المصادر المفتوحة';

  @override
  String get e7SettingsDetailUi19 => 'حول هذا الإصدار';

  @override
  String get e7SettingsDetailUi22 =>
      'تطبيق محمول للاتصال بخادم OpenCode. يعمل التعرّف على الصوت محليًا بعد تنزيل النماذج الاختيارية.';

  @override
  String get e7SettingsDetailUi23 => 'تطبيق حاسوب للاتصال بخادم OpenCode.';

  @override
  String get e7SettingsDetailUi25 => 'المدمجة';

  @override
  String get e7SettingsDetailUi26 => 'المهارات';

  @override
  String get e7SettingsDetailUi27 => 'المراجع';

  @override
  String get e7SettingsAlphaBody =>
      'بُني هذا التطبيق المستقل بمساعدة كبيرة من الذكاء الاصطناعي. أندرويد هو المنصة الأساسية المدعومة. إصدارات الحاسوب تجريبية ولم تُختبر على أجهزة فعلية. أبلغ عن الأعطال للمساعدة في تحسين التطبيق.';

  @override
  String get e7SettingsNonAffiliation =>
      'OpenCode Mobile مشروع مجتمعي مستقل. لم يُنشئه فريق OpenCode الرسمي ولا يتولّى صيانته أو يؤيّده، وليس مرتبطًا به.';

  @override
  String e7SettingsDiagnosticOccurrences(int count) {
    return 'عدد مرات الحدوث: $count';
  }

  @override
  String get e7ProjectProjectsReconnect =>
      'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا بعد قليل.';

  @override
  String e7ProjectProjectRenameFailed(String error) {
    return 'تعذّر تغيير اسم المشروع: $error';
  }

  @override
  String get e7ProjectProjectDefaultDirectory => 'المجلد الافتراضي للخادم';

  @override
  String get e7ProjectProjectSwitchUnavailableDetail =>
      'يستخدم هذا الخادم المجلد المحدد للمحادثات. ابدأ محادثة جديدة من «العمل» للمتابعة.';

  @override
  String get e7ProjectProjectsTitle => 'المشاريع';

  @override
  String get e7ProjectProjectsRefresh => 'تحديث المشاريع';

  @override
  String get e7ProjectProjectsSearch => 'البحث عن مشروع أو مسار';

  @override
  String get e7ProjectProjectsOpened => 'المشاريع المفتوحة';

  @override
  String get e7ProjectProjectsEmpty => 'لا توجد مشاريع مفتوحة';

  @override
  String get e7ProjectProjectsEmptyDetail =>
      'تظهر هنا المشاريع التي تفتحها أو تنشئها.';

  @override
  String get e7ProjectProjectsRefreshFailed => 'تعذّر تحديث المشاريع';

  @override
  String e7ProjectProjectWorktrees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count شجرة عمل',
      many: '$count شجرة عمل',
      few: '$count أشجار عمل',
      two: 'شجرتا عمل',
      one: 'شجرة عمل واحدة',
      zero: 'لا توجد أشجار عمل',
    );
    return '$_temp0';
  }

  @override
  String e7ProjectProjectRenameAction(String name) {
    return 'تغيير اسم $name';
  }

  @override
  String get e7ProjectProjectRenameTitle => 'تغيير اسم المشروع';

  @override
  String get e7ProjectProjectNameLabel => 'اسم المشروع';

  @override
  String get e7ProjectProjectNameHint =>
      'امسح الاسم لاستخدام اسم مجلد المشروع.';

  @override
  String get e7ProjectProjectSave => 'حفظ';

  @override
  String get e7ProjectMonitorUnsupported =>
      'متابعة الطلبات في الخلفية غير متاحة لهذا الخادم. افتح المحادثة لمراجعة الطلبات الحالية.';

  @override
  String get readerUiDisconnected => 'الخادم غير متصل.';

  @override
  String get readerUiIndicatorsUnavailable =>
      'مؤشرات تغييرات الملفات غير متاحة على هذا الخادم.';

  @override
  String get readerUiIndicatorsFailed => 'تعذّر تحديث مؤشرات تغييرات الملفات.';

  @override
  String get readerUiReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get readerUiCommentAdded =>
      'أُضيف تعليق المراجعة. عُد إلى المحادثة للمتابعة.';

  @override
  String get readerUiCommentCopied => 'نُسخ تعليق المراجعة. ألصقه في محادثة.';

  @override
  String get readerUiSearchSymbols => 'البحث عن الرموز';

  @override
  String get readerUiSearchFiles => 'البحث عن الملفات';

  @override
  String get readerUiSelectFile => 'اختر ملفًا لمعاينته';

  @override
  String get readerUiEmptyFolder => 'المجلد فارغ';

  @override
  String get readerUiNoFiles => 'لم يتم العثور على ملفات';

  @override
  String get readerUiPullRefresh => 'اسحب للأسفل لتحديث هذا المجلد.';

  @override
  String get readerUiTryFileName => 'جرّب اسم ملف آخر.';

  @override
  String get readerUiAttachPrompt => 'إرفاق بالطلب';

  @override
  String get readerUiOpenReview => 'فتح في المراجعة';

  @override
  String get readerUiCopyPath => 'نسخ المسار';

  @override
  String get readerUiWorkspaceSymbols => 'البحث عن رموز المشروع';

  @override
  String get readerUiSymbolsHint =>
      'ابحث عن الأصناف والدوال ودوال الأعضاء والمتغيرات بالاسم.';

  @override
  String get readerUiNoSymbols => 'لم يتم العثور على رموز';

  @override
  String get readerUiSymbolsUnavailable =>
      'جرّب اسمًا آخر. بعض خدمات اللغات لا تدعم البحث عن الرموز في المشروع بأكمله.';

  @override
  String get readerUiFiles => 'الملفات';

  @override
  String get readerUiSymbols => 'الرموز';

  @override
  String get readerUiChanges => 'التغييرات';

  @override
  String get readerUiRefreshChanges => 'تحديث التغييرات';

  @override
  String get readerUiEntireChange => 'تغيير الملف بالكامل';

  @override
  String get readerUiWorkingTree => 'غير مثبّت';

  @override
  String get readerUiSessionScopeHint => 'الملفات التي غيّرتها هذه المحادثة.';

  @override
  String get readerUiWorkingScopeHint =>
      'كل ما لم يُثبَّت بعد، أيًا كان من غيّره.';

  @override
  String get readerUiBranchScopeHint =>
      'كل ما في هذا الفرع، مقارنةً بالفرع الرئيسي.';

  @override
  String get readerUiSaveDevice => 'حفظ على الجهاز';

  @override
  String get readerUiPreviewUnavailable => 'المعاينة غير متاحة';

  @override
  String get readerUiRendered => 'معاينة';

  @override
  String get readerUiRaw => 'المصدر';

  @override
  String get readerUiSaveFailed => 'تعذّر حفظ تفضيلات القراءة. حاول مجددًا.';

  @override
  String get readerUiSourceFirst => 'ملفات المصدر أولًا';

  @override
  String get readerUiServerOrder => 'الترتيب الافتراضي';

  @override
  String readerUiLine(int number) {
    return 'السطر $number';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return 'أُضيف $label إلى الطلب';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label موجود بالفعل في الطلب';
  }

  @override
  String readerUiReferenceFull(int count) {
    return 'عدد المراجع الموجودة في الطلب: $count';
  }

  @override
  String readerUiAttached(String name) {
    return 'تم إرفاق $name.';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف متغير',
      many: '$count ملفًا متغيرًا',
      few: '$count ملفات متغيرة',
      two: 'ملفان متغيران',
      one: 'ملف واحد متغير',
      zero: 'لا توجد ملفات متغيرة',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return 'تم إرفاق $name. عُد إلى المحادثة لإضافة تعليقك.';
  }

  @override
  String readerUiSaveNamed(String name) {
    return 'حفظ $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return 'تم حفظ $name على جهازك.';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · السطر $line';
  }

  @override
  String readerUiOnPrompt(int count) {
    return '$count في الطلب';
  }

  @override
  String readerUiNewLines(String label) {
    return 'بعد التغيير: $label';
  }

  @override
  String readerUiOldLines(String label) {
    return 'قبل التغيير: $label';
  }

  @override
  String readerUiLineRange(int first, int last) {
    return 'الأسطر $first–$last';
  }

  @override
  String readerUiReviewPrompt(String path) {
    return 'راجع `$path`';
  }

  @override
  String readerUiSaved(String name) {
    return 'تم حفظ $name.';
  }

  @override
  String get readerUiSymbolFile => 'ملف';

  @override
  String get readerUiSymbolModule => 'وحدة';

  @override
  String get readerUiSymbolNamespace => 'نطاق أسماء';

  @override
  String get readerUiSymbolPackage => 'حزمة';

  @override
  String get readerUiSymbolClass => 'صنف';

  @override
  String get readerUiSymbolMethod => 'دالة عضو';

  @override
  String get readerUiSymbolProperty => 'خاصية';

  @override
  String get readerUiSymbolField => 'حقل';

  @override
  String get readerUiSymbolConstructor => 'دالة إنشاء';

  @override
  String get readerUiSymbolEnum => 'تعداد';

  @override
  String get readerUiSymbolInterface => 'واجهة';

  @override
  String get readerUiSymbolFunction => 'دالة';

  @override
  String get readerUiSymbolVariable => 'متغير';

  @override
  String get readerUiSymbolConstant => 'ثابت';

  @override
  String get readerUiSymbolEnummember => 'عنصر تعداد';

  @override
  String get readerUiSymbolStruct => 'بنية';

  @override
  String get readerUiSymbolEvent => 'حدث';

  @override
  String get readerUiSymbolOperator => 'عامل';

  @override
  String get readerUiSymbolTypeparameter => 'معامل نوع';

  @override
  String get readerUiSymbolSymbol => 'رمز';

  @override
  String get readerUiAdded => 'مضاف';

  @override
  String get readerUiDeleted => 'محذوف';

  @override
  String get readerUiModified => 'معدّل';

  @override
  String get readerUiChanged => 'متغير';

  @override
  String get readerUiSession => 'هذه المحادثة';

  @override
  String get readerUiBranch => 'الفرع كله';

  @override
  String get readerUiAttachmentMissing =>
      'محتوى المرفق غير موجود في هذه الرسالة.';

  @override
  String get readerUiRemoteAttachment => 'معاينة المرفقات البعيدة غير متاحة.';

  @override
  String get readerUiAttachmentInvalid => 'تعذّر قراءة بيانات المرفق.';

  @override
  String get e7WorkspaceDisconnected => 'الخادم غير متصل.';

  @override
  String get e7WorkspaceNeedsYou => 'بانتظارك';

  @override
  String get e7WorkspaceStopSharing => 'إيقاف المشاركة';

  @override
  String get e7WorkspaceDisconnect => 'قطع الاتصال';

  @override
  String get e7WorkspaceBackExit => 'اضغط رجوع مرة أخرى للخروج';

  @override
  String get e7WorkspaceConnected => 'متصل';

  @override
  String get e7WorkspaceConnecting => 'جارٍ الاتصال';

  @override
  String get e7WorkspaceOffline => 'غير متصل';

  @override
  String get e7WorkspaceReconnectingAgain =>
      'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا.';

  @override
  String get e7WorkspaceDismissRequest => 'تجاهل هذا الطلب؟';

  @override
  String get e7WorkspaceDismissDetail =>
      'سيتابع OpenCode دون إجابات عن هذه الأسئلة.';

  @override
  String get e7WorkspaceNeedsInput => 'يحتاج OpenCode إلى إدخال';

  @override
  String activityAgentNeedsInput(String agent) {
    return '$agent يحتاج إلى إدخال';
  }

  @override
  String get e7WorkspaceSendAnswers => 'إرسال الإجابات';

  @override
  String get e7WorkspaceReferenceRetry =>
      'مرجع المحادثة غير متاح. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationRetry =>
      'مشروع المحادثة غير متاح. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationChangedReturn =>
      'تغيّر مشروع المحادثة. ارجع وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationChangedRetry =>
      'تغيّر مشروع المحادثة. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceReferenceUnavailable => 'مرجع المحادثة غير متاح.';

  @override
  String get e7WorkspacePaginationStuck =>
      'تعذّر تحميل الصفحة التالية من المحادثات. حدّث القائمة للمتابعة.';

  @override
  String e7WorkspaceSessionId(String id) {
    return 'المحادثة $id';
  }

  @override
  String get e7WorkspaceUnknownProject => 'مشروع غير معروف';

  @override
  String get e7WorkspaceJustNow => 'الآن';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return 'قبل $count د';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return 'قبل $count س';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return 'قبل $count ي';
  }

  @override
  String e7WorkspaceFileCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف',
      many: '$count ملفًا',
      few: '$count ملفات',
      two: 'ملفان',
      one: 'ملف واحد',
      zero: 'لا توجد ملفات',
    );
    return '$_temp0';
  }

  @override
  String get chatUiAllMatchingRequests => '(كل الطلبات المطابقة)';

  @override
  String get chatUiNoOutput => '(لا توجد مخرجات)';

  @override
  String get chatUiNoResult => '(لا توجد نتيجة)';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen =>
      'يُضاف مرجع واحد كنص عند الإرسال. لا يُحفظ مع مسودتك.';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      'إضافة مرجع لمشروع OpenCode إلى هذا الطلب';

  @override
  String get chatUiAddAnImageOrFileToThe => 'إضافة صورة أو ملف إلى الطلب';

  @override
  String get chatUiAgent => 'الوكيل';

  @override
  String get chatUiAlreadyAnsweredElsewhere => 'أُجيب عنه في مكان آخر بالفعل';

  @override
  String get chatUiAlreadyDelivered => 'سُلّم بالفعل';

  @override
  String get chatUiAlwaysAllow => 'السماح دائمًا';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover => 'سيشمل السماح الدائم أيضًا';

  @override
  String get chatUiAnswerWasCutOffByTheLength => 'قُطع الرد بسبب حد الطول';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      'يمكن لأي شخص لديه الرابط الاطّلاع على هذه المحادثة وسياقها المشترك. لا تشارك محادثات تتضمّن أسرارًا أو بيانات اعتماد أو ملفات خاصة.';

  @override
  String get chatUiAppDiagnostics => 'تشخيصات التطبيق';

  @override
  String get chatUiAppearance => 'المظهر';

  @override
  String get chatUiApplyPatch => 'تطبيق رقعة';

  @override
  String get chatUiAskOpenCode => 'اسأل OpenCode…';

  @override
  String get chatUiAttachFile => 'إرفاق ملف';

  @override
  String get chatUiAttachToPrompt => 'إرفاق بالطلب';

  @override
  String get chatUiAttachment => 'مرفق';

  @override
  String get chatUiAttachmentLimitReached => 'بلغت الحد الأقصى للمرفقات';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 =>
      'يجب ألا يتجاوز إجمالي حجم المرفقات 20 MB.';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes =>
      'متاح عند انتهاء التشغيل الحالي';

  @override
  String get chatUiBrowseProjectAndGlobalSkills =>
      'تصفّح مهارات المشروع والمهارات العامة';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      'تصفّح ملفات المشروع ومعاينتها وتنزيلها وإرفاقها';

  @override
  String get chatUiCancelAndReturnToTheComposer =>
      'الإلغاء والعودة إلى محرّر الرسالة';

  @override
  String get chatUiCancelMessage => 'إلغاء الرسالة';

  @override
  String get chatUiCancelThisPendingMessage =>
      'هل تريد إلغاء هذه الرسالة المنتظرة؟';

  @override
  String get chatUiChangeTheActiveOpenCodeConsoleOrganization =>
      'تغيير المؤسسة النشطة في OpenCode Console';

  @override
  String get chatUiChangeTheTitleShownInTheSession =>
      'تغيير العنوان الظاهر في قائمة المحادثات';

  @override
  String get chatUiChangeThisSessionSExperimentalWorkspace =>
      'تغيير البيئة السحابية التجريبية لهذه المحادثة';

  @override
  String get chatUiChangedFile => 'ملف متغيّر';

  @override
  String get chatUiChanges => 'التغييرات';

  @override
  String get chatUiChooseAServerModelByProviderAnd =>
      'اختيار نموذج على الخادم بحسب مزوّد الخدمة والقدرات';

  @override
  String get chatUiChooseAnotherModelInThePickerTo =>
      'اختر نموذجًا آخر من القائمة لإضافته إلى نماذجك الأخيرة.';

  @override
  String get chatUiChooseModel => 'اختيار نموذج';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent => 'اختيار وكيل OpenCode النشط';

  @override
  String chatUiChooseAgentMode(String agent) {
    return 'اختر طريقة عمل $agent: أوضاعه';
  }

  @override
  String get chatUiChooseTheCurrentModelVariantOrReasoning =>
      'اختيار متغيّر النموذج الحالي أو مستوى الاستدلال';

  @override
  String get chatUiCollapseReasoning => 'طيّ الاستدلال';

  @override
  String get chatUiCommandMap => 'دليل الأوامر';

  @override
  String get chatUiCompactContext => 'اختصار السياق';

  @override
  String get chatUiCompactSession => 'اختصار المحادثة';

  @override
  String get chatUiCompactingConversation => 'جارٍ اختصار المحادثة…';

  @override
  String get chatUiCompactionFailed => 'فشل اختصار السياق';

  @override
  String get chatUiCompactionStarted => 'بدأ اختصار السياق';

  @override
  String get chatUiCompose => 'كتابة رسالة';

  @override
  String get chatUiConnectProvider => 'ربط مزوّد خدمة';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      'حالة الاتصال وإصدار الخادم والوضع المباشر';

  @override
  String get chatUiContextAdded => 'أُضيف السياق';

  @override
  String get chatUiContextCompacted => 'لُخّصت الرسائل السابقة لتوفير المساحة';

  @override
  String get chatUiCopiedPasteItIntoTheComposer =>
      'نُسخ النص. الصقه في محرّر الرسالة';

  @override
  String get chatUiCopyMessageText => 'نسخ نص الرسالة';

  @override
  String get chatUiCopyShareLink => 'نسخ رابط المشاركة';

  @override
  String get chatUiCopyTheRenderedConversationAsMarkdown =>
      'نسخ المحادثة المعروضة بصيغة Markdown';

  @override
  String get chatUiCopyTranscript => 'نسخ سجل المحادثة';

  @override
  String get chatUiCreateOrCopyAPublicSessionLink =>
      'إنشاء رابط عام للمحادثة أو نسخه';

  @override
  String get chatUiCurrentSession => 'المحادثة الحالية';

  @override
  String get chatUiDelegate => 'تفويض';

  @override
  String get chatUiDelegateThisPrompt => 'تفويض هذا الطلب';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent =>
      'تفويض هذا الطلب إلى وكيل فرعي على الخادم';

  @override
  String get chatUiDelegatedSession => 'محادثة مفوّضة';

  @override
  String get chatUiDeleteMessage => 'حذف الرسالة';

  @override
  String get chatUiDeleteThisMessage => 'هل تريد حذف هذه الرسالة؟';

  @override
  String get chatUiDetails => 'التفاصيل';

  @override
  String get chatUiDirectory => 'المجلد';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink =>
      'تعطيل الرابط العام الحالي للمحادثة';

  @override
  String get chatUiDiscardDraft => 'تجاهل المسودة';

  @override
  String get chatUiDiscardPromptChanges => 'هل تريد تجاهل تغييرات الطلب؟';

  @override
  String get chatUiDiscardQueuedDraft =>
      'هل تريد تجاهل المسودة في قائمة الانتظار؟';

  @override
  String get chatUiDismissPromptError => 'إخفاء خطأ الطلب';

  @override
  String get chatUiEachAttachmentMustBe10MBOr =>
      'يجب ألا يتجاوز حجم كل مرفق 10 MB.';

  @override
  String get chatUiEdit => 'تحرير';

  @override
  String get chatUiEditDraft => 'تحرير المسودة';

  @override
  String get chatUiEditTheCurrentPromptInAFocused =>
      'تحرير الطلب الحالي في عرض مخصّص بملء الشاشة';

  @override
  String get chatUiErrorDetails => 'تفاصيل الخطأ';

  @override
  String get chatUiExpandReasoning => 'توسيع الاستدلال';

  @override
  String get chatUiExportSessionTranscript => 'تصدير سجل المحادثة';

  @override
  String get chatUiExportTranscript => 'تصدير سجل المحادثة';

  @override
  String get chatUiFetchPage => 'جلب صفحة';

  @override
  String get chatUiFiles => 'الملفات';

  @override
  String get chatUiFilesAreUnavailableInThisPreview =>
      'الملفات غير متاحة في هذه المعاينة.';

  @override
  String get chatUiFindACommandOrAction => 'البحث عن أمر أو إجراء';

  @override
  String get chatUiFindAMessageJumpToItOr =>
      'البحث عن رسالة أو الانتقال إليها أو إنشاء فرع من طلب';

  @override
  String get chatUiFindASubagent => 'البحث عن وكيل فرعي';

  @override
  String get chatUiFindFiles => 'البحث عن ملفات';

  @override
  String get chatUiFindSessionsAcrossEveryOpenCodeProject =>
      'البحث عن محادثات في كل مشاريع OpenCode';

  @override
  String get chatUiFollowAndroidOrChooseTheNativeLight =>
      'اتباع Android أو اختيار المظهر الفاتح أو الداكن للنظام';

  @override
  String get chatUiForkFromThisPrompt => 'إنشاء فرع من هذا الطلب';

  @override
  String get chatUiForkSession => 'إنشاء فرع من المحادثة';

  @override
  String get chatUiGeneratedFile => 'ملف مُنشأ';

  @override
  String get chatUiHideTimestamps => 'إخفاء الطوابع الزمنية';

  @override
  String get chatUiImageDataIsUnavailable => 'بيانات الصورة غير متاحة.';

  @override
  String get chatUiInputRequested => 'مطلوب إدخال';

  @override
  String get chatUiInspectGitLanguageServicesAndFormattersFor =>
      'فحص Git وخدمات اللغة وأدوات التنسيق لهذا المشروع';

  @override
  String get chatUiInspectMCPStatusAuthenticationAndResources =>
      'فحص حالة MCP والمصادقة والموارد';

  @override
  String get chatUiInspectCurrentTokensCacheCostAndContext =>
      'فحص الرموز الحالية وذاكرة التخزين المؤقت والتكلفة واستخدام السياق';

  @override
  String get chatUiInspectToolsCallableByTheActiveProvider =>
      'فحص الأدوات التي يمكن لمزوّد الخدمة والنموذج النشطين استدعاؤها';

  @override
  String get chatUiItsTextReturnsToTheComposerAs =>
      'يعود نصها إلى محرّر الرسالة كمسودة.';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      'انتقل إلى أي موضع. يستعيد إنشاء الفرع طلبًا لتحريره.';

  @override
  String get chatUiKeepItPending => 'إبقاؤها منتظرة';

  @override
  String get chatUiKeepItQueued => 'إبقاؤها في قائمة الانتظار';

  @override
  String get chatUiLanguageServer => 'خادم اللغة';

  @override
  String get chatUiList => 'عرض قائمة';

  @override
  String get chatUiLoadingSubagents => 'جارٍ تحميل الوكلاء الفرعيين…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript =>
      'تفاصيل الاستدلال الطويلة مطوية في سجل المحادثة';

  @override
  String get chatUiMCPServers => 'خوادم MCP';

  @override
  String get chatUiManageProviderAndIntegrationAuthentication =>
      'إدارة مصادقة مزوّدي الخدمة والتكاملات';

  @override
  String get chatUiMessage => 'الرسالة';

  @override
  String get chatUiMessageTimeline => 'التسلسل الزمني للرسائل';

  @override
  String get chatUiMessageTimestampsHidden => 'أُخفيت الطوابع الزمنية للرسائل';

  @override
  String get chatUiMessageTimestampsShown => 'أُظهرت الطوابع الزمنية للرسائل';

  @override
  String get chatUiModel => 'النموذج';

  @override
  String get chatUiModelAndAgent => 'النموذج والوكيل';

  @override
  String get chatUiMoveSession => 'نقل المحادثة';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory =>
      'نقل هذه المحادثة إلى مشروع آخر';

  @override
  String get chatUiMoved => 'نُقلت';

  @override
  String get chatUiNavigate => 'التنقّل';

  @override
  String get chatUiNoAnswer => 'لا توجد إجابة';

  @override
  String get chatUiNoMatchingMessages => 'لا توجد رسائل مطابقة';

  @override
  String get chatUiNoShareLinkWasReturned => 'لم يُعد رابط مشاركة';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer =>
      'لا يتوفر وكلاء فرعيون من هذا الخادم';

  @override
  String get chatUiNotConnectedToTheServerRightNow =>
      'غير متصل بالخادم حاليًا.';

  @override
  String get chatUiOpenParentSession => 'فتح المحادثة الأم';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals =>
      'فتح جلسات الطرفية الدائمة للمشروع';

  @override
  String get chatUiOpenProviders => 'فتح مزوّدي الخدمة';

  @override
  String get chatUiOpenSubagentSession => 'فتح محادثة الوكيل الفرعي';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      'أوامر OpenCode غير متاحة دون اتصال.';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt =>
      'تعذّر على OpenCode إتمام هذا الطلب.';

  @override
  String get chatUiOpenCodeIsReconnecting => 'جارٍ إعادة اتصال OpenCode.';

  @override
  String chatUiAgentIsReconnecting(String agent) {
    return 'يعيد $agent الاتصال.';
  }

  @override
  String chatUiAgentIsReconnectingTryAgainShortly(String agent) {
    return 'يعيد $agent الاتصال. حاول مرة أخرى بعد قليل.';
  }

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا بعد قليل.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا عندما يتصل الخادم.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا.';

  @override
  String get chatUiOpenCodeNeedsInput => 'يحتاج OpenCode إلى إدخال';

  @override
  String get chatUiOpenCodeServerCommand => 'أمر خادم OpenCode';

  @override
  String get chatUiOutputPruned => 'حُذفت المخرجات';

  @override
  String get chatUiPendingChange => 'تغيير منتظر';

  @override
  String get chatUiProjectFiles => 'ملفات المشروع';

  @override
  String get chatUiProjectHealth => 'حالة المشروع';

  @override
  String get chatUiProjectReference => 'مرجع مشروع';

  @override
  String get chatUiProjectReferences => 'مراجع المشاريع';

  @override
  String get chatUiProjectsAndWorkspaces => 'المشاريع وأشجار العمل';

  @override
  String get chatUiPromptEditor => 'محرّر الطلب';

  @override
  String get chatUiPromptFromParentAgent => 'طلب من الوكيل الأب';

  @override
  String get chatUiPromptTools => 'أدوات الطلب';

  @override
  String get chatUiQuestion => 'سؤال';

  @override
  String get chatUiQuestions => 'الأسئلة';

  @override
  String get chatUiQueuedRunsAfterThisTurn =>
      'في قائمة الانتظار · يُشغّل بعد هذا التبادل';

  @override
  String get chatUiQueuedWillSendWhenReconnected =>
      'في قائمة الانتظار — سيُرسل عند إعادة الاتصال';

  @override
  String get chatUiRead => 'قراءة';

  @override
  String get chatUiReasoningExpandedInTheTranscript =>
      'تفاصيل الاستدلال موسّعة في سجل المحادثة';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice =>
      'التسجيل وتحويل الصوت إلى نص على هذا الجهاز';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      'احتُفظ بالمرجع لطلبك التالي — لا تتضمّنه الأوامر.';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      'احتُفظ بالمراجع لطلبك التالي — لا تتضمّنها الأوامر.';

  @override
  String get chatUiReloadMessages => 'تحديث الرسائل';

  @override
  String get chatUiRename => 'إعادة تسمية';

  @override
  String get chatUiRenameSession => 'إعادة تسمية المحادثة';

  @override
  String get chatUiRestoreRevertedPrompt => 'استعادة الطلب المتراجَع عنه';

  @override
  String get chatUiRestoreTheCurrentlyRevertedSessionState =>
      'استعادة حالة المحادثة المتراجَع عنها حاليًا';

  @override
  String get chatUiRetryLastPrompt => 'إعادة محاولة الطلب الأخير';

  @override
  String get chatUiRevertLastPrompt => 'التراجع عن الطلب الأخير';

  @override
  String get chatUiReviewCommentAddedToThePrompt =>
      'أُضيف تعليق المراجعة إلى الطلب';

  @override
  String get chatUiReviewHandledAppErrorsAndSendA =>
      'مراجعة أخطاء التطبيق المعالَجة وإرسال تقرير ببيانات حساسة محجوبة';

  @override
  String get chatUiReviewTheActualDiffForThisSession =>
      'مراجعة الفروق الفعلية لهذه المحادثة';

  @override
  String get chatUiRunOnYourComputer => 'التشغيل على حاسوبك';

  @override
  String get chatUiRunShellCommand => 'تشغيل أمر صدفة';

  @override
  String get chatRunShellLabel => 'الأمر';

  @override
  String get chatRunShellHint => 'npm test';

  @override
  String get chatRunShellHelper =>
      'يشغّله الوكيل في هذا المشروع وتُضاف مخرجاته إلى المحادثة.';

  @override
  String get chatRunShellEmpty => 'اكتب أمرًا لتشغيله.';

  @override
  String get chatRenameEmpty => 'اكتب عنوانًا.';

  @override
  String get chatUiSaveTheConversationAsAMarkdownFile =>
      'حفظ المحادثة كملف Markdown';

  @override
  String get chatUiScope => 'النطاق';

  @override
  String get chatUiSearchMessages => 'البحث في الرسائل';

  @override
  String get chatUiSearchMobileActionsAndServerProvidedCommands =>
      'البحث في إجراءات الهاتف والأوامر التي يقدّمها الخادم';

  @override
  String get chatUiSearchText => 'البحث عن نص';

  @override
  String get chatUiSelectAModelBeforeCompactingThisSession =>
      'اختر نموذجًا قبل اختصار هذه المحادثة.';

  @override
  String get chatUiServerCommands => 'أوامر الخادم';

  @override
  String get chatUiServerMessage => 'رسالة من الخادم';

  @override
  String get chatUiServerStatus => 'حالة الخادم';

  @override
  String get chatUiSessionChanges => 'تغييرات المحادثة';

  @override
  String get chatUiSessionContext => 'سياق المحادثة';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      'شُوركت المحادثة. انسخ الرابط الظاهر يدويًا.';

  @override
  String get chatUiShareLinkCopied => 'نُسخ رابط المشاركة';

  @override
  String get chatUiShareSession => 'مشاركة المحادثة';

  @override
  String get chatUiShareThisSession => 'هل تريد مشاركة هذه المحادثة؟';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView =>
      'مشتركة: يمكن لأي شخص لديه الرابط الاطّلاع عليها';

  @override
  String get chatUiShowAllSubagentSessions => 'عرض كل محادثات الوكلاء الفرعيين';

  @override
  String get chatUiShowTimestamps => 'عرض الطوابع الزمنية';

  @override
  String get chatUiSkill => 'مهارة ·';

  @override
  String get chatUiSkills => 'المهارات';

  @override
  String get chatUiSlashCommandsAndAgents =>
      'الأوامر ذات الشرطة المائلة والوكلاء';

  @override
  String get chatUiStartACleanSessionInThisWorkspace =>
      'بدء محادثة جديدة خالية من السياق في هذا المشروع';

  @override
  String get chatUiStopSharing => 'إيقاف المشاركة';

  @override
  String get chatUiSubagent => 'وكيل فرعي';

  @override
  String get chatUiSubagentFailed => 'فشل الوكيل الفرعي.';

  @override
  String get chatUiSubagentWorking => 'الوكيل الفرعي يعمل…';

  @override
  String get chatUiSubagentsCouldNotBeLoaded => 'تعذّر تحميل الوكلاء الفرعيين';

  @override
  String get chatUiSummarizeTheSessionUsingTheSelectedModel =>
      'تلخيص المحادثة باستخدام النموذج المحدّد';

  @override
  String get chatUiSwitchOrganization => 'تبديل المؤسسة';

  @override
  String get chatUiSwitchProjectDirectoryOrWorktree =>
      'تبديل المشروع أو المجلد أو شجرة العمل';

  @override
  String get chatUiSystemUpdate => 'تحديث النظام';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo =>
      'أخبر الوكيل بالسبب أو بما ينبغي فعله بدلًا من ذلك (اختياري)';

  @override
  String get chatUiThatMessageIsNoLongerInThis =>
      'لم تعد تلك الرسالة في هذه المحادثة.';

  @override
  String get chatUiTheFileHasNoContentToAttach =>
      'لا يحتوي الملف على محتوى لإرفاقه.';

  @override
  String get chatUiTheFileHasNoContentToSave =>
      'لا يحتوي الملف على محتوى لحفظه.';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe =>
      'تغيّر النموذج أو المشروع. افتح الطلب الحالي مجددًا.';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom =>
      'الملف المُنشأ غير متاح من هذا الخادم.';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      'ستُحذف الرسالة وكل أجزائها نهائيًا من المحادثة، ولن تكون متاحة للردود اللاحقة. لن يُتراجع عن تغييرات الملفات التي أجرتها.';

  @override
  String get chatUiTheServerReturnedEmptyImageData =>
      'أعاد الخادم بيانات صورة فارغة.';

  @override
  String get chatUiThisDraftHasNotBeenSentTo =>
      'لم تُرسل هذه المسودة إلى OpenCode.';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      'هذه المسودة أكبر من أن تُضاف إلى قائمة الانتظار، أو أن القائمة ممتلئة بمسودات أحدث. أزل مرفقًا أو امسح الطلبات في قائمة الانتظار من الإعدادات.';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn =>
      'لا يمكن استعادة هذا الطلب لأن أحد المرفقات غير متاح.';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn =>
      'لا يمكن إعادة محاولة هذا الطلب لأن أحد المرفقات غير متاح.';

  @override
  String get chatUiTimeline => 'التسلسل الزمني';

  @override
  String get chatUiTimestampsUsage => 'الطوابع الزمنية والاستخدام';

  @override
  String get chatUiTitle => 'العنوان';

  @override
  String get chatUiTodos => 'المهام';

  @override
  String get chatUiToggleCreationTimesBesideTranscriptEntries =>
      'إظهار أوقات الإنشاء أو إخفاؤها بجانب عناصر سجل المحادثة';

  @override
  String get chatUiToggleLongReasoningDetailsAcrossTheTranscript =>
      'إظهار تفاصيل الاستدلال الطويلة أو إخفاؤها في سجل المحادثة';

  @override
  String get chatUiToolFailed => 'فشلت الأداة.';

  @override
  String get chatUiToolsAndCapabilities => 'الأدوات والقدرات';

  @override
  String get chatUiTranscriptCopiedAsMarkdown =>
      'نُسخ سجل المحادثة بصيغة Markdown';

  @override
  String get chatUiTranscriptDisplay => 'عرض سجل المحادثة';

  @override
  String get chatUiTranscriptSaved => 'حُفظ سجل المحادثة';

  @override
  String get chatUiVoiceConversationWasInterrupted =>
      'انقطعت المحادثة الصوتية.';

  @override
  String get chatUiVoiceInput => 'الإدخال الصوتي';

  @override
  String get chatUiVoiceInputIsUnavailable => 'الإدخال الصوتي غير متاح.';

  @override
  String get chatUiLoadTools => 'تحميل الأدوات';

  @override
  String chatUiToolFrom(String tool, String server) {
    return '$tool · $server';
  }

  @override
  String get chatUiWebSearch => 'البحث في الويب';

  @override
  String get chatUiWrite => 'كتابة';

  @override
  String get chatUiWriteYourOpenCodePrompt => 'اكتب طلبك إلى OpenCode…';

  @override
  String get chatUiYou => 'أنت';

  @override
  String get chatUiYourOriginalComposerDraftAndAttachmentsWill =>
      'ستبقى مسودتك الأصلية في محرّر الرسالة ومرفقاتها دون تغيير.';

  @override
  String get chatUiInThisChat => 'في هذه المحادثة';

  @override
  String get chatUiNewFile => 'ملف جديد';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return 'في قائمة الانتظار، سيُرسَل عند إعادة الاتصال. $detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '‎/$command غير متاح حاليًا.';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return 'يمكنك إرفاق ما يصل إلى $count ملفات.';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أُرسل $count طلب من قائمة الانتظار',
      many: 'أُرسل $count طلبًا من قائمة الانتظار',
      few: 'أُرسلت $count طلبات من قائمة الانتظار',
      two: 'أُرسل طلبان من قائمة الانتظار',
      one: 'أُرسل طلب واحد من قائمة الانتظار',
      zero: 'لم يُرسَل أي طلب من قائمة الانتظار',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة تنتظر خوادم أخرى',
      many: '$count مسودة تنتظر خوادم أخرى',
      few: '$count مسودات تنتظر خوادم أخرى',
      two: 'مسودتان تنتظران خوادم أخرى',
      one: 'مسودة واحدة تنتظر خوادم أخرى',
      zero: 'لا مسودات تنتظر خوادم أخرى',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return 'ستستخدم الأدوار التالية في هذه المحادثة $model.';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '‎@$name موجود بالفعل في الطلب';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return 'تم إرفاق $filename. أضف تعليقك.';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return 'حفظ $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return 'تم حفظ $filename على جهازك.';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      many: '$count مسودة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      few: '$count مسودات في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      two: 'مسودتان في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      one: 'مسودة واحدة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      zero: 'لا مسودات في قائمة الانتظار.',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة تنتظر خوادم أخرى.',
      many: '$count مسودة تنتظر خوادم أخرى.',
      few: '$count مسودات تنتظر خوادم أخرى.',
      two: 'مسودتان تنتظران خوادم أخرى.',
      one: 'مسودة واحدة تنتظر خوادم أخرى.',
      zero: 'لا مسودات تنتظر خوادم أخرى.',
    );
    return '$_temp0';
  }

  @override
  String chatUiQuestionCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سؤال',
      many: '$count سؤالًا',
      few: '$count أسئلة',
      two: 'سؤالان',
      one: 'سؤال واحد',
      zero: 'لا أسئلة',
    );
    return '$_temp0';
  }

  @override
  String chatUiPermissionNeeded(Object title) {
    return 'إذن مطلوب: $title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return 'سؤال: $title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سؤال',
      many: '$count سؤالًا',
      few: '$count أسئلة',
      two: 'سؤالان',
      one: 'سؤال واحد',
      zero: 'لا أسئلة',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return 'تم تجاوز حد المعدل. تجري إعادة المحاولة$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return 'تم تجاوز حد المعدل. إعادة المحاولة$attempt بعد $time';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يُضاف $count مرجع كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      many: 'يُضاف $count مرجعًا كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      few: 'تُضاف $count مراجع كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      two: 'يُضاف مرجعان كنص عند الإرسال. لا يُحفظان مع مسودتك.',
      one: 'يُضاف مرجع واحد كنص عند الإرسال. لا يُحفظ مع مسودتك.',
      zero: 'لا تُضاف مراجع عند الإرسال.',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '$count مرفق';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count رسالة سابقة',
      many: '$count رسالة سابقة',
      few: '$count رسائل سابقة',
      two: 'رسالتان سابقتان',
      one: 'رسالة سابقة واحدة',
      zero: 'لا رسائل سابقة',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count رمز';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '$position من $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return 'وكيل فرعي · $count';
  }

  @override
  String chatUiToolsSummary(Object tools) {
    return 'الأدوات: $tools';
  }

  @override
  String chatUiFromLine(Object line) {
    return 'من السطر $line';
  }

  @override
  String chatUiLineCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سطر',
      many: '$count سطرًا',
      few: '$count أسطر',
      two: 'سطران',
      one: 'سطر واحد',
      zero: 'لا أسطر',
    );
    return '$_temp0';
  }

  @override
  String chatUiLineRange(Object start, Object end) {
    return '‎L$start–$end';
  }

  @override
  String chatUiEntryCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر',
      many: '$count عنصرًا',
      few: '$count عناصر',
      two: 'عنصران',
      one: 'عنصر واحد',
      zero: 'لا عناصر',
    );
    return '$_temp0';
  }

  @override
  String chatUiFoundCount(Object count) {
    return 'عُثر على $count';
  }

  @override
  String chatUiMatchCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تطابق',
      many: '$count تطابقًا',
      few: '$count تطابقات',
      two: 'تطابقان',
      one: 'تطابق واحد',
      zero: 'لا تطابقات',
    );
    return '$_temp0';
  }

  @override
  String chatUiFileCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف',
      many: '$count ملفًا',
      few: '$count ملفات',
      two: 'ملفان',
      one: 'ملف واحد',
      zero: 'لا ملفات',
    );
    return '$_temp0';
  }

  @override
  String chatUiProviderSearch(Object provider) {
    return 'بحث $provider';
  }

  @override
  String chatUiResultCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نتيجة',
      many: '$count نتيجة',
      few: '$count نتائج',
      two: 'نتيجتان',
      one: 'نتيجة واحدة',
      zero: 'لا نتائج',
    );
    return '$_temp0';
  }

  @override
  String chatUiAnsweredCount(Object count) {
    return 'تمت الإجابة عن $count';
  }

  @override
  String chatUiAskedCount(Object count) {
    return 'طُرح $count';
  }

  @override
  String chatUiDurationMinutesSeconds(Object minutes, Object seconds) {
    return '$minutes د $seconds ث';
  }

  @override
  String chatUiDurationSeconds(Object seconds) {
    return '$seconds ث';
  }

  @override
  String chatUiFileLoadFailed(Object error) {
    return 'تعذّر تحميل هذا الملف من خادم OpenCode: $error';
  }

  @override
  String chatUiMoreEntries(Object total) {
    return '$total إجمالًا · المزيد متاح';
  }

  @override
  String chatUiEntryTotal(Object total) {
    return '$total عنصر';
  }

  @override
  String chatUiAnsweredDetail(Object answer) {
    return 'الإجابة: $answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return 'جارٍ تحميل $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return 'معاينة الصورة المُنشأة $filename';
  }

  @override
  String chatUiParentSession(Object title) {
    return 'المحادثة الأصل · $title';
  }

  @override
  String chatUiChooseOption(Object option) {
    return 'اختيار: $option';
  }

  @override
  String get chatUiMainSession => 'المحادثة الرئيسية';

  @override
  String get chatUiTodo => 'المهام';

  @override
  String get chatUiBackgroundResult => 'نتيجة العمل في الخلفية';

  @override
  String get chatUiBackgroundComplete => 'اكتمل';

  @override
  String get chatUiBackgroundError => 'فشل';

  @override
  String get chatUiBackgroundCancelled => 'أُلغي';

  @override
  String get chatUiResultSourceDetails => 'تفاصيل رسالة الخادم';

  @override
  String get chatUiResultOpenChild => 'فتح محادثة الوكيل الفرعي';

  @override
  String get chatUiNoResultText => 'لم يُرجع الخادم أي نص للنتيجة.';

  @override
  String get chatUiTimedOut => 'انتهت المهلة';

  @override
  String get chatUiKilled => 'أُوقف';

  @override
  String get chatUiTruncated => 'مقتطع';

  @override
  String get chatUiUpdated => 'محدَّث';

  @override
  String get chatUiError => 'خطأ';

  @override
  String get chatUiAssistant => 'المساعد';

  @override
  String get chatUiUser => 'المستخدم';

  @override
  String get chatUiOpenCodeSession => 'محادثة OpenCode';

  @override
  String get chatUiTool => 'أداة';

  @override
  String get chatUiFile => 'ملف';

  @override
  String get e7LibraryReportABug => 'الإبلاغ عن مشكلة';

  @override
  String get e7LibraryKeyboardShortcuts => 'اختصارات لوحة المفاتيح';

  @override
  String get e7LibraryHTTPHeader => 'ترويسة HTTP';

  @override
  String get e7LibraryEnvironmentVariable => 'متغير بيئة';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      'يعيد الخادم الاتصال. حاول مجددًا بعد قليل.';

  @override
  String get e7LibraryWhatIsMCP => 'ما هو MCP؟';

  @override
  String get e7LibrarySavingConfiguration => 'جارٍ حفظ الإعدادات';

  @override
  String get e7LibrarySaveMCPServer => 'حفظ خادم MCP';

  @override
  String get e7LibraryThisProject => 'هذا المشروع';

  @override
  String e7LibraryWritesOnlyTo(String detail1) {
    return 'يكتب في $detail1 فقط.';
  }

  @override
  String get e7LibraryWritesToThisOpenCodeServerSGlobal =>
      'يكتب في الإعدادات العامة لخادم OpenCode هذا.';

  @override
  String get e7LibraryServerName => 'اسم الخادم';

  @override
  String get e7LibraryDocsOrBrowserTools => 'مثل docs أو browser-tools';

  @override
  String get e7LibraryUniqueWithinTheSelectedConfiguration =>
      'اسم فريد ضمن الإعدادات المحددة.';

  @override
  String get e7LibraryEnterAServerName => 'أدخل اسمًا للخادم';

  @override
  String get e7LibraryRemoteURL => 'رابط بعيد';

  @override
  String get e7LibraryLocalCommand => 'أمر محلي';

  @override
  String get e7LibraryOptional => 'اختياري';

  @override
  String get e7LibraryEnterAValueGreaterThanZero => 'أدخل قيمة أكبر من صفر';

  @override
  String get e7LibraryMCPEndpointURL => 'رابط نقطة اتصال MCP';

  @override
  String get e7LibraryHTTPIsAcceptedForLocalDevelopmentServers =>
      'يمكن استخدام HTTP مع خوادم التطوير المحلية.';

  @override
  String get e7LibraryEnterAValidHTTPOrHTTPSURL =>
      'أدخل رابط HTTP أو HTTPS صالحًا لا يحتوي على بيانات اعتماد';

  @override
  String get e7LibraryDetectOAuthAutomatically => 'اكتشاف OAuth تلقائيًا';

  @override
  String get e7LibraryTurnThisOffWhenTheServerUses =>
      'أوقف هذا الخيار إذا كان الخادم يستخدم الترويسات ولا ينبغي له بدء OAuth مطلقًا.';

  @override
  String get e7LibraryCommandAndArguments => 'الأمر والوسائط';

  @override
  String get e7LibraryRunsOnTheOpenCodeServerNotThis =>
      'يُنفَّذ على خادم OpenCode وليس على هذا الهاتف. أدخل وسيطة واحدة في كل سطر.';

  @override
  String get e7LibraryEnterACommand => 'أدخل أمرًا';

  @override
  String get e7LibraryWorkingDirectory => 'مجلد العمل';

  @override
  String get e7LibraryOptionalServerPath => 'مسار اختياري على الخادم';

  @override
  String get e7LibraryEnvironmentVariables => 'متغيرات البيئة';

  @override
  String e7LibraryInvalidOnLineUseKEYVALUE(String detail1, String detail2) {
    return 'قيمة $detail1 غير صالحة في السطر $detail2. استخدم KEY=VALUE.';
  }

  @override
  String e7LibraryInvalidNameOnLine(String detail1, String detail2) {
    return 'اسم $detail1 غير صالح في السطر $detail2.';
  }

  @override
  String e7LibraryDuplicateName(String detail1, String detail2) {
    return 'اسم $detail1 مكرر: «$detail2».';
  }

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgain =>
      'يعيد الخادم الاتصال. حاول مجددًا.';

  @override
  String get e7LibraryRevokeAlwaysAllowedAction => 'إلغاء الإذن؟';

  @override
  String get e7LibraryAction => 'الإجراء';

  @override
  String get e7LibraryResource => 'المورد';

  @override
  String get e7LibraryRevokeAccess => 'إلغاء الإذن';

  @override
  String get e7LibraryAlwaysAllowedActionRevoked =>
      'أُلغي السماح الدائم للإجراء';

  @override
  String get e7LibraryAlwaysAllowedActions => 'الإجراءات المسموح بها دائمًا';

  @override
  String get e7LibraryNoAlwaysAllowedActions =>
      'لا توجد إجراءات مسموح بها دائمًا';

  @override
  String get e7LibraryTheLastActionFailed => 'فشل الإجراء الأخير';

  @override
  String e7LibraryRevokeAccess2(String detail1) {
    return 'إلغاء الإذن لإجراء $detail1';
  }

  @override
  String get e7LibraryToolsAndCapabilities => 'الأدوات والإمكانات';

  @override
  String get e7LibraryRefreshTools => 'تحديث الأدوات';

  @override
  String get e7LibraryOpenCodeToolsDependOnTheProviderAnd =>
      'تعتمد أدوات OpenCode على مزوّد الخدمة والنموذج المستخدمَين في المحادثة النشطة.';

  @override
  String get e7LibraryChooseModel => 'اختيار النموذج';

  @override
  String e7LibrarySearchTools2(String detail1) {
    return 'البحث في الأدوات ($detail1)';
  }

  @override
  String get e7LibraryRegisteredInventoryUnavailable =>
      'قائمة الأدوات المسجّلة غير متاحة';

  @override
  String get e7LibraryServerCapabilityUnavailable => 'إمكانات الخادم غير متاحة';

  @override
  String get e7LibraryNoToolsForThisModel => 'لا توجد أدوات لهذا النموذج';

  @override
  String get e7LibraryNoDescriptionReturnedByOpenCode =>
      'لم يُرجع OpenCode وصفًا';

  @override
  String get e7LibraryCopyParameterSchema => 'نسخ مخطط المعلمات';

  @override
  String get e7LibraryNoProjectSelected => 'لم يُحدَّد مشروع';

  @override
  String get e7LibraryNoProjectFolderIsOpenChooseOne =>
      'لا يوجد مشروع مفتوح. اختر مشروعًا من أعلى «الملفات».';

  @override
  String get e7LibrarySwitchProject => 'تبديل المشروع';

  @override
  String get e7LibraryWorktrees => 'أشجار العمل';

  @override
  String get e7LibraryManagedWorkspaces => 'البيئات السحابية';

  @override
  String get e7LibraryProjectHealth => 'حالة المشروع';

  @override
  String get e7LibraryOpenCodeIsReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get e7LibraryCloudEnvironments => 'البيئات السحابية';

  @override
  String get e7LibraryDiscoverExistingEnvironments => 'اكتشاف البيئات الموجودة';

  @override
  String get e7LibraryNewEnvironment => 'بيئة جديدة';

  @override
  String get e7LibraryEnvironments => 'البيئات';

  @override
  String get e7LibraryNoCloudEnvironments => 'لا توجد بيئات سحابية';

  @override
  String get e7LibraryEnvironmentRefreshFailed => 'فشل تحديث البيئات';

  @override
  String get e7LibraryRetryCloudEnvironments => 'إعادة المحاولة';

  @override
  String get e7LibraryAdapterRefreshFailed => 'فشل تحديث المهايئات';

  @override
  String get e7LibraryConnected => 'متصل';

  @override
  String get e7LibraryConnecting => 'جارٍ الاتصال';

  @override
  String get e7LibraryDisconnected => 'غير متصل';

  @override
  String get e7LibraryEnvironmentActions => 'إجراءات البيئة';

  @override
  String get e7LibraryOpenAgain => 'فتح مجددًا';

  @override
  String get e7LibraryNewManagedWorkspace => 'بيئة سحابية جديدة';

  @override
  String get e7LibraryCreateAndOpen => 'إنشاء وفتح';

  @override
  String e7LibraryRemove(String detail1) {
    return 'حذف $detail1؟';
  }

  @override
  String get e7LibraryRemovePermanently => 'حذف نهائي';

  @override
  String e7LibraryIsReady(String detail1) {
    return 'أصبح $detail1 جاهزًا';
  }

  @override
  String get e7LibraryOpenCodeCouldNotPrepareThisWorktree =>
      'تعذّر تجهيز شجرة العمل هذه.';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return 'أُنشئ $detail1. لم تُؤكّد حالة إعداده بعد.';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis =>
      'انتظر حتى تنتهي شجرة العمل هذه من التجهيز.';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations =>
      'لم يغيّر الخادم المشروع.';

  @override
  String e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
    String detail1,
    String detail2,
  ) {
    return 'تعذّر التحقق من $detail1 قبل هذا الإجراء الذي يحذف البيانات: $detail2';
  }

  @override
  String e7LibraryResetToTheDefaultBranch(String detail1) {
    return 'أُعيد $detail1 إلى الفرع الافتراضي';
  }

  @override
  String e7LibraryAndItsBranchWereRemoved(String detail1) {
    return 'حُذف $detail1 وفرعه';
  }

  @override
  String e7LibraryReset(String detail1) {
    return 'إعادة ضبط $detail1؟';
  }

  @override
  String get e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes =>
      'سيؤدي هذا إلى تجاهل التغييرات المتتبَّعة نهائيًا وحذف جميع الملفات غير المتتبَّعة والمتجاهَلة. تُعاد الوحدات الفرعية أيضًا إلى حالتها الأصلية وتُنظَّف. لا يمكن التراجع عن هذا الإجراء.';

  @override
  String get e7LibraryResetWorktree => 'إعادة ضبط شجرة العمل';

  @override
  String get e7LibraryRefreshWorktrees => 'تحديث أشجار العمل';

  @override
  String get e7LibraryNewWorktree => 'شجرة عمل جديدة';

  @override
  String get e7LibraryNoIsolatedWorktreesYet => 'لا توجد أشجار عمل معزولة بعد';

  @override
  String get e7LibraryPreparingFilesAndProjectTasks =>
      'جارٍ تجهيز الملفات ومهام المشروع…';

  @override
  String get e7LibraryWorktreeActions => 'إجراءات شجرة العمل';

  @override
  String get e7LibraryReset2 => 'إعادة ضبط';

  @override
  String get e7LibraryNameOptional => 'الاسم (اختياري)';

  @override
  String get e7LibraryTheWorktreeDirectoryAndItsGitBranch =>
      'سيُحذف مجلد شجرة العمل وفرع Git الخاص به نهائيًا. تبقى المحادثات السابقة في السجل، لكن مجلد عملها لن يعود موجودًا.';

  @override
  String get e7LibraryInitializeGitRepository => 'تهيئة مستودع Git؟';

  @override
  String get e7LibraryOpenCodeWillRunGitInitInThe =>
      'سينفّذ OpenCode الأمر git init في المشروع الحالي. لن تتغير الملفات الموجودة ولن تُثبَّت في Git. يتيح هذا استخدام الفروع وتغييرات شجرة العمل وميزات المراجعة.';

  @override
  String get e7LibraryInitializeGit => 'تهيئة Git';

  @override
  String get e7LibraryGitRepositoryInitialized => 'تمت تهيئة مستودع Git';

  @override
  String get e7LibraryVersionControl => 'التحكم بالإصدارات';

  @override
  String get e7LibraryLanguageServices => 'خدمات اللغات';

  @override
  String get e7LibraryFormatters => 'أدوات التنسيق';

  @override
  String get e7LibraryGitIsNotInitialized => 'لم تتم تهيئة Git';

  @override
  String get e7LibraryInitializeThisProjectToEnableBranchesWorking =>
      'هيّئ هذا المشروع لاستخدام الفروع وتغييرات شجرة العمل والمراجعة.';

  @override
  String get e7LibraryRunGitInitFromATerminal => 'نفّذ `git init` من الطرفية';

  @override
  String get e7LibraryGitInitializationFailed => 'فشلت تهيئة Git';

  @override
  String get e7LibraryNoActiveBranch => 'لا يوجد فرع نشط';

  @override
  String e7LibraryDefaultBranch(String detail1) {
    return 'الفرع الافتراضي: $detail1';
  }

  @override
  String get e7LibraryWorkingTreeIsClean => 'شجرة العمل خالية من التغييرات';

  @override
  String e7LibraryChangedFiles(String detail1) {
    return 'الملفات المتغيرة: $detail1';
  }

  @override
  String get e7LibraryNoUncommittedChanges => 'لا توجد تغييرات غير مثبَّتة';

  @override
  String get e7LibraryNoActiveLanguageServices => 'لا توجد خدمات لغات نشطة';

  @override
  String get e7LibraryOpenCodeActivatesThemWhileItInspectsSupported =>
      'يفعّلها OpenCode أثناء فحص ملفات المصدر المدعومة خلال البرمجة.';

  @override
  String get e7LibraryNoFormattersConfigured => 'لا توجد أدوات تنسيق مضبوطة';

  @override
  String get e7LibraryEnabled => 'مفعّل';

  @override
  String get e7LibraryDisabled => 'معطّل';

  @override
  String e7LibraryLoading(String detail1) {
    return 'جارٍ تحميل $detail1';
  }

  @override
  String get e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe =>
      'التقاط رابط العودة تلقائيًا غير متاح. ألصق رابط العودة أو رمز التفويض.';

  @override
  String get e7LibraryThePhoneIsSecurelyListeningForThis =>
      'ينتظر الهاتف رابط العودة لهذا التفويض بأمان. يمكنك إدخاله يدويًا أيضًا.';

  @override
  String get e7LibraryCallbackURLOrCode => 'رابط العودة أو الرمز';

  @override
  String get e7LibraryUpdating => 'جارٍ التحديث…';

  @override
  String get e7LibraryNotConnected => 'غير متصل';

  @override
  String get e7LibraryServerEnvironment => 'بيئة الخادم';

  @override
  String get e7LibraryServerManaged => 'يديره الخادم';

  @override
  String get e7LibraryConnect => 'اتصال';

  @override
  String get e7LibraryAuthenticationFailed => 'فشلت المصادقة';

  @override
  String get e7LibraryAuthenticationAttemptExpired =>
      'انتهت صلاحية محاولة المصادقة';

  @override
  String get e7LibraryAuthenticationComplete => 'اكتملت المصادقة';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe =>
      'عُد من المتصفح وأدخل رمز التفويض.';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      'أكمل المصادقة في المتصفح، ثم تحقّق من حالتها.';

  @override
  String get e7LibraryAuthorizationCode => 'رمز التفويض';

  @override
  String get e7LibrarySelectAnOption => 'اختر خيارًا';

  @override
  String get e7LibraryEnterAValue => 'أدخل قيمة';

  @override
  String get e7LibraryTheServerReturnedAnUnsafeAuthorizationLink =>
      'أرجع الخادم رابط تفويض غير آمن. يُسمح فقط بروابط HTTPS ذات مضيف صالح والخالية من بيانات اعتماد مضمّنة.';

  @override
  String get e7LibraryCouldNotLoadThisSection => 'تعذّر تحميل هذا القسم';

  @override
  String get e7LibrarySkills => 'المهارات';

  @override
  String get e7LibraryNoSkillsAvailable => 'لا توجد مهارات متاحة';

  @override
  String get e7LibraryModelsAndAgents => 'النماذج والوكلاء';

  @override
  String get e7LibraryUnavailable => 'غير متاح';

  @override
  String get e7LibraryFinishOrCancelTheCurrentMCPAuthorization =>
      'أكمل تفويض MCP الحالي أو ألغِه أولًا.';

  @override
  String get e7LibraryCouldNotOpenTheAuthorizationPage =>
      'تعذّر فتح صفحة التفويض';

  @override
  String e7LibraryAuthenticated(String detail1) {
    return 'تمت مصادقة $detail1';
  }

  @override
  String get e7LibraryCouldNotConfirmMCPAuthentication =>
      'تعذّر تأكيد مصادقة MCP';

  @override
  String get e7LibraryMCPServerSavedInOpenCode => 'حُفظ خادم MCP في OpenCode';

  @override
  String get e7LibraryMCPUnavailable => 'MCP غير متاح';

  @override
  String get e7LibraryMCPAndIntegrations => 'MCP وعمليات التكامل';

  @override
  String get e7LibraryCouldNotSaveSignInRecovery =>
      'تعذّر حفظ معلومات استعادة تسجيل الدخول.';

  @override
  String get e7LibraryNoProviderConnectionsAvailable =>
      'لا توجد اتصالات متاحة بمزوّدي الخدمة';

  @override
  String get e7LibraryThisServerDidNotReturnAnyProvider =>
      'لم يُرجع هذا الخادم أي تكاملات مع مزوّدي الخدمة.';

  @override
  String get e7LibrarySearchProvidersOrModels =>
      'البحث في مزوّدي الخدمة أو النماذج';

  @override
  String get e7LibraryNoMCPServersConfigured => 'لا توجد خوادم MCP مضبوطة';

  @override
  String get e7LibrarySaveOneForThisProjectOrEvery =>
      'احفظ خادمًا لهذا المشروع أو لجميع المشاريع على الخادم.';

  @override
  String get e7LibraryAddAnMCPServer => 'إضافة خادم MCP';

  @override
  String get e7LibraryAuthorizing => 'جارٍ التفويض';

  @override
  String get e7LibraryResources => 'الموارد';

  @override
  String get e7LibraryNoResourcesAvailable => 'لا توجد موارد متاحة';

  @override
  String get e7LibraryConnectedMCPServersHaveNotExposedAny =>
      'لم تُتِح خوادم MCP المتصلة أي موارد.';

  @override
  String get e7LibraryOpenBrowser => 'فتح المتصفح';

  @override
  String get e7LibraryConnectedAndToolsAreAvailable => 'متصل والأدوات متاحة';

  @override
  String get e7LibraryConnectionFailed => 'فشل الاتصال';

  @override
  String get e7LibraryAuthenticationRequired => 'المصادقة مطلوبة';

  @override
  String get e7LibraryClientRegistrationRequired => 'تسجيل العميل مطلوب';

  @override
  String e7LibraryStoredCredential(String detail1) {
    return 'بيانات اعتماد محفوظة: $detail1';
  }

  @override
  String get e7LibraryNoConnectionMethodsAvailable => 'لا توجد طرق اتصال متاحة';

  @override
  String get e7LibraryConfiguredOnTheServer => 'مُعدّ على الخادم';

  @override
  String e7LibraryDisconnect2(String detail1) {
    return 'قطع اتصال $detail1؟';
  }

  @override
  String e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
    String detail1,
  ) {
    return 'حُذفت بيانات اعتماد $detail1؛ وتبقى بيئة الخادم نشطة';
  }

  @override
  String e7LibraryDisconnected2(String detail1) {
    return 'قُطع اتصال $detail1';
  }

  @override
  String e7LibraryConnect2(String detail1) {
    return 'توصيل $detail1';
  }

  @override
  String get e7LibraryAuthorizationWasNotOpenedThePendingAttempt =>
      'لم تُفتح صفحة التفويض. تبقى المحاولة المعلّقة محفوظة.';

  @override
  String get e7LibraryCouldNotOpenOAuth => 'تعذّر فتح OAuth';

  @override
  String e7LibraryIsConnected(String detail1) {
    return '$detail1 متصل';
  }

  @override
  String get e7LibraryTheSignInSourceChanged => 'تغيّر مصدر تسجيل الدخول.';

  @override
  String get e7LibraryCouldNotConfirmAuthenticationReturnToThe =>
      'تعذّر تأكيد المصادقة. عُد إلى المصدر الأصلي وحاول مجددًا.';

  @override
  String get e7LibraryNoServerCommandsFound => 'لم يُعثر على أوامر للخادم';

  @override
  String get e7LibraryCommandsFromYourProjectAndSkillsAppear =>
      'تظهر هنا الأوامر من مشروعك ومهاراتك.';

  @override
  String get e7LibraryNoDescription => 'لا يوجد وصف';

  @override
  String get e7LibraryServerCommands => 'أوامر الخادم';

  @override
  String get e7LibraryReferences => 'المراجع';

  @override
  String get e7LibraryNoReferencesConfigured => 'لا توجد مراجع مضبوطة';

  @override
  String e7LibraryChangedFilesDetected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'اكتُشف $count ملف متغيّر.',
      many: 'اكتُشف $count ملفًا متغيّرًا.',
      few: 'اكتُشفت $count ملفات متغيرة.',
      two: 'اكتُشف ملفان متغيّران.',
      one: 'اكتُشف ملف متغيّر واحد.',
      zero: 'لم تُكتشف ملفات متغيرة.',
    );
    return '$_temp0';
  }

  @override
  String get e7LibraryEnvironmentRemainsAfterDisconnect =>
      'يستخدم مزوّد الخدمة هذا أيضًا بيئة الخادم، التي لا يستطيع تطبيق الهاتف إزالتها وستبقى نشطة.';

  @override
  String get e7LibrarySearchImportAliases =>
      'backup restore transfer JSON conversation session chat جلسة نسخ احتياطي استعادة نقل محادثة';

  @override
  String get e7LibrarySearchShortcutsAliases =>
      'hotkeys help desktop اختصارات مفاتيح مساعدة حاسوب';

  @override
  String get e7SetupApiKeyHint => 'لصق مفتاح API';

  @override
  String get e7SetupBrowserHint =>
      'يفتح المتصفح. إذا تعذّرت إعادة التوجيه إلى OpenCode، الصق عنوان URL للعودة هنا.';

  @override
  String get e7SetupDeviceCodeHint =>
      'يستخدم رمزًا لمرة واحدة. يعمل من الهاتف.';

  @override
  String get e7SetupAccountHint => 'تسجيل الدخول بحسابك';

  @override
  String get e7SetupNewTerminalDetail => 'ابدأ جلسة طرفية في المشروع الحالي.';

  @override
  String get e7SetupShowPassword => 'إظهار كلمة مرور الخادم';

  @override
  String get e7SetupAuthFailed => 'بدأ تشغيل الخادم، لكن المصادقة فشلت.';

  @override
  String get e7SetupScanInstruction =>
      'وجّه الكاميرا نحو رمز QR الذي يعرضه الأمر opencode2 pair.';

  @override
  String get e7SetupRightKey => 'مفتاح السهم لليمين';

  @override
  String get e7SetupRestartReconnectFailed =>
      'أُعيد تشغيل الخادم المحلي، لكن تعذّر على التطبيق الاتصال مجددًا.';

  @override
  String get e7SetupInstallingOpenCode => 'جارٍ تثبيت OpenCode';

  @override
  String get e7SetupNoOutput => 'لا توجد مخرجات في الطرفية بعد.';

  @override
  String get e7SetupFollowLog => 'متابعة سجل الخادم';

  @override
  String get e7SetupOpenSetupGuide => 'فتح دليل الإعداد';

  @override
  String get e7SetupDownKey => 'مفتاح السهم لأسفل';

  @override
  String get e7SetupVerifyContinue => 'التحقق والمتابعة';

  @override
  String get e7SetupAccessibleTerminal => 'استخدام سجل وإدخال ميسّرين';

  @override
  String get e7SetupCameraFailedDetail =>
      'قد يستخدم تطبيق آخر الكاميرا. يمكنك لصق رمز الاقتران في جميع الأحوال.';

  @override
  String get e7SetupTermuxNoAnswer =>
      'لم يستجب Termux. افتحه، ونفّذ أمر السماح بالتحكم، ثم تحقّق مجددًا.';

  @override
  String get e7SetupCopyOpenTermux => 'النسخ وفتح Termux';

  @override
  String get e7SetupStopTerminalDetail =>
      'ستتوقف العملية الجارية والعمليات التابعة لها.';

  @override
  String get e7SetupEdit => 'تعديل';

  @override
  String get e7SetupServerPassword => 'كلمة مرور الخادم';

  @override
  String get e7SetupHttpsHint =>
      'يُفضّل HTTPS. يتطلب HTTP البعيد إذنك لكل خادم.';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      'الخادم المحلي جاهز، لكن تعذّر حفظ الإصدار الذي رصده التطبيق. حدّث الإعداد للمحاولة مجددًا.';

  @override
  String get e7SetupPasteInstead => 'لصق الرمز بدلًا من مسحه';

  @override
  String get e7SetupConfirmUpdate => 'تحديث OpenCode الذي يديره التطبيق؟';

  @override
  String get e7SetupInstallServiceDetail =>
      'أداة التثبيت الرسمية مع خدمة مستخدم systemd تستمر بعد إغلاق الطرفية وإعادة التشغيل.';

  @override
  String get e7SetupUpdateHost => 'تحديث OpenCode على الكمبيوتر المضيف';

  @override
  String get e7SetupKeepAfterLogout => 'متابعة التشغيل بعد تسجيل الخروج';

  @override
  String get e7SetupGetTermux => 'الحصول على Termux';

  @override
  String get e7SetupRestartServer => 'إعادة تشغيل الخادم';

  @override
  String get e7SetupResumeSetup =>
      'المحاولة مجددًا — يستأنف الإعداد من حيث توقف';

  @override
  String get e7SetupHidePassword => 'إخفاء كلمة مرور الخادم';

  @override
  String get e7SetupControlKeys =>
      'مفاتيح التحكم في الطرفية. اسحب أفقيًا لعرض المزيد.';

  @override
  String get e7SetupEndInputKey => 'نهاية الإدخال، Control D';

  @override
  String get e7SetupGuidanceSaveFailed =>
      'تعذّر حفظ إرشادات الاتصال. حاول الحفظ مجددًا.';

  @override
  String get e7SetupServerOperation => 'جارٍ تنفيذ إجراء على الخادم';

  @override
  String get e7SetupNewTerminal => 'طرفية جديدة';

  @override
  String get e7SetupUnsavedProfile => 'لم يُحفظ الخادم.';

  @override
  String get e7SetupCheckingInstall => 'جارٍ التحقق من التثبيت الحالي…';

  @override
  String get e7SetupInspectTermuxFailed =>
      'تعذّر على Android التحقق من Termux.';

  @override
  String get e7SetupUsername => 'اسم المستخدم (اختياري)';

  @override
  String get e7SetupFirstSetupDuration =>
      'قد يستغرق الإعداد الأول من 10 إلى 15 دقيقة. يمكنك مغادرة هذه الشاشة والعودة إليها؛ سيستمر الإعداد.';

  @override
  String get e7SetupNoTerminals => 'لا توجد عمليات في الطرفية';

  @override
  String get e7SetupEscapeKey => 'مفتاح Escape';

  @override
  String get e7SetupLeftKey => 'مفتاح السهم لليسار';

  @override
  String get e7SetupInstallService => 'تثبيت OpenCode كخدمة في الخلفية';

  @override
  String get e7SetupSaveToFinish => 'تم الاتصال — احفظ لإكمال الإعداد.';

  @override
  String get e7SetupPasswordStartupHint =>
      'تظهر عند تشغيل الخادم. اتركها فارغة إن لم تكن له كلمة مرور.';

  @override
  String get e7SetupInstallTermuxDetail =>
      'ثبّت الإصدار الحالي من Termux عبر F-Droid، ثم عُد إلى هنا.';

  @override
  String get e7SetupStopBeforeUpdate =>
      'أوقف التوليد الجاري قبل تحديث OpenCode.';

  @override
  String get e7SetupRestartingLocal => 'جارٍ إعادة تشغيل الخادم المحلي';

  @override
  String get e7SetupAddServer => 'إضافة خادم';

  @override
  String get e7SetupInteractiveTerminal => 'استخدام الطرفية التفاعلية';

  @override
  String get e7SetupInstallingUbuntu => 'جارٍ إعداد Ubuntu';

  @override
  String get e7SetupInterruptKey => 'مقاطعة، Control C';

  @override
  String get e7SetupServerUrl => 'عنوان URL للخادم';

  @override
  String get e7SetupUsbAccess => 'الاتصال من هذا الهاتف عبر USB';

  @override
  String get e7SetupWaitingTermux =>
      'في انتظار استجابة Termux. قد يستغرق ذلك بعض الوقت.';

  @override
  String get e7SetupDiscardChanges => 'تجاهل تعديلات الخادم؟';

  @override
  String get e7SetupPasswordRequired => 'يلزم إدخال كلمة المرور مجددًا';

  @override
  String get e7SetupPairing => 'جارٍ الاقتران…';

  @override
  String get e7SetupCommandInput => 'إدخال أمر للطرفية';

  @override
  String get e7SetupCameraFailed => 'تعذّر فتح الكاميرا';

  @override
  String get e7SetupPastePassword => 'لصق كلمة مرور الخادم';

  @override
  String get e7SetupSavingLocal => 'جارٍ حفظ إعدادات الخادم المحلي';

  @override
  String get e7SetupCopyFailureReport => 'نسخ تقرير الخطأ';

  @override
  String get e7SetupCameraDisabled => 'الوصول إلى الكاميرا معطّل';

  @override
  String get e7SetupPastePairing => 'لصق رمز الاقتران';

  @override
  String get e7SetupServers => 'الخوادم';

  @override
  String get e7SetupStartingSetup => 'جارٍ بدء الإعداد في Termux';

  @override
  String get e7SetupSetupNotStarted =>
      'فُتح Termux لكن الإعداد لم يبدأ. حاول مجددًا؛ وإذا تكررت المشكلة، انسخ تقرير الخطأ.';

  @override
  String get e7SetupThisDevice => 'هذا الجهاز (Termux)';

  @override
  String get e7SetupTransportReconnecting => 'جارٍ إعادة الاتصال بالخادم.';

  @override
  String get e7SetupStepUnavailable => 'غير متاح بعد';

  @override
  String get e7SetupUpdateHostDetail =>
      'عندما يعلن الخادم عن تحديث، تتيح الإعدادات تحديثه مباشرةً. هذا الأمر ينفّذ التحديث نفسه على الكمبيوتر المضيف.';

  @override
  String get e7SetupMissingPasswordShort =>
      'كلمة المرور المحفوظة غير متاحة. أدخلها مجددًا، أو اتركها فارغة فقط إذا لم يعد الخادم يتطلبها.';

  @override
  String get e7SetupTesting => 'جارٍ الاختبار…';

  @override
  String get e7SetupScanPairing => 'مسح رمز الاقتران';

  @override
  String get e7SetupRestartUnconfirmed =>
      'تعذّر تأكيد إعادة التشغيل. حدّث حالة التقدّم قبل المحاولة مجددًا.';

  @override
  String get e7SetupExistingMissingCredential =>
      'يوجد خادم محلي، لكن بيانات دخوله المحفوظة غير متاحة. أعد الإعداد لاستبدالها بأمان.';

  @override
  String get e7SetupPreparingModels => 'جارٍ تجهيز النماذج';

  @override
  String get e7SetupHostInstructions =>
      'تُشغّل هذه الأوامر على الكمبيوتر الذي يستضيف الخادم؛ لا يستطيع التطبيق تنفيذها نيابةً عنك. انسخ كل أمر إلى الطرفية على ذلك الكمبيوتر.';

  @override
  String get e7SetupTranscript => 'سجل الطرفية';

  @override
  String get e7SetupHostFirstSetup => 'الإعداد لأول مرة — نفّذ على الكمبيوتر';

  @override
  String get e7SetupNoCameraDetail =>
      'شغّل الأمر opencode2 pair على الخادم، ثم انسخ الرمز الذي يعرضه والصقه في محرر الخادم.';

  @override
  String get e7SetupDiscard => 'تجاهل';

  @override
  String get e7SetupConnectionClosed => 'أُغلق الاتصال';

  @override
  String get e7SetupUpKey => 'مفتاح السهم لأعلى';

  @override
  String get e7SetupConnect => 'اتصال';

  @override
  String get e7SetupRemoveTerminalDetail => 'ستتم إزالة سجل هذه الطرفية.';

  @override
  String get e7SetupInputDisconnected =>
      'الإدخال غير متاح أثناء انقطاع الاتصال.';

  @override
  String get e7SetupHostCopied =>
      'تم النسخ. شغّل الأمر على الكمبيوتر الذي يستضيف الخادم.';

  @override
  String get e7SetupCameraPrivacy =>
      'تُستخدم الكاميرا فقط لقراءة رمز QR الذي يعرضه الأمر opencode2 pair، أثناء فتح هذه الشاشة. يمكنك لصق الرمز بدلًا من مسحه للحصول على النتيجة نفسها.';

  @override
  String get e7SetupIsV2 => 'هذا خادم OpenCode 2.';

  @override
  String get e7SetupResumeLive => 'استئناف المتابعة المباشرة';

  @override
  String get e7SetupTabKey => 'مفتاح Tab';

  @override
  String get e7SetupCloseScanner => 'إغلاق الماسح';

  @override
  String get e7SetupChooseContinue => 'اختر طريقة المتابعة';

  @override
  String get e7SetupTermuxOutdated =>
      'إصدار Termux هذا قديم ولا يستطيع التطبيق التحكم فيه. ثبّت الإصدار الحالي من F-Droid أو GitHub، ثم تحقّق مجددًا.';

  @override
  String get e7SetupCameraNeeded => 'يلزم السماح بالكاميرا لمسح الرمز';

  @override
  String get e7SetupTerminalActions => 'إجراءات الطرفية';

  @override
  String get e7SetupReadPassword => 'عرض كلمة مرور الخادم لهذا التطبيق';

  @override
  String get e7SetupStartInstalled => 'تشغيل OpenCode المثبّت؟';

  @override
  String get e7SetupTestConnection => 'اختبار الاتصال';

  @override
  String get e7SetupCheckingTermux => 'جارٍ التحقق من الاتصال بـ Termux';

  @override
  String get e7SetupCheckingTermuxShort => 'جارٍ التحقق من Termux…';

  @override
  String get e7SetupDefaultServer => 'خادم OpenCode';

  @override
  String get e7SetupSaving => 'جارٍ الحفظ…';

  @override
  String get e7SetupLinuxService => 'إعداد OpenCode 2';

  @override
  String get e7SetupPairingDesktopHint =>
      'تحقّق من تشغيل الخادم وإمكانية وصول هذا الجهاز إلى العنوان الذي عرضه.';

  @override
  String get e7SetupAboutNotices => 'حول التطبيق وإشعارات المصدر المفتوح';

  @override
  String get e7SetupSetupLost => 'تعذّرت متابعة الإعداد الجاري في Termux';

  @override
  String get e7SetupReportCopied => 'تم نسخ تقرير الخطأ.';

  @override
  String get e7SetupAppSettings => 'إعدادات التطبيق';

  @override
  String get e7SetupNoCamera => 'هذا الجهاز لا يحتوي على كاميرا';

  @override
  String get e7SetupServerDisconnected => 'الخادم غير متصل.';

  @override
  String get e7SetupEmptyPasswordHint =>
      'اتركها فارغة فقط إذا لم يعد هذا الخادم يستخدم كلمة مرور.';

  @override
  String get e7SetupAndroidOnly =>
      'الإعداد على هذا الهاتف متاح على Android فقط';

  @override
  String get e7SetupEditServer => 'تعديل الخادم';

  @override
  String get e7SetupReenterPassword => 'إدخال كلمة المرور مجددًا';

  @override
  String get e7SetupMissingCredential =>
      'بيانات الدخول المحفوظة لهذا الخادم غير متاحة. أعد الإعداد لاستبدالها بأمان.';

  @override
  String get e7SetupReconnect => 'إعادة الاتصال';

  @override
  String get e7SetupSwitchNotStarted => 'لم يبدأ تبديل الإصدار.';

  @override
  String get e7SetupNoUbuntu => 'لا يوجد تثبيت Ubuntu يديره التطبيق.';

  @override
  String get e7SetupKeyUnavailable => 'غير متاح عندما تكون الطرفية غير متصلة';

  @override
  String get e7SetupCopyTerminal => 'نسخ النص المحدد أو سجل الطرفية';

  @override
  String get e7SetupCommandHint => 'اكتب أمرًا';

  @override
  String get e7SetupCheckInstallFailed => 'تعذّر التحقق من التثبيت الحالي.';

  @override
  String get e7SetupConnectionFailed => 'فشل الاتصال.';

  @override
  String get e7SetupOpenAppSettings => 'فتح إعدادات التطبيق';

  @override
  String get e7SetupFullWalkthrough => 'الدليل الكامل (يفتح في المتصفح)';

  @override
  String get e7SetupPairingPhoneHint =>
      'لا يمكن لهذا الهاتف الوصول إلى خادم يستمع على 127.0.0.1 فقط دون توجيه الاتصال: استخدم «adb reverse tcp:PORT tcp:PORT» عبر USB أو نفق SSH. وللوصول عبر الشبكة، استخدم HTTPS.';

  @override
  String get e7SetupOutputCopied => 'تم نسخ مخرجات الإعداد.';

  @override
  String get e7SetupMissingPasswordLong =>
      'كلمة المرور المحفوظة غير متاحة. أدخلها مجددًا، أو اتركها فارغة فقط إذا لم يعد هذا الخادم يتطلب كلمة مرور.';

  @override
  String get e7SetupNoServerGuide =>
      'لا يوجد خادم بعد؟ يوضّح دليل الإعداد كيفية تشغيله.';

  @override
  String get e7SetupStartingLocal => 'جارٍ تشغيل الخادم المحلي';

  @override
  String get e7SetupTerminalSemantics =>
      'طرفية تفاعلية. استخدم زر تسهيل الاستخدام لعرض سجل مقروء وحقل إدخال واضح.';

  @override
  String get e7SetupRestartingLocalStage => 'جارٍ إعادة تشغيل الخادم المحلي';

  @override
  String get e7SetupEmptyPairClipboard =>
      'الحافظة فارغة. شغّل «opencode2 pair» على الخادم وانسخ الرمز الذي يعرضه.';

  @override
  String get e7SetupRestartActiveChanged =>
      'أُعيد تشغيل الخادم المحلي، لكن الخادم النشط تغيّر. أعد الاتصال عندما تكون مستعدًا.';

  @override
  String get e7SetupTokenRequired => 'يلزم إدخال رمز الاتصال مجددًا';

  @override
  String get e7SetupPairingInstructions =>
      'شغّل «opencode2 pair» على حاسوبك، ثم الصق الرمز الذي يعرضه أو امسحه بالكاميرا.';

  @override
  String get e7SetupHostDaily => 'الاستخدام اليومي — نفّذ على الكمبيوتر';

  @override
  String get e7SetupStopLocal => 'إيقاف الخادم المحلي';

  @override
  String get e7SetupReadingProgress => 'جارٍ قراءة تقدّم الإعداد';

  @override
  String get e7SetupCameraSettingsDetail =>
      'لن يطلب Android الإذن مجددًا. فعّل الكاميرا من إعدادات التطبيق ثم عُد إلى هنا. يمكنك لصق الرمز الآن دون أي إذن.';

  @override
  String get e7SetupVerifyTermuxFailed => 'تعذّر التحقق من الاتصال بـ Termux.';

  @override
  String get e7SetupUbuntuOnly => 'Ubuntu مثبّت، لكن OpenCode لم يُثبّت بعد.';

  @override
  String get e7SetupRenameTerminal => 'إعادة تسمية الطرفية';

  @override
  String get e7SetupInputUnavailable => 'إدخال الطرفية غير متاح';

  @override
  String get e7SetupUpdateInterruption =>
      'سيتوقف الخادم لفترة قصيرة. أوقف التوليد الجاري أولًا.';

  @override
  String get e7SetupRunningOnPhone => 'يعمل OpenCode على هذا الهاتف.';

  @override
  String get e7SetupLocalStopped =>
      'الخادم المحلي متوقف. ملفاته المثبتة محفوظة.';

  @override
  String get e7SetupDidNotConnect => 'لم يتصل الخادم.';

  @override
  String get e7SetupSendCommand => 'إرسال الأمر إلى الطرفية';

  @override
  String get e7SetupUnsupportedSetup =>
      'يتطلب الإعداد على الجهاز تطبيق Termux على Android. على هذا الكمبيوتر، شغّل `opencode serve` ثم أضفه كخادم.';

  @override
  String get e7SetupSendKey => 'يرسل هذا المفتاح إلى الطرفية';

  @override
  String get e7SetupRemoveTerminal => 'إزالة الطرفية؟';

  @override
  String e7SetupTerminalNumber(int number) {
    return 'الطرفية $number';
  }

  @override
  String e7SetupCopyCommandLabel(String label) {
    return 'نسخ الأمر: $label';
  }

  @override
  String e7SetupConnectFailedDetail(String name, String detail) {
    return 'تعذّر الاتصال بـ $name. $detail تحقّق من عنوان الخادم وبيانات الدخول، ثم حاول مجددًا.';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return 'حُفظ $name، لكن تعذّر الاتصال به. تحقّق من عنوان الخادم وبيانات الدخول، ثم حاول مجددًا. ($detail)';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return 'تعذّر حفظ $name. لم يتغيّر الخادم الحالي. تحقّق من مساحة تخزين الجهاز وحاول مجددًا. ($detail)';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return 'إزالة $name؟';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return 'أُزيل $name، لكن تعذّر إغلاق اتصاله بالكامل. أعد تشغيل التطبيق قبل الاتصال بخادم آخر. ($detail)';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return 'تعذّرت إزالة $name. بقي الخادم المحفوظ والاتصال الحالي. تحقّق من مساحة تخزين الجهاز وحاول مجددًا. ($detail)';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return 'تم الاقتران بـ $host — اختير من بين $count عناوين في الرمز.';
  }

  @override
  String e7SetupPaired(String host) {
    return 'تم الاقتران بـ $host.';
  }

  @override
  String e7SetupStartFailed(String detail) {
    return 'تعذّر حفظ الإعداد المحلي أو بدؤه: $detail';
  }

  @override
  String e7SetupRestartFailed(String detail) {
    return 'تعذّرت إعادة تشغيل الخادم المحلي: $detail';
  }

  @override
  String e7SetupStopFailed(String detail) {
    return 'تعذّر إيقاف الخادم المحلي: $detail';
  }

  @override
  String e7SetupElapsedSeconds(int seconds) {
    return 'انقضت $seconds ث';
  }

  @override
  String e7SetupElapsedMinutes(int minutes, int seconds) {
    return 'انقضت $minutes د و$seconds ث';
  }

  @override
  String e7SetupDeleteDisclosure(int queued, int drafts) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: 'سيُحذف $queued طلب من قائمة الانتظار.',
      many: 'سيُحذف $queued طلبًا من قائمة الانتظار.',
      few: 'ستُحذف $queued طلبات من قائمة الانتظار.',
      two: 'سيُحذف طلبان من قائمة الانتظار.',
      one: 'سيُحذف طلب واحد من قائمة الانتظار.',
      zero: '',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: 'ستُحذف $drafts مسودة غير مرسلة.',
      many: 'ستُحذف $drafts مسودة غير مرسلة.',
      few: 'ستُحذف $drafts مسودات غير مرسلة.',
      two: 'ستُحذف مسودتان غير مرسلتين.',
      one: 'ستُحذف مسودة واحدة غير مرسلة.',
      zero: '',
    );
    return 'يحذف هذا كل ما حفظه الجهاز لهذا الخادم: كلمة المرور، والنموذج والوكيل المحددين، والمشروع المختار، والمحادثات المعروضة في أداة الشاشة الرئيسية.\n\n$_temp0 $_temp1\n\nلن يُحذف أي شيء على الخادم نفسه أو لدى مزوّدي الذكاء الاصطناعي.';
  }

  @override
  String e7SetupPairingFailed(String detail, String hint) {
    return 'لم يستجب أي عنوان في رمز الاقتران:\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String get e7SetupPairNone =>
      'لا يوجد رمز اقتران هنا. شغّل «opencode2 pair» على الخادم وامسح الرمز الذي يعرضه أو انسخه.';

  @override
  String get e7SetupPairLong =>
      'هذا النص أطول من رمز اقتران. انسخ فقط السطر الذي يعرضه «opencode2 pair» أو امسح رمز QR الخاص به.';

  @override
  String get e7SetupPairInvalid =>
      'هذا ليس رمز اقتران. شغّل «opencode2 pair» على الخادم وامسح الرمز الذي يعرضه أو انسخه.';

  @override
  String get e7SetupPairShape =>
      'بنية رمز الاقتران غير صحيحة؛ يجب أن يكون كائن JSON يحتوي على «urls» و«username» و«password».';

  @override
  String get e7SetupPairNoUrls =>
      'رمز الاقتران لا يحتوي على حقل «urls»، لذا لا يوجد عنوان للاتصال به.';

  @override
  String get e7SetupPairUrlsType =>
      'حقل «urls» في رمز الاقتران ليس قائمة عناوين.';

  @override
  String get e7SetupPairTooMany =>
      'يحتوي رمز الاقتران على عناوين أكثر مما يحاول التطبيق الاتصال به. اربط الخادم بواجهة شبكة واحدة وأعد الاقتران.';

  @override
  String get e7SetupPairAddressType => 'يحتوي رمز الاقتران على عنوان ليس نصًا.';

  @override
  String get e7SetupPairAddressLong =>
      'يحتوي رمز الاقتران على عنوان أطول من عنوان URL مقبول للخادم.';

  @override
  String get e7SetupPairAddressMissing =>
      'رمز الاقتران لا يحتوي على عنوان خادم. تحقّق من أن الخادم يستقبل الاتصالات، ثم شغّل «opencode2 pair» مجددًا.';

  @override
  String get e7SetupPairUsernameType =>
      'حقل «username» في رمز الاقتران ليس نصًا.';

  @override
  String get e7SetupPairPasswordMissing =>
      'رمز الاقتران لا يحتوي على حقل «password». ربما لم يُنسخ كاملًا؛ امسح الرمز أو انسخه بالكامل.';

  @override
  String get e7SetupPairPasswordType =>
      'حقل «password» في رمز الاقتران ليس نصًا.';

  @override
  String get e7SetupPairTestFailed =>
      'فشل اختبار الاتصال قبل التحقق من الخادم. جرّب عنوانًا آخر.';

  @override
  String get e7SetupNotOpenCode =>
      'لم يستجب العنوان كخادم OpenCode. تحقّق من العنوان وحاول مجددًا.';

  @override
  String get e7SetupNoServerAnswer =>
      'لم يستجب الخادم. تحقّق من تشغيل opencode serve على ذلك العنوان.';

  @override
  String get e7SetupPairPasswordRejected =>
      'رُفضت كلمة المرور. تحقّق من رمز الاقتران وحاول مجددًا.';

  @override
  String get e7SetupPairAddressUnusable =>
      'يحتوي رمز الاقتران على عنوان خادم غير صالح للاستخدام.';

  @override
  String get e7SetupInvalidAddress => '<عنوان غير صالح>';

  @override
  String get e7SetupEnterUrl => 'أدخل عنوان URL للخادم.';

  @override
  String get e7SetupIncludeScheme =>
      'أضف https:// أو http://. يتطلب HTTP البعيد إذنك.';

  @override
  String get e7SetupCompleteUrl =>
      'أدخل عنوان URL كاملًا للخادم، مثل https://server.example:4096.';

  @override
  String get e7SetupUrlScheme =>
      'يجب أن تبدأ عناوين الخادم بـ https:// أو http://.';

  @override
  String get e7SetupTermuxUrlScheme =>
      'يجب أن تستخدم عناوين الخوادم https://، أو http:// لـ Termux المحلي.';

  @override
  String get e7SetupUrlCredentials =>
      'لا تضع بيانات الدخول في عنوان URL. استخدم الحقول أدناه.';

  @override
  String get e7SetupUrlQuery =>
      'أزل معاملات الاستعلام والجزء الذي يبدأ بـ # من عنوان URL للخادم.';

  @override
  String get e7SetupUrlPath =>
      'أزل المسار من عنوان URL للخادم. أدخل البروتوكول والمضيف والمنفذ فقط.';

  @override
  String get e7SetupRequireHttps =>
      'يلزم HTTPS خارج هذا الجهاز. يجب عدم إرسال بيانات المصادقة الأساسية عبر HTTP.';

  @override
  String get e7SetupLocalHttp =>
      'يُسمح بـ HTTP فقط مع هذا الجهاز أو عنوان شبكة خاصة مثل 192.168.x.x. للخوادم الأخرى استخدم HTTPS أو Tailscale.';

  @override
  String get e7SetupRefused =>
      'رُفض الاتصال. هل يعمل opencode serve على ذلك المضيف والمنفذ؟';

  @override
  String get e7SetupTimeout =>
      'انتهت مهلة الاتصال. تحقّق من العنوان وإمكانية الوصول إلى الخادم من هذا الهاتف.';

  @override
  String get e7SetupDns =>
      'تعذّر العثور على اسم المضيف. تحقّق من كتابة العنوان.';

  @override
  String get e7SetupCertificate =>
      'الشهادة غير موثوقة. تحقق من بصمتها قبل أن تقرر الوثوق بها.';

  @override
  String get e7SetupUnhealthy =>
      'استجاب الخادم لكنه أبلغ عن مشكلة في حالته. راجع سجلاته ثم حاول مجددًا.';

  @override
  String get e7SetupServerStarting =>
      'الخادم قيد التشغيل. حاول مجددًا بعد قليل.';

  @override
  String get e7SetupPasswordNeeded => 'يتطلب هذا الخادم كلمة مرور التشغيل.';

  @override
  String get e7SetupPasswordRejected =>
      'رُفضت كلمة المرور. انسخ سطر «server password» الحالي من مخرجات الخادم؛ يتغيّر عند كل إعادة تشغيل ما لم يُضبط OPENCODE_PASSWORD.';

  @override
  String get e7SetupCredentialsRefused =>
      'رفض الخادم بيانات الدخول. تحقّق من اسم المستخدم وكلمة المرور.';

  @override
  String get e7SetupCodexUrl => 'أدخل عنوان URL لخادم Codex.';

  @override
  String get e7SetupCodexCompleteUrl => 'أدخل عنوان URL كاملًا لخادم Codex.';

  @override
  String get e7SetupCodexScheme =>
      'يجب أن تستخدم عناوين خادم Codex البروتوكول wss://، أو ws:// للخادم المحلي.';

  @override
  String get e7SetupCodexCredentials =>
      'لا تضع بيانات الدخول في عنوان URL لـ Codex.';

  @override
  String get e7SetupCodexQuery =>
      'أزل معاملات الاستعلام والجزء الذي يبدأ بـ # من عنوان URL لـ Codex.';

  @override
  String get e7SetupCodexPath => 'أزل المسار من عنوان URL لخادم Codex.';

  @override
  String get e7SetupCodexPlain =>
      'يُسمح باتصال WebSocket غير المشفّر لخادم Codex المحلي فقط.';

  @override
  String get e7SetupCodexDirectory => 'أدخل مسارًا مطلقًا لمجلد مشروع Codex.';

  @override
  String get e7SetupCodexToken => 'أدخل رمز اتصال صالحًا لـ Codex.';

  @override
  String get e7SetupRefreshPackages => 'جارٍ تحديث قائمة حزم Termux';

  @override
  String get e7SetupRepairPackages => 'جارٍ إصلاح حزم Termux';

  @override
  String get e7SetupInstallDependencies => 'جارٍ تثبيت متطلبات Termux';

  @override
  String get e7SetupPrepareTermux => 'جارٍ تجهيز Termux';

  @override
  String get e7SetupInstallUbuntu => 'جارٍ تثبيت Ubuntu';

  @override
  String get e7SetupRefreshModels => 'جارٍ تحديث قائمة نماذج OpenCode';

  @override
  String get e7SetupStartLocalServer => 'جارٍ تشغيل الخادم المحلي';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode جاهز';

  @override
  String get e7SetupPrepareRuntime => 'جارٍ تجهيز إصدار OpenCode المحدد';

  @override
  String get e7SetupSwitchLocal =>
      'جارٍ تبديل الخادم المحلي الذي يديره التطبيق';

  @override
  String get e7SetupCheckRestart =>
      'جارٍ التحقق من الخادم المحلي قبل إعادة تشغيله';

  @override
  String get e7SetupStoppingLocal => 'جارٍ إيقاف الخادم المحلي';

  @override
  String get e7SetupStoppedLocal => 'توقف الخادم المحلي';

  @override
  String get e7SetupUnknownSetup => 'حالة الإعداد غير معروفة';

  @override
  String get e7SetupUnexpectedStop =>
      'توقف خادم OpenCode المحلي بشكل غير متوقع';

  @override
  String get e7SetupSetupInterrupted =>
      'توقف الإعداد بشكل غير متوقع؛ راجع المخرجات المباشرة للتفاصيل';

  @override
  String get e7SetupRecoveryDisabled => 'الاستعادة التلقائية معطّلة';

  @override
  String get e7SetupRecoveryWasDisabled => 'عُطّلت الاستعادة التلقائية';

  @override
  String get e7SetupPortBusy =>
      'منفذ الخادم المحلي ما زال مستخدمًا؛ لم يُشغّل خادم بديل';

  @override
  String get e7SetupCredentialMismatch =>
      'تختلف بيانات الدخول المحفوظة عن بيانات هذا الإصدار؛ استعد بياناته الأصلية المحفوظة قبل العودة';

  @override
  String get e7SetupUbuntuUnavailable =>
      'تثبيت Ubuntu الذي يديره التطبيق غير متاح';

  @override
  String get e7SetupRuntimeUnavailable => 'أمر OpenCode المحدد غير متاح';

  @override
  String get e7SetupIdentityMismatch =>
      'العملية المتعقبة ليست خادم OpenCode الذي يديره التطبيق';

  @override
  String get e7SetupPasswordMissing => 'كلمة مرور الخادم المحلي مفقودة';

  @override
  String get e7SetupVersionMissing => 'ثُبّت OpenCode لكنه لم يُبلغ عن إصداره';

  @override
  String get e7SetupModelsRefreshFailed =>
      'حُدّث OpenCode، لكن تعذّر تحديث قائمة نماذجه';

  @override
  String get e7SetupStartupExited => 'توقف خادم OpenCode أثناء بدء التشغيل';

  @override
  String get e7SetupReadinessTimeout =>
      'لم يصبح خادم OpenCode جاهزًا مع مصادقة ناجحة خلال 30 ثانية';

  @override
  String get e7SetupUnreadableData => 'تعذّرت قراءة سجل موقع بيانات OpenCode 2';

  @override
  String get e7SetupUnreadablePrevious => 'تعذّرت قراءة سجل الإصدار السابق';

  @override
  String get e7SetupReadManagerFailed => 'تعذّرت قراءة حالة مدير الإعداد';

  @override
  String get e7SetupMissingManager => 'مدير الإعداد غير موجود';

  @override
  String get e7SetupRemoveInterruptedFailed =>
      'تعذّرت إزالة تثبيت Ubuntu غير المكتمل الذي يملكه التطبيق';

  @override
  String get e7SetupCheckStorageFailed =>
      'تعذّر التحقق من مساحة التخزين المتاحة قبل الإعداد';

  @override
  String get e7SetupReadStorageFailed =>
      'تعذّرت قراءة مساحة التخزين المتاحة قبل الإعداد';

  @override
  String get e7SetupRepositoryFailed => 'تعذّر اختيار مستودع حزم Termux الرسمي';

  @override
  String get e7SetupRepositoryRefreshFailed =>
      'تعذّر تحديث packages.termux.dev؛ تحقّق من الشبكة وحاول مجددًا';

  @override
  String get e7SetupRepairFailed => 'تعذّر إصلاح عملية حزم Termux غير المكتملة';

  @override
  String get e7SetupUpgradeFailed => 'تعذّر إكمال الترقية الآمنة لحزم Termux';

  @override
  String get e7SetupDependenciesFailed => 'تعذّر تثبيت متطلبات Termux';

  @override
  String get e7SetupDependenciesUnusable =>
      'متطلبات Termux ما زالت غير صالحة للاستخدام بعد إصلاح الحزم';

  @override
  String get e7SetupUbuntuUnusable =>
      'تثبيت Ubuntu الحالي غير صالح للاستخدام؛ لن يحذفه الإعداد';

  @override
  String get e7SetupExtractionFailed =>
      'لم ينتج عن فك ضغط Ubuntu Base تثبيت صالح للاستخدام';

  @override
  String get e7SetupLockFailed => 'تعذّر على مدير الإعداد حجز عملية تشغيله';

  @override
  String get e7SetupSetupGroupFailed =>
      'لم يبدأ مدير الإعداد ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupSwitchGroupFailed =>
      'لم يبدأ مدير التبديل ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupServerGroupFailed =>
      'لم يبدأ الخادم المُدار ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupRecordIdentityFailed =>
      'تعذّر تسجيل هوية عملية الخادم المُدار';

  @override
  String e7SetupConnectingProfile(String name) {
    return 'جارٍ الاتصال بـ $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return 'جارٍ الاتصال مجددًا (المحاولة $attempt)';
  }

  @override
  String get e7SetupOpeningWorkspace => 'جارٍ فتح المشروع المحفوظ.';

  @override
  String get e7SetupWhatToCheck => 'ما يجب التحقق منه';

  @override
  String get e7SetupUpdatePassword => 'تحديث كلمة المرور';

  @override
  String e7SetupLastSetupDetail(String detail) {
    return 'آخر مخرجات الإعداد: $detail';
  }

  @override
  String e7SetupBridgeDetail(String detail) {
    return 'تفاصيل الاتصال بـ Termux: $detail';
  }

  @override
  String e7SetupDiagnosticsUnavailable(String detail) {
    return 'التشخيص غير متاح: $detail';
  }

  @override
  String e7SetupProbeHttp(String status) {
    return 'استجاب العنوان، لكن ليس كخادم OpenCode (HTTP $status). تحقّق من أن عنوان URL يشير إلى opencode serve.';
  }

  @override
  String e7SetupProbeError(String detail) {
    return 'فشل اختبار الاتصال: $detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'توقف خادم OpenCode (الرمز $code)';
  }

  @override
  String get e7SetupCheckTermux => 'على هذا الهاتف';

  @override
  String get e7SetupCommandFailed => 'فشل تنفيذ الأمر في Termux.';

  @override
  String get e7SetupUnexpectedBridge =>
      'أعاد Termux استجابة غير متوقعة للتحقق من الاتصال.';

  @override
  String get e7SetupSetupQueued => 'الإعداد في قائمة الانتظار';

  @override
  String get e7SetupNoSetup => 'لم يبدأ أي إعداد بعد';

  @override
  String get e7SetupManagerMissingAfterLaunch =>
      'مدير الإعداد غير موجود بعد التشغيل';

  @override
  String get e7SetupBootstrapCleared => 'مُسحت حالة الإعداد الأولي';

  @override
  String get e7SetupInstallingBeta => 'جارٍ تثبيت OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => 'فشلت المصادقة';

  @override
  String get e7SetupUnknownVersion => 'إصدار غير معروف';

  @override
  String get e7ModelUiClose => 'إغلاق اختيار النموذج';

  @override
  String get e7ModelUiClearSearch => 'مسح البحث عن النماذج';

  @override
  String get e7ModelUiLoadFailed => 'تعذّر تحميل النماذج';

  @override
  String get e7ModelUiRetry => 'إعادة المحاولة';

  @override
  String get e7ModelUiBasicCatalog =>
      'أرسل هذا الخادم قائمة أساسية بالنماذج. تفاصيل الإمكانات وسعة السياق غير متاحة.';

  @override
  String get e7ModelUiEditFilters => 'تعديل مرشّحات النماذج';

  @override
  String get e7ModelUiFilterModels => 'تصفية النماذج';

  @override
  String get e7ModelUiFiltered => 'تمت التصفية';

  @override
  String get e7ModelUiFilters => 'المرشّحات';

  @override
  String get e7ModelUiRefresh => 'تحديث النماذج';

  @override
  String get e7ModelUiAnyCapability => 'كل الإمكانات';

  @override
  String get e7ModelUiFastModes => 'الأوضاع السريعة';

  @override
  String get e7ModelUiReasoning => 'الاستدلال';

  @override
  String get e7ModelUiLargestContext => 'أكبر سعة سياق';

  @override
  String get e7ModelUiNoneAvailable => 'لا توجد نماذج متاحة';

  @override
  String get e7ModelUiFavoritesEmpty => 'احتفظ بنماذجك المفضّلة هنا';

  @override
  String get e7ModelUiRecentEmpty => 'اختيارك التالي يبدأ هنا';

  @override
  String get e7ModelUiNoMatches => 'لا توجد نماذج مطابقة';

  @override
  String get e7ModelUiConfigureProvider =>
      'أعِدّ مزوّد خدمة على خادم OpenCode، ثم حدّث القائمة.';

  @override
  String get e7ModelUiFavoritesHint =>
      'اضغط على النجمة بجانب أي نموذج لتجده هنا.';

  @override
  String get e7ModelUiRecentHint =>
      'ستظهر النماذج التي تستخدمها هنا، بدءًا بالأحدث.';

  @override
  String get e7ModelUiNoFastModes =>
      'لا يعلن أي نموذج عن وضع سريع أو وضع ذي جهد استدلال منخفض.';

  @override
  String get e7ModelUiNoMatchesHint =>
      'جرّب بحثًا آخر أو مزوّدًا أو مرشّح إمكانات مختلفًا.';

  @override
  String get e7ModelUiClearFilters => 'مسح المرشّحات';

  @override
  String get e7ModelUiBrowseAll => 'تصفّح كل النماذج';

  @override
  String get e7ModelUiAgent => 'الوكيل';

  @override
  String get e7ModelUiServerDefault => 'إعداد الخادم الافتراضي';

  @override
  String get e7ModelUiProvider => 'مزوّد الخدمة';

  @override
  String get e7ModelUiAllProviders => 'كل مزوّدي الخدمة';

  @override
  String get e7ModelUiCurrent => 'النموذج الحالي';

  @override
  String get e7ModelUiUnavailable => 'غير متاح';

  @override
  String get e7ModelUiDeprecated => 'متقادم';

  @override
  String get e7ModelUiPreview => 'تجريبي';

  @override
  String get e7ModelUiFavoritesFailed => 'تعذّر حفظ المفضّلة. حاول مجددًا.';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return 'استخدام النموذج والوضع';
  }

  @override
  String get e7ModelUiUseSession => 'استخدام في هذه المحادثة';

  @override
  String get e7ModelUiUseNewSessions => 'استخدام في المحادثات الجديدة';

  @override
  String get e7ModelUiTools => 'الأدوات';

  @override
  String get e7ModelUiAttachments => 'المرفقات';

  @override
  String get e7ModelUiDefault => 'الافتراضي';

  @override
  String get e7ModelUiSelectionGone =>
      'لم يعد هذا الخيار متاحًا. حدّث النماذج وحاول مجددًا.';

  @override
  String get e7VoiceUiLocalInput => 'إدخال صوتي محلي';

  @override
  String get e7VoiceUiChooseModel => 'اختر نموذج Whisper INT8 متعدد اللغات';

  @override
  String get e7VoiceUiPrivacyDownload =>
      'يبقى الصوت على هذا الجهاز. يُحوَّل الكلام إلى نص محليًا ويُحذف الصوت بعد الاستخدام. يتطلب تنزيل النموذج مرة واحدة اتصالًا بالإنترنت.';

  @override
  String get e7VoiceUiNoBuiltInMic =>
      'يشير Android إلى عدم وجود ميكروفون مدمج. قد يعمل الإدخال الصوتي باستخدام ميكروفون سلكي أو USB.';

  @override
  String get e7VoiceUiLanguage => 'لغة تحويل الكلام إلى نص';

  @override
  String get e7VoiceUiVerifying => 'جارٍ التحقّق من النموذج المنزّل';

  @override
  String get e7VoiceUiCancelDownload => 'إلغاء التنزيل';

  @override
  String get e7VoiceUiNotNow => 'ليس الآن';

  @override
  String get e7VoiceUiUseModel => 'استخدام النموذج';

  @override
  String get e7VoiceUiDownload => 'تنزيل';

  @override
  String get e7VoiceUiKeep => 'احتفاظ';

  @override
  String get e7VoiceUiDelete => 'حذف';

  @override
  String get e7VoiceUiDefaultBadge => 'افتراضي';

  @override
  String get e7VoiceUiOptionalBadge => 'اختياري';

  @override
  String get e7VoiceUiInstalledBadge => 'مثبّت';

  @override
  String get e7VoiceUiNotInstalledBadge => 'غير مثبّت';

  @override
  String get e7VoiceUiSetupBusy => 'غير متاح أثناء إعداد النموذج';

  @override
  String get e7VoiceUiSelected => 'محدّد';

  @override
  String get e7VoiceUiSelectHint => 'اضغط مرتين للاختيار';

  @override
  String get e7VoiceUiDefault => 'الافتراضي';

  @override
  String get e7VoiceUiOptional => 'اختياري';

  @override
  String get e7VoiceUiInstalled => 'مثبّت';

  @override
  String get e7VoiceUiRedownload => 'إعادة التنزيل';

  @override
  String get e7VoiceUiOpenSettings => 'فتح إعدادات التطبيق';

  @override
  String get e7VoiceUiRetry => 'إعادة المحاولة';

  @override
  String get e7VoiceUiStartListening => 'بدء الاستماع';

  @override
  String get e7VoiceUiDraftReady => 'النص جاهز للمراجعة';

  @override
  String get e7VoiceUiNeedsAttention => 'الإدخال الصوتي يحتاج إلى انتباهك';

  @override
  String get e7VoiceUiModelRequired => 'يلزم نموذج محلي';

  @override
  String e7ModelUiCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نموذج',
      many: '$count نموذجًا',
      few: '$count نماذج',
      two: 'نموذجان',
      one: 'نموذج واحد',
      zero: 'لا توجد نماذج',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiContext(String count) {
    return 'سعة سياق $count';
  }

  @override
  String e7ModelUiOutput(String count) {
    return 'حد إخراج $count';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return 'إضافة $model إلى المفضّلة';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return 'إزالة $model من المفضّلة';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · جهد الاستدلال: $effort';
  }

  @override
  String get e7ModelUiLoading => 'جارٍ تحميل قائمة النماذج';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input إدخال · $output إخراج / مليون';
  }

  @override
  String e7VoiceUiDownloadPercent(int percent) {
    return 'جارٍ تنزيل النموذج الصوتي: $percent بالمئة';
  }

  @override
  String e7VoiceUiDownloadProgress(String received, String total) {
    return '$received من $total';
  }

  @override
  String e7VoiceUiSetupFailed(String error) {
    return 'تعذّر إعداد النموذج: $error';
  }

  @override
  String e7VoiceUiDeletePack(String model) {
    return 'هل تريد حذف $model؟';
  }

  @override
  String e7VoiceUiDeleteDetail(String size) {
    return 'سيؤدي ذلك إلى إزالة $size من مساحة تخزين التطبيق الخاصة. يمكنك تنزيله مجددًا لاحقًا.';
  }

  @override
  String e7VoiceUiDownloadSize(String size) {
    return 'حجم التنزيل: $size';
  }

  @override
  String e7VoiceUiPackSemantics(
    String model,
    String size,
    String badges,
    String description,
  ) {
    return '$model، $size، $badges. $description';
  }

  @override
  String get e7VoiceUiLicenses => 'تراخيص الصوت ومصادره';

  @override
  String get e7VoiceUiNoticesFailed => 'تعذّر تحميل تراخيص الصوت. حاول مجددًا.';

  @override
  String get e7VoiceUiAuto => 'اكتشاف تلقائي';

  @override
  String get e7VoiceUiEnglish => 'الإنجليزية';

  @override
  String get e7VoiceUiArabic => 'العربية';

  @override
  String get e7VoiceUiBalanced => 'متوازن';

  @override
  String get e7VoiceUiBalancedDetail =>
      'توازن موصى به بين الجودة ومساحة التخزين والسرعة.';

  @override
  String get e7VoiceUiAccurate => 'دقة عالية';

  @override
  String get e7VoiceUiAccurateDetail =>
      'جودة أعلى اختيارية؛ تتطلب ذاكرة أكبر بكثير.';

  @override
  String get e7VoiceUiCompact => 'بديل صغير الحجم';

  @override
  String get e7VoiceUiCompactDetail =>
      'الأسرع والأصغر حجمًا؛ تقل دقته مع الصوت غير الواضح.';

  @override
  String get e7VoiceUiUnsupportedAbi =>
      'لا يدعم أي محرّك صوتي مضمّن البنية الثنائية لهذا الجهاز.';

  @override
  String get e7VoiceUiPermissionBlocked =>
      'الوصول إلى الميكروفون محظور. اسمح به في إعدادات تطبيق Android.';

  @override
  String get e7VoiceUiPermissionRequired =>
      'يلزم إذن الميكروفون للإدخال الصوتي المحلي.';

  @override
  String get e7VoiceUiDeviceUnavailable =>
      'الإدخال الصوتي المحلي غير متاح. أوقف التشغيل، وتحقّق من إعدادات الميكروفون، ثم حاول مجددًا.';

  @override
  String get e7VoiceUiInputUnavailable =>
      'الإدخال الصوتي المحلي غير متاح على هذه المنصّة.';

  @override
  String get e7VoiceUiNoAudio => 'لم يُلتقط أي صوت.';

  @override
  String get e7VoiceUiInterrupted => 'انقطع التسجيل.';

  @override
  String get e7VoiceUiMicrophoneError =>
      'أبلغ الميكروفون عن خطأ. تحقّق من إعداداته وحاول مجددًا.';

  @override
  String get e7VoiceUiInputFailed => 'تعذّر إكمال الإدخال الصوتي. حاول مجددًا.';

  @override
  String get e7VoiceUiTechnicalDetails => 'التفاصيل التقنية';

  @override
  String get e7VoiceUiHttpsRequired =>
      'لا يمكن تنزيل النماذج الصوتية إلا عبر HTTPS.';

  @override
  String get e7VoiceUiTransportClosed => 'أُغلق اتصال تنزيل النموذج الصوتي.';

  @override
  String get e7VoiceUiInvalidRedirect =>
      'أعاد تنزيل النموذج الصوتي توجيهًا غير صالح.';

  @override
  String get e7VoiceUiUnsafeRedirect =>
      'أُعيد توجيه تنزيل النموذج الصوتي إلى عنوان لا يستخدم HTTPS.';

  @override
  String get e7VoiceUiNoResponse => 'لم يستجب خادم النماذج.';

  @override
  String get e7VoiceUiDownloadTimeout =>
      'انتهت مهلة تنزيل النموذج. تحقّق من الاتصال وحاول مجددًا.';

  @override
  String get e7VoiceUiChecksumFailed =>
      'لم يجتز النموذج المنزّل فحص البصمة. أعد تنزيله.';

  @override
  String get e7VoiceUiVerificationFailed =>
      'لم يجتز النموذج التحقّق النهائي. أعد تنزيله.';

  @override
  String get e7VoiceUiHttpFailed => 'رفض خادم النماذج التنزيل. حاول مجددًا.';

  @override
  String get e7VoiceUiLengthFailed =>
      'حجم النموذج المنزّل غير متوقّع. أعد تنزيله.';

  @override
  String get e7VoiceUiIncomplete => 'تنزيل النموذج غير مكتمل. حاول مجددًا.';

  @override
  String get e7VoiceUiDownloadFailed =>
      'تعذّر تنزيل النموذج الصوتي. حاول مجددًا.';

  @override
  String e7VoiceUiMemory(String model, int required, int available) {
    return 'يحتاج $model إلى هاتف بذاكرة تبلغ نحو $required ميغابايت؛ ذاكرة هذا الهاتف $available ميغابايت.';
  }

  @override
  String e7VoiceUiStorage(String model, String size) {
    return 'يتطلب $model مساحة خالية قدرها $size، تشمل هامشًا احتياطيًا.';
  }

  @override
  String get e7ModelUiProviderFallback => 'مزوّد خدمة';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first و$last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first، و$last';
  }

  @override
  String get e7ModelUiListSeparator => '، ';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'سجّل OpenCode الدخول إلى $providers، لكنه لم يحمّل بيانات الخدمة بعد. لذلك يتعذّر استخدام النماذج وتظهر رسالة «Model not found». أعد التحميل لتفعيل تسجيل الدخول.',
      one:
          'سجّل OpenCode الدخول إلى $providers، لكنه لم يحمّل بيانات الخدمة بعد. لذلك يتعذّر استخدام النماذج وتظهر رسالة «Model not found». أعد التحميل لتفعيل تسجيل الدخول.',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain =>
      'تعذّر الوصول إلى OpenCode. حاول مرة أخرى.';

  @override
  String get approvalsUiMenu => 'الموافقات';

  @override
  String get approvalsUiInheritedFrom => 'موروث من المحادثة الأصل';

  @override
  String get approvalsUiFollowParent => 'اتبع المحادثة الأصل مجددًا';

  @override
  String get approvalsUiIndicatorOn => 'الموافقة تلقائيًا';

  @override
  String approvalsUiAutoApproved(String action) {
    return 'تمت الموافقة تلقائيًا · $action';
  }

  @override
  String get approvalsUiFailedDetail =>
      'فشلت الموافقة التلقائية. راجع هذا الطلب.';

  @override
  String approvalsUiSaveFailed(String error) {
    return 'تعذّر حفظ إعداد الموافقة: $error';
  }

  @override
  String get approvalsUiIndicatorPaused => 'الموافقة التلقائية متوقفة مؤقتًا';

  @override
  String get handoffUiComputerTitle => 'المتابعة على الحاسوب';

  @override
  String handoffUiComputerIntro(String binary) {
    return 'شغّل هذا الأمر في طرفية على الحاسوب الذي يعمل عليه هذا الخادم. سيفتح المحادثة نفسها في واجهة $binary. لن يُرسل أي شيء حتى تكتب بنفسك.';
  }

  @override
  String get handoffUiComputerDirectoryNote =>
      'تنتمي المحادثات إلى مجلد مشروع، لذلك ينتقل الأمر أولًا إلى مجلد هذه المحادثة.';

  @override
  String handoffUiComputerVerify(String verified, String binary) {
    return 'تم التحقق من الصيغة مع $verified. إذا كان الإصدار المثبّت لديك مختلفًا، فراجع $binary --help للتأكد من خيار --session.';
  }

  @override
  String get handoffUiUnavailableDirectory =>
      'لم يُبلغ الخادم عن مجلد مشروع لهذه المحادثة، لذلك لا يوجد مجلد لفتحها فيه. أعد تحميل المحادثة وحاول مرة أخرى.';

  @override
  String get handoffUiUnavailableWorkspace =>
      'تعمل هذه المحادثة داخل بيئة سحابية. مجلدها يخص مضيف البيئة، لذلك لا يمكن لأمر طرفية عادي فتحها. صدّر المحادثة ثم استوردها بدلًا من ذلك.';

  @override
  String get handoffUiUnavailableReference =>
      'لا يمكن وضع مرجع هذه المحادثة في أمر بشكل آمن.';

  @override
  String get handoffUiPhoneTitle => 'الفتح على هاتف آخر';

  @override
  String get handoffUiPhoneIntro =>
      'امسح هذا الرمز بتطبيق OpenCode Mobile على الهاتف الآخر. لا يحمل الرمز سوى معرّف هذا الخادم المحفوظ ومعرّف المحادثة: لا رسائل ولا عنوان ولا كلمة مرور. يجب أن يكون هذا الخادم محفوظًا مسبقًا على الهاتف الآخر.';

  @override
  String get handoffUiPhoneQrLabel => 'رمز QR يفتح هذه المحادثة على هاتف آخر';

  @override
  String get handoffUiPhoneLinkLabel => 'الرابط';

  @override
  String get handoffUiPhoneCopyLink => 'نسخ الرابط';

  @override
  String get handoffUiPhoneUnavailable =>
      'تعذّر إنشاء رابط لهذه المحادثة. أعد تحميل المحادثة وحاول مرة أخرى.';

  @override
  String get handoffUiLinkServerMissing =>
      'هذا الخادم غير محفوظ على هذا الهاتف. أضفه من قسم الخوادم، ثم امسح الرمز مرة أخرى.';

  @override
  String get handoffUiLinkDismiss => 'تجاهل';

  @override
  String get handoffUiLinkWaiting => 'سيتم فتح المحادثة بمجرد اتصال الخادم…';

  @override
  String get handoffUiLinkReentry =>
      'أدخل كلمة مرور هذا الخادم مرة أخرى، ثم امسح الرمز مجددًا.';

  @override
  String get handoffUiLinkConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. تحقق منه في قسم الخوادم، ثم امسح الرمز مرة أخرى.';

  @override
  String get teamUiAccessControls => 'القرارات والتحكم';

  @override
  String get teamUiAccessReadOnly => 'للقراءة فقط';

  @override
  String get teamUiAddAddressHint => 'http://100.x.x.x:8373';

  @override
  String get teamUiAddAddressLabel => 'العنوان';

  @override
  String get teamUiAddManually => 'إضافة يدويًا';

  @override
  String get teamUiAddSubmit => 'اختبار وتشغيل';

  @override
  String get teamUiAddTesting => 'جارٍ فحص العنوان…';

  @override
  String get teamUiAddTitle => 'إضافة مضيف فريق الذكاء الاصطناعي';

  @override
  String get teamUiAddressRequired => 'أدخل عنوان المضيف.';

  @override
  String get teamUiChange => 'تغيير';

  @override
  String get teamUiDisclaimerComputer => 'يعمل بسرعة حاسوبك؛ أبقِه مستيقظًا';

  @override
  String get teamUiDisclaimerPhone =>
      'قد يوقفه أندرويد عند انطفاء الشاشة؛ وهو أبطأ من الحاسوب';

  @override
  String get teamUiEditorBody =>
      'إذا كان هذا الحاسوب يشغّل Gas City، فسيعثر عليه التطبيق تلقائيًا.';

  @override
  String teamUiEditorConfigured(String url) {
    return 'مضيف فريق الذكاء الاصطناعي: $url';
  }

  @override
  String get teamUiEditorTitle => 'فريق الذكاء الاصطناعي (اختياري)';

  @override
  String get teamUiHostGuideIntro =>
      'كل شيء يبقى داخل شبكة Tailscale الخاصة بك؛ ولا يُنشر أي شيء على الإنترنت.';

  @override
  String get teamUiHostGuideStep1 =>
      'ثبّت أدوات Gas City الثلاث، gc وbd وdolt، ضمن PATH؛ يتضمن الدليل الكامل رابط تنزيل كل أداة وبصمتها للتحقق منها. ثم تحقّق من العثور على الأدوات الثلاث:';

  @override
  String get teamUiHostGuideStep2 =>
      'احفظ ملف الفريق من الدليل الكامل في مجلد بجانب مشروعك. ثم أعدّ الفريق وأضف مشروعك، مع كتابة المجلد أولًا:';

  @override
  String get teamUiHostGuideStep3 => 'شغّل الفريق وتحقّق من استجابته:';

  @override
  String get teamUiHostGuideStep4 =>
      'نزّل الواجهة التي تتيح لهذا الهاتف الاتصال عبر Tailscale، وتحقّق منها وشغّلها، مع وضع حساب Tailscale الخاص بك بعد --allow. ثم أضفها هنا: عنوان Tailscale للحاسوب مع المنفذ الموجود في الأمر، واسم الفريق.';

  @override
  String get teamUiHostGuideTitle => 'شغّل فريق ذكاء اصطناعي على حاسوبك';

  @override
  String get teamUiHostModeComputer => 'حاسوب';

  @override
  String get teamUiHostModePhone => 'هذا الهاتف';

  @override
  String get teamUiHow => 'كيف';

  @override
  String get teamUiKeep => 'إبقاء';

  @override
  String get teamUiLabelAccess => 'الصلاحية';

  @override
  String get teamUiLabelAddress => 'العنوان';

  @override
  String get teamUiLabelCity => 'المدينة';

  @override
  String get teamUiLabelHost => 'المضيف';

  @override
  String get teamUiLabelProvider => 'المزوّد';

  @override
  String get teamUiLabelVersion => 'الإصدار';

  @override
  String get teamUiLearnHow => 'تعرّف على الطريقة';

  @override
  String get teamUiPluginsTitle => 'الإضافات';

  @override
  String get teamUiReadOnlyBody =>
      'يمكنك متابعة هذا الفريق من الهاتف. أما الإجابة والتوجيه فيحتاجان إلى الواجهة الأمامية على الحاسوب.';

  @override
  String get teamUiReasonCityNotRunning => 'مضيف الفريق قيد البدء';

  @override
  String get teamUiReasonNotGasCity => 'لم يُعثر على فريق ذكاء اصطناعي';

  @override
  String get teamUiReasonPlainHttp => 'العنوان ليس على شبكة Tailscale';

  @override
  String get teamUiReasonReadFailed => 'فشلت آخر قراءة';

  @override
  String get teamUiReasonUnreachable => 'تعذّر الوصول إلى المضيف';

  @override
  String get teamUiRefresh => 'تحديث';

  @override
  String get teamUiRowConnecting => 'مفعّل · جارٍ الاتصال…';

  @override
  String get teamUiRowNotAvailable => 'غير متاح على هذا الخادم';

  @override
  String teamUiRowNotAvailableReason(String reason) {
    return 'غير متاح على هذا الخادم · $reason';
  }

  @override
  String get teamUiRowOff => 'متوقف';

  @override
  String teamUiRowOn(String server) {
    return 'مفعّل · $server';
  }

  @override
  String teamUiRowOnReadOnly(String server) {
    return 'مفعّل · $server · للعرض فقط';
  }

  @override
  String get teamUiRowReconnecting => 'مفعّل · جارٍ إعادة الاتصال…';

  @override
  String get teamUiRowTitle => 'فريق الذكاء الاصطناعي · Gas City';

  @override
  String teamUiRowUnreachable(String minutes) {
    return 'مفعّل · تعذّر الوصول إلى المضيف منذ $minutes دقيقة';
  }

  @override
  String get teamUiTailnetRequired =>
      'يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale الخاصة بك أو على هذا الجهاز. استخدم عنوان Tailscale الخاص بالحاسوب (100.x.x.x أو name.ts.net).';

  @override
  String get teamUiTechnicalDetails => 'التفاصيل التقنية';

  @override
  String get teamUiTechnicalLastAnswer => 'آخر استجابة من المضيف';

  @override
  String get teamUiTermAgent => 'وكيل · polecat';

  @override
  String get teamUiTermProject => 'مشروع · rig';

  @override
  String get teamUiTermRun => 'تشغيل · convoy';

  @override
  String get teamUiTermTeam => 'فريق · city';

  @override
  String get teamUiTermWork => 'عمل · bead';

  @override
  String get teamUiTermsHeading => 'المصطلحات';

  @override
  String get teamUiTurnOffBody =>
      'يزيل بطاقته وعناصر الانتباه وبيانات الفريق المخزّنة مؤقتًا من هذا الهاتف. لا يتغير شيء على المضيف.';

  @override
  String get teamUiTurnOffConfirm => 'إيقاف AI Team';

  @override
  String teamUiTurnOffTitle(String server) {
    return 'هل تريد إيقاف فريق الذكاء الاصطناعي لخادم $server؟';
  }

  @override
  String get teamUiVerdictCityNotRunning =>
      'مضيف الفريق قيد البدء. حاول مجددًا بعد قليل.';

  @override
  String get teamUiVerdictNotGasCity =>
      'لا يشغّل هذا الخادم فريق ذكاء اصطناعي بعد. جهّز واحدًا على الحاسوب — يستغرق ذلك بضع دقائق.';

  @override
  String get teamUiVerdictUnreachable =>
      'لا استجابة من هذا العنوان. تحقق منه، ومن أن الحاسوب مستيقظ ومتصل بشبكة Tailscale الخاصة بك.';

  @override
  String get teamUiVersionUnknown => 'غير معروف';

  @override
  String teamUiCardAgentsSummary(
    int total,
    int working,
    int waiting,
    int idle,
    int stopped,
  ) {
    return '$total وكلاء: $working يعملون، $waiting ينتظرون، $idle خاملون، $stopped متوقفون';
  }

  @override
  String get teamUiCardEmptyHint => 'ابدأ المهام من الحاسوب في الوقت الحالي.';

  @override
  String get teamUiCardEmptyTitle => 'لا توجد مهام حديثة';

  @override
  String get teamUiCardErrorCityNotRunning =>
      'مضيف الفريق قيد البدء. حاول مرة أخرى بعد لحظات.';

  @override
  String get teamUiCardErrorNotGasCity =>
      'هذا الخادم لا يشغّل فريق ذكاء اصطناعي بعد. جهّز واحدًا على الحاسوب، فالأمر يستغرق بضع دقائق.';

  @override
  String get teamUiCardErrorPlainHttp =>
      'يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale لديك أو على هذا الجهاز. استخدم tailscale serve على الحاسوب ثم حاول مرة أخرى.';

  @override
  String get teamUiCardErrorUnreachable =>
      'تعذّر الوصول إلى مضيف الفريق. يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale لديك أو على هذا الجهاز.';

  @override
  String get teamUiStateUnreachableTitle => 'تعذّر الوصول إلى مضيف الفريق';

  @override
  String get teamUiStateNotGasCityTitle =>
      'لا يوجد فريق ذكاء اصطناعي على هذا الخادم';

  @override
  String get teamUiStateStartingTitle => 'مضيف الفريق قيد البدء';

  @override
  String get teamUiStatePlainHttpTitle =>
      'لا يمكن لفريق الذكاء الاصطناعي استخدام هذا العنوان';

  @override
  String get teamUiStateNotAnsweringTitle => 'مضيف الفريق لا يستجيب';

  @override
  String get teamUiCardLoading => 'جارٍ الاتصال بمضيف الفريق…';

  @override
  String teamUiCardRefreshFailed(String time) {
    return 'فشل آخر تحديث · تُعرض بيانات من $time';
  }

  @override
  String get teamUiCardRetry => 'إعادة المحاولة';

  @override
  String get teamUiCardRunStateBlocked => 'معطّل';

  @override
  String get teamUiCardRunStateCancelled => 'أُلغي';

  @override
  String get teamUiCardRunStateCompleted => 'مكتمل';

  @override
  String get teamUiCardRunStateFailed => 'فشل';

  @override
  String get teamUiCardRunStatePlanning => 'قيد التخطيط';

  @override
  String get teamUiCardRunStateUnknown => 'غير معروف';

  @override
  String get teamUiCardRunStateWaiting => 'بانتظار عامل';

  @override
  String get teamUiCardRunStateWorking => 'قيد العمل';

  @override
  String get teamUiCardRunStateWaitingMerge => 'قيد المراجعة';

  @override
  String get teamUiCardRunStateMerged => 'مكتمل · دُمج';

  @override
  String teamUiCardStale(String time) {
    return 'تُعرض بيانات من $time · تعذّر الوصول إلى المضيف';
  }

  @override
  String get teamUiHomeAgentNoWork => 'لا عمل حالي';

  @override
  String get teamUiHomeAgentStateBlocked => 'معطّل';

  @override
  String get teamUiHomeAgentStateCrashed => 'تعطّل';

  @override
  String get teamUiHomeAgentStateIdle => 'خامل';

  @override
  String get teamUiHomeAgentStateStopped => 'متوقف';

  @override
  String get teamUiHomeAgentStateUnknown => 'غير معروف';

  @override
  String get teamUiHomeAgentStateWaiting => 'بانتظارك';

  @override
  String get teamUiHomeAgentStateWorking => 'قيد العمل';

  @override
  String get teamUiHomeFilterActive => 'النشطة';

  @override
  String get teamUiHomeFilterAll => 'الكل';

  @override
  String get teamUiHomeFilterBlocked => 'المعطّلة';

  @override
  String get teamUiHomeFilterCompleted => 'المكتملة';

  @override
  String get teamUiHomeGateAnswerOnComputer =>
      'أجب عن هذا على الحاسوب. يمكن للهاتف المتابعة فقط في الوقت الحالي.';

  @override
  String get teamUiHomeGateAnswerOnPhone =>
      'أجب عن هذا في المضيف على هذا الهاتف. يمكن للتطبيق المتابعة فقط في الوقت الحالي.';

  @override
  String get teamUiHomeGateKindChoice => 'قرار';

  @override
  String get teamUiHomeGateKindConfirmation => 'موافقة';

  @override
  String get teamUiHomeGateKindFreeText => 'سؤال';

  @override
  String get teamUiHomeGateKindGateBead => 'بوابة';

  @override
  String get teamUiHomeGateKindReviewReady => 'جاهز للمراجعة';

  @override
  String get teamUiHomeGateKindRunFailed => 'فشل التشغيل';

  @override
  String get teamUiHomeGateKindUnknown => 'يحتاجك';

  @override
  String teamUiHomeGateLinkAgent(String name) {
    return 'الوكيل $name';
  }

  @override
  String teamUiHomeGateLinkRun(String title) {
    return 'التشغيل $title';
  }

  @override
  String teamUiHomeGateLinkWork(String title) {
    return 'العمل $title';
  }

  @override
  String get teamUiHomeGateOptions => 'الخيارات';

  @override
  String get teamUiHomeHostRawHeading => 'القيم الخام';

  @override
  String get teamUiHomeRunNeedsYou => 'يحتاجك';

  @override
  String teamUiHomeRunProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get teamUiHomeRunsEmptyFiltered => 'لا توجد مهام مطابقة.';

  @override
  String get teamUiHomeRunsEmptyHint => 'جرّب عامل تصفية آخر أو امسح البحث.';

  @override
  String get teamUiHomeSearchHint => 'ابحث في المهام';

  @override
  String get teamUiHomeTitle => 'فريق الذكاء الاصطناعي';

  @override
  String get teamUiRunBack => 'رجوع';

  @override
  String teamUiRunBlockedByDeps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'ينتظر $count خطوة أخرى',
      many: 'ينتظر $count خطوة أخرى',
      few: 'ينتظر $count خطوات أخرى',
      two: 'ينتظر خطوتين أخريين',
      one: 'ينتظر خطوة أخرى',
      zero: 'لا ينتظر شيئًا',
    );
    return '$_temp0';
  }

  @override
  String teamUiRunElapsedDays(int count) {
    return '$count ي';
  }

  @override
  String teamUiRunElapsedHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String teamUiRunElapsedMinutes(int count) {
    return '$count د';
  }

  @override
  String teamUiRunSinceHandoff(String elapsed) {
    return '$elapsed منذ التسليم';
  }

  @override
  String get teamUiRunLabelFormula => 'الصيغة';

  @override
  String get teamUiRunLabelId => 'معرّف التشغيل';

  @override
  String get teamUiRunLabelKind => 'النوع';

  @override
  String get teamUiRunLabelLastError => 'آخر خطأ';

  @override
  String get teamUiRunLabelProject => 'المشروع';

  @override
  String get teamUiRunLabelRawState => 'حالة المزوّد';

  @override
  String get teamUiRunLabelStarted => 'بدأ';

  @override
  String get teamUiRunLabelTrackedWork => 'العمل المتتبَّع';

  @override
  String get teamUiRunLabelUpdated => 'حُدّث';

  @override
  String get teamUiRunMissingHint =>
      'ربما أُغلق أو أُزيل. حدّث للتحقق مرة أخرى.';

  @override
  String get teamUiRunMissingTitle => 'لم تعد هذه المهمة موجودة على المضيف';

  @override
  String get teamUiRunTermBatch => 'مهمة · convoy';

  @override
  String get teamUiRunTermFormula => 'مهمة · formula';

  @override
  String get teamUiRunTermUnknown => 'مهمة';

  @override
  String teamUiRunTimelineAgentStopped(String name) {
    return 'توقف $name';
  }

  @override
  String teamUiRunTimelineAgentWoke(String name) {
    return 'بدأ $name';
  }

  @override
  String teamUiRunTimelineGateOpened(String title) {
    return 'يحتاجك: $title';
  }

  @override
  String teamUiRunTimelineGateResolved(String title) {
    return 'أُجيب: $title';
  }

  @override
  String teamUiRunTimelineRunChanged(String state) {
    return 'التشغيل الآن $state';
  }

  @override
  String teamUiRunTimelineWorkClosed(String title) {
    return 'أُغلق $title';
  }

  @override
  String teamUiRunTimelineWorkCreated(String title) {
    return 'أُضيف $title';
  }

  @override
  String teamUiRunTimelineWorkUpdated(String title) {
    return 'حُدّث $title';
  }

  @override
  String teamUiAgentContextSemantics(int percent) {
    return 'استُخدم $percent% من السياق';
  }

  @override
  String teamUiAgentContextShort(int percent) {
    return 'السياق $percent%';
  }

  @override
  String get teamUiAgentLabelBranch => 'الفرع';

  @override
  String get teamUiAgentLabelHarness => 'البيئة';

  @override
  String get teamUiAgentLabelModel => 'النموذج';

  @override
  String get teamUiAgentLabelPack => 'الحزمة';

  @override
  String get teamUiAgentLabelPool => 'المجمّع';

  @override
  String get teamUiAgentLabelSessionAge => 'عمر الجلسة';

  @override
  String get teamUiAgentLabelSessionId => 'الجلسة';

  @override
  String get teamUiAgentLabelSessionName => 'اسم الجلسة';

  @override
  String get teamUiAgentLabelWorkDir => 'مجلد العمل';

  @override
  String get teamUiAgentMissingHint => 'ربما أُعيد تدويره. حدّث للتحقق.';

  @override
  String get teamUiAgentMissingTitle => 'هذا الوكيل لم يعد على المضيف';

  @override
  String get teamUiAgentNeedsYou => 'بحاجة إليك';

  @override
  String get teamUiAgentOutputConnecting => 'جارٍ الاتصال بالجلسة…';

  @override
  String get teamUiAgentOutputCopy => 'نسخ المخرجات';

  @override
  String get teamUiAgentOutputEnded =>
      'انتهت الجلسة · المخرجات لم تعد على المضيف';

  @override
  String get teamUiAgentOutputJump => 'الانتقال إلى الأحدث';

  @override
  String get teamUiAgentOutputLive => 'مباشر';

  @override
  String get teamUiAgentOutputTitle => 'المخرجات المباشرة';

  @override
  String get teamUiAgentOutputUnavailable =>
      'المخرجات المباشرة غير متاحة لهذا الوكيل';

  @override
  String get teamUiAgentRecyclingSoon =>
      'إعادة التدوير قريبًا · السياق شبه ممتلئ';

  @override
  String teamUiAgentSessionAge(String age) {
    return 'الجلسة منذ $age';
  }

  @override
  String teamUiAgentTermSession(String id) {
    return 'وكيل · جلسة $id';
  }

  @override
  String get teamUiAgentValueUnknown => 'غير مُبلَّغ عنه';

  @override
  String get teamUiWorkLabelAssignee => 'المكلَّف';

  @override
  String get teamUiWorkLabelClosedReason => 'سبب الإغلاق';

  @override
  String get teamUiWorkLabelDependsOn => 'يعتمد على (معرّفات)';

  @override
  String get teamUiWorkLabelId => 'معرّف العمل';

  @override
  String get teamUiWorkLabelLabels => 'الوسوم';

  @override
  String get teamUiWorkLabelParent => 'الأصل';

  @override
  String get teamUiWorkLabelProject => 'المشروع';

  @override
  String get teamUiWorkLabelRawState => 'حالة المزوّد';

  @override
  String get teamUiWorkLabelRun => 'معرّف التشغيل';

  @override
  String get teamUiWorkLabelSession => 'معرّف الجلسة';

  @override
  String get teamUiWorkLabelSessionName => 'اسم الجلسة';

  @override
  String get teamUiWorkLabelType => 'النوع';

  @override
  String get teamUiWorkOwnerNone => 'غير مُسنَد';

  @override
  String get teamUiWorkSheetBlocking => 'يعطّل';

  @override
  String get teamUiWorkSheetBranch => 'الفرع';

  @override
  String get teamUiWorkSheetClosed => 'أُغلق';

  @override
  String get teamUiWorkSheetCreated => 'أُنشئ';

  @override
  String get teamUiWorkSheetDependencies => 'يعتمد على';

  @override
  String get teamUiWorkSheetDescription => 'الوصف';

  @override
  String get teamUiWorkSheetMissing => 'عنصر العمل هذا لم يعد على المضيف.';

  @override
  String get teamUiWorkSheetNoTimestamps => 'لم يرسل المضيف طوابع زمنية.';

  @override
  String get teamUiWorkSheetOpenSession => 'فتح الجلسة';

  @override
  String get teamUiWorkSheetOutput => 'المخرجات';

  @override
  String teamUiWorkSheetStamp(String date, String clock, String age) {
    return '$date · $clock ($age)';
  }

  @override
  String get teamUiWorkSheetTarget => 'هدف الدمج';

  @override
  String get teamUiWorkSheetTimestamps => 'الطوابع الزمنية';

  @override
  String get teamUiWorkSheetUpdated => 'حُدّث';

  @override
  String get teamUiWorkSheetValidation => 'التحقق';

  @override
  String get teamUiWorkSheetValidationFailed => 'فشل';

  @override
  String get teamUiWorkSheetValidationPassed => 'نجح';

  @override
  String get teamUiWorkSheetValidationUnknown => 'سُجّلت نتيجة';

  @override
  String get teamUiWorkSheetWorktree => 'شجرة العمل';

  @override
  String get teamUiWorkStateBlocked => 'معطّل';

  @override
  String get teamUiWorkStateCancelled => 'أُلغي';

  @override
  String get teamUiWorkStateCompleted => 'مكتمل';

  @override
  String get teamUiWorkStateFailed => 'فشل';

  @override
  String get teamUiWorkStateNeedsInput => 'يحتاج مدخلات';

  @override
  String get teamUiWorkStateQueued => 'في الانتظار';

  @override
  String get teamUiWorkStateReady => 'جاهز';

  @override
  String get teamUiWorkStateReview => 'مراجعة';

  @override
  String get teamUiWorkStateUnknown => 'غير معروف';

  @override
  String get teamUiWorkStateWaiting => 'منتظر';

  @override
  String get teamUiWorkStateWorking => 'قيد العمل';

  @override
  String teamUiUsageCostEstimated(String cost) {
    return '$cost تقديريًا';
  }

  @override
  String teamUiUsageTokens(String count) {
    return '$count رمز';
  }

  @override
  String get teamUiGateAnswerOnHost =>
      'أجب عن هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateAnswerOnHostPhone =>
      'أجب عن هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateCloseOnHost =>
      'أغلق هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateCloseOnHostPhone =>
      'أغلق هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateDestructive => 'إجراء مدمّر';

  @override
  String get teamUiGateFailureActionAgent =>
      'أعد تشغيل الوكيل على المضيف؛ سيستأنف عنصر العمل من جديد.';

  @override
  String get teamUiGateFailureActionAuthentication =>
      'سجّل الدخول مجددًا على المضيف (مفتاح المزوّد أو الرمز)، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionContext =>
      'أعد تشغيل الوكيل بسياق جديد على المضيف؛ سيستأنف من عنصر العمل.';

  @override
  String get teamUiGateFailureActionDependency =>
      'ثبّت الاعتمادية الناقصة أو حدّثها على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionExecution =>
      'اقرأ سجل الخطوة على المضيف، وأصلح الأمر، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionInfrastructure =>
      'تحقق من المضيف وخدماته، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionMergeConflict =>
      'حُل التعارض في شجرة العمل على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionTest =>
      'أصلح الاختبارات الفاشلة على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionUnknown =>
      'اقرأ الخطأ على المضيف وقرر هناك؛ لا يستطيع الهاتف التصرف فيه بعد.';

  @override
  String get teamUiGateFailureAffectedNone =>
      'لا عنصر عمل مفتوحًا في هذا التشغيل.';

  @override
  String get teamUiGateFailureAffectedWork => 'العمل المتأثر';

  @override
  String get teamUiGateFailureClassAgent => 'الوكيل';

  @override
  String get teamUiGateFailureClassAuthentication => 'المصادقة';

  @override
  String get teamUiGateFailureClassContext => 'السياق';

  @override
  String get teamUiGateFailureClassDependency => 'الاعتماديات';

  @override
  String get teamUiGateFailureClassExecution => 'التنفيذ';

  @override
  String get teamUiGateFailureClassInfrastructure => 'البنية التحتية';

  @override
  String get teamUiGateFailureClassMergeConflict => 'تعارض دمج';

  @override
  String get teamUiGateFailureClassTest => 'الاختبارات';

  @override
  String get teamUiGateFailureClassUnknown => 'غير معروف';

  @override
  String get teamUiGateFailureClassification => 'التصنيف';

  @override
  String get teamUiGateFailureErrorNone => 'لم يرسل المضيف نص خطأ.';

  @override
  String get teamUiGateGone =>
      'لم يعد هذا بانتظارك؛ أُجيب عنه أو أُغلق على المضيف.';

  @override
  String get teamUiGateLabelKind => 'نوع المزوّد';

  @override
  String get teamUiGateLabelRequestId => 'معرّف الطلب';

  @override
  String get teamUiGateLabelRunId => 'معرّف التشغيل';

  @override
  String get teamUiGateLabelSessionId => 'معرّف الجلسة';

  @override
  String get teamUiGateLabelWorkId => 'معرّف العمل';

  @override
  String get teamUiGateNoDescription => 'لم يرسل المضيف وصفًا.';

  @override
  String get teamUiGateReviewOnHost =>
      'راجع هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateReviewOnHostPhone =>
      'راجع هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateUnblocks => 'يفتح الطريق أمام';

  @override
  String get teamUiGateUnblocksNone => 'لا شيء ينتظر هذا بعد.';

  @override
  String get teamUiHostKindDesktop => 'حاسوب مكتبي';

  @override
  String get teamUiHostKindDisclaimerLaptop =>
      'النوم وإغلاق الغطاء يوقفان الفريق مؤقتًا؛ وتستأنف التشغيلات عند الاستيقاظ';

  @override
  String get teamUiHostKindDisclaimerWsl =>
      'النوم وإغلاق الغطاء يوقفان الفريق مؤقتًا؛ وتستأنف التشغيلات عند الاستيقاظ. كما يتوقف WSL عند إغلاق آخر نافذة طرفية له.';

  @override
  String get teamUiHostKindHint => 'لا يغيّر سوى التنبيه المعروض مع الفريق.';

  @override
  String get teamUiHostKindLabel => 'نوع الحاسوب';

  @override
  String get teamUiHostKindLaptop => 'حاسوب محمول';

  @override
  String get teamUiHostKindWsl => 'Windows (WSL)';

  @override
  String get teamUiReceiptSent => 'أُرسل · بانتظار تأكيد المضيف';

  @override
  String get teamUiReceiptAnswered => 'تمت الإجابة';

  @override
  String get teamUiReceiptUnconfirmed =>
      'أُرسل دون تأكيد — تحقق على المضيف قبل إعادة الإرسال';

  @override
  String get teamUiGateAnswerSend => 'إرسال';

  @override
  String get teamUiGateAnswerApprove => 'موافقة';

  @override
  String get teamUiGateAnswerDeny => 'رفض';

  @override
  String get teamUiGateAnswerMarkDone => 'تم الإنجاز';

  @override
  String get teamUiGateAnswerHint => 'اكتب إجابتك';

  @override
  String teamUiGateAnswerRunRetry(String work, String agent) {
    return 'إعادة المحاولة';
  }

  @override
  String get teamUiGateAnswerRunLogs => 'عرض السجلات';

  @override
  String get teamUiGateAnswerRunCancel => 'إيقاف العمل';

  @override
  String teamUiGateAnswerRejected(String message) {
    return 'لم يُقبل: $message';
  }

  @override
  String get teamUiGateAnswerRejectedNoMessage => 'لم يقبل المضيف هذه الإجابة.';

  @override
  String get teamUiGateAnswerConfirmApproveTitle =>
      'الموافقة على هذا الإجراء المدمّر؟';

  @override
  String get teamUiGateAnswerConfirmApproveBody =>
      'يصنّف المضيف هذا الإجراء مدمّرًا. لا يمكن التراجع عنه من الهاتف.';

  @override
  String get teamUiGateAnswerConfirmCancelRunTitle => 'إيقاف هذا العمل؟';

  @override
  String get teamUiGateAnswerConfirmCancelRunBody =>
      'يتوقف التشغيل ويبقى عمله المفتوح كما هو.';

  @override
  String get teamUiControlMessage => 'رسالة';

  @override
  String get teamUiControlNudge => 'تنبيه';

  @override
  String get teamUiControlPause => 'إيقاف مؤقت';

  @override
  String get teamUiControlResume => 'استئناف';

  @override
  String get teamUiControlStop => 'إيقاف';

  @override
  String get teamUiControlRestart => 'إعادة تشغيل';

  @override
  String get teamUiControlReassign => 'إعادة إسناد العمل…';

  @override
  String get teamUiControlCreateWork => 'أُرسلت المهمة إلى وكيل';

  @override
  String teamUiControlStopConfirmTitle(String agent) {
    return 'إيقاف $agent؟';
  }

  @override
  String get teamUiControlStopConfirmBody =>
      'تنتهي جلسته الآن. يبقى عمله حيث هو؛ ويمكن للمضيف إيقاظه لاحقًا.';

  @override
  String teamUiControlRestartConfirmTitle(String agent) {
    return 'إعادة تشغيل $agent؟';
  }

  @override
  String get teamUiControlRestartConfirmBody =>
      'تتوقف جلسته ثم تبدأ من جديد. يفقد الوكيل ما كان في سياقه ويستأنف عمله من المضيف.';

  @override
  String get teamUiControlKeep => 'متابعة';

  @override
  String get teamUiControlReceiptSent => 'أُرسل';

  @override
  String get teamUiControlReceiptConfirmed => 'مؤكد';

  @override
  String get teamUiControlReceiptUnconfirmed => 'غير مؤكد';

  @override
  String get teamUiControlReceiptRefused => 'مرفوض';

  @override
  String teamUiControlReceiptLine(String control, String state) {
    return '$control · $state';
  }

  @override
  String get teamUiControlCancelRun => 'إيقاف التشغيل';

  @override
  String get teamUiStartRunFab => 'كلّف الفريق بمهمة';

  @override
  String get teamUiStartRunTitle => 'كلّف الفريق بمهمة';

  @override
  String get teamUiStartRunObjectiveLabel => 'الهدف';

  @override
  String get teamUiStartRunObjectiveHint =>
      'ما الذي ينبغي أن يحققه الفريق؟ نتيجة واحدة، بكلماتك.';

  @override
  String get teamUiStartRunObjectiveEmpty => 'اكتب هدفًا أولًا.';

  @override
  String get teamUiStartRunProjectLabel => 'المشروع';

  @override
  String get teamUiStartRunProjectAny => 'دع المخطِّط يختار';

  @override
  String get teamUiStartRunSupervisionLabel => 'الإشراف';

  @override
  String get teamUiStartRunSupervisionHigh => 'عالٍ';

  @override
  String get teamUiStartRunSupervisionHighHint =>
      'يسأل الفريق قبل كل قرار، وقبل الاختبارات التي تغيّر الحالة، وقبل أي دمج.';

  @override
  String get teamUiStartRunSupervisionBalanced => 'متوازن';

  @override
  String get teamUiStartRunSupervisionBalancedHint =>
      'يقرر الفريق الأمور الروتينية بنفسه ويسأل قبل الدمج وعند الإخفاقات وفي خيارات التصميم.';

  @override
  String get teamUiStartRunSupervisionAutonomous => 'مستقل';

  @override
  String get teamUiStartRunSupervisionAutonomousHint =>
      'يعمل الفريق حتى الإنجاز ضمن حدود المضيف ولا يسأل إلا عندما يتعذر عليه المتابعة.';

  @override
  String get teamUiStartRunPlannerLabel => 'المخطِّط';

  @override
  String get teamUiStartRunPlannerMayor => 'العمدة (Mayor)';

  @override
  String teamUiStartRunSend(String planner) {
    return 'إرسال إلى المخطِّط';
  }

  @override
  String get teamUiStartRunHostGuide => 'دليل المضيف';

  @override
  String get teamUiStartRunWaking => 'جارٍ إيقاظ المخطِّط…';

  @override
  String get teamUiStartRunDirectIntro =>
      'المخطِّط متوقف على هذا المضيف. أعطِ مهمة واحدة مباشرةً إلى وكيل المشروع.';

  @override
  String get teamUiStartRunDirectTitle => 'المهمة';

  @override
  String get teamUiStartRunDirectTitleHint =>
      'سطر واحد: ماذا ينبغي أن يفعل الوكيل؟';

  @override
  String get teamUiStartRunDirectTitleRequired => 'اكتب مهمة أولًا.';

  @override
  String get teamUiStartRunDirectDetails => 'التفاصيل (اختياري)';

  @override
  String get teamUiStartRunDirectSend => 'إرسال إلى وكيل';

  @override
  String get teamUiStartRunRefused => 'لم يقبل المضيف هذه المهمة.';

  @override
  String get teamUiMergeTitleNotReady => 'غير جاهز للدمج';

  @override
  String teamUiMergeRequest(String id) {
    return 'طلب الدمج $id';
  }

  @override
  String get teamUiMergeLineWork => 'بنود العمل';

  @override
  String get teamUiMergeLineTests => 'الاختبارات';

  @override
  String get teamUiMergeLineBuild => 'البناء';

  @override
  String get teamUiMergeLineReview => 'المراجعة';

  @override
  String get teamUiMergeLineConflicts => 'لا تعارضات';

  @override
  String get teamUiMergeLineAcceptance => 'معايير القبول';

  @override
  String teamUiMergeFiles(int files, int additions, int deletions) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files ملف · +$additions / −$deletions',
      many: '$files ملفًا · +$additions / −$deletions',
      few: '$files ملفات · +$additions / −$deletions',
      two: 'ملفان · +$additions / −$deletions',
      one: 'ملف واحد · +$additions / −$deletions',
      zero: 'لا تغييرات في الملفات',
    );
    return '$_temp0';
  }

  @override
  String get teamUiMergeReviewChanges => 'مراجعة التغييرات';

  @override
  String get teamUiMergeApprove => 'اعتماد الطلب';

  @override
  String teamUiMergeApprovedBy(String login) {
    return 'اعتمده $login';
  }

  @override
  String get teamUiMergeMerge => 'دمج';

  @override
  String teamUiMergeConfirmAction(String branch) {
    return 'دمج في $branch';
  }

  @override
  String teamUiMergeDisabledReason(String line, String detail) {
    return 'الدمج متوقف: $line — $detail';
  }

  @override
  String teamUiMergeBoundary(String text) {
    return 'حدود المضيف: $text';
  }

  @override
  String get teamUiMergeRefused => 'لم يقبل المضيف هذا، فلم يُدمج شيء.';

  @override
  String teamUiMergeMerged(String branch, String commit) {
    return 'تم الدمج في $branch · $commit';
  }

  @override
  String teamUiMergeAlready(String branch) {
    return 'موجود بالفعل على $branch';
  }

  @override
  String teamUiMergeUnavailable(String reason) {
    return 'جاهزية الدمج غير متاحة: $reason';
  }

  @override
  String get teamUiMergeNoRoles => 'لا يملك المضيف أدوار دمج لهذا التشغيل';

  @override
  String get teamUiMergeLoading => 'جارٍ التحقق من جاهزية الدمج…';

  @override
  String get teamUiMergePending => 'يعمل على المضيف';

  @override
  String get teamUiMergeChangesTitle => 'التغييرات';

  @override
  String get teamUiMergeChangesEmpty => 'لم يُبلغ المضيف عن تغييرات في الملفات';

  @override
  String get teamUiMergeChangesWork => 'بنود العمل';

  @override
  String get teamUiMergeSent => 'أُرسل · بانتظار تأكيد المضيف';

  @override
  String get teamUiMergeApproveConfirmed => 'تم تسجيل الاعتماد';

  @override
  String teamUiPolicySupervision(String level) {
    return 'الإشراف · $level';
  }

  @override
  String get teamUiPolicyBoundariesLabel => 'الحدود';

  @override
  String get teamUiPolicyBoundariesNone => 'لا حدود مضبوطة على المضيف';

  @override
  String get teamUiPolicyFromHost => 'مضبوطة على المضيف · للقراءة فقط هنا';

  @override
  String teamUiPolicyRig(String rig) {
    return 'لـ $rig';
  }

  @override
  String teamUiPolicySemantics(String level, String boundaries) {
    return 'الإشراف $level. الحدود: $boundaries';
  }

  @override
  String get teamUiRunLabelRawTitle => 'عنوان المزوّد';

  @override
  String get termuxStorageTitle => 'التخزين على هذا الهاتف';

  @override
  String termuxStorageRowUsed(String size) {
    return '$size مستخدمة';
  }

  @override
  String get termuxStorageRowNotScanned => 'لم يُفحص بعد';

  @override
  String get termuxStorageRowScanning => 'جارٍ القياس…';

  @override
  String get termuxStorageScanAction => 'فحص التخزين';

  @override
  String get termuxStorageRescanAction => 'إعادة الفحص';

  @override
  String get termuxStorageIntro =>
      'اعرض مساحة التخزين التي يستخدمها Termux، بما فيها الخادم المحلي والأدوات الأخرى. وسّع أي فئة للاطلاع على تفاصيلها. يمكن تنظيف ملفات تخزين مؤقت محددة قابلة لإعادة الإنشاء فقط؛ وتبقى المشاريع وبيانات الفريق وتسجيلات الدخول وسجل المحادثات محفوظة.';

  @override
  String get termuxStorageScanning => 'جارٍ قياس التخزين';

  @override
  String get termuxStorageScanningDetail =>
      'قد تستغرق الذاكرات المؤقتة الكبيرة دقيقة أو دقيقتين. يمكنك مغادرة هذه الشاشة؛ سيستمر الفحص.';

  @override
  String get termuxStorageCancel => 'إيقاف الفحص';

  @override
  String get termuxStorageCancelled => 'أُوقف الفحص';

  @override
  String get termuxStorageFailed => 'لم يكتمل الفحص. حاول مرة أخرى.';

  @override
  String termuxStorageTotal(String size) {
    return 'تم قياس $size في Termux';
  }

  @override
  String termuxStorageDeletableTotal(String size) {
    return 'يمكن تنظيف $size';
  }

  @override
  String get termuxStorageScannedJustNow => 'فُحص للتو';

  @override
  String termuxStorageScannedMinutesAgo(int minutes) {
    return 'فُحص قبل $minutes دقيقة';
  }

  @override
  String termuxStorageScannedHoursAgo(int hours) {
    return 'فُحص قبل $hours ساعة';
  }

  @override
  String get termuxStorageCatBuildCaches => 'ذاكرات البناء المؤقتة';

  @override
  String get termuxStorageNoteBuildCaches =>
      'ملفات Gradle المؤقتة وذاكرة المحتوى المنزّل لـ npm فقط. قد يلزم تنزيل الملفات مجددًا، مما قد يؤثر في البناء دون اتصال. أوقف عمليات البناء وتثبيت الحزم قبل التنظيف.';

  @override
  String get termuxStorageCatAgentScratch => 'مجلدات عمل الوكلاء المؤقتة';

  @override
  String get termuxStorageNoteAgentScratch =>
      'قد تحتوي المجلدات المؤقتة على عمل غير مكتمل أو ملفات تستخدمها أدوات أخرى. تُعرض أحجامها للاطلاع فقط ولا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatProjectBuildOutputs =>
      'مجلدات بأسماء مرتبطة بالبناء';

  @override
  String get termuxStorageNoteProjectBuildOutputs =>
      'قد تحتوي المجلدات المسماة build أو .dart_tool أو node_modules أو target على ملفاتك أيضًا. لا يكفي الاسم لإثبات إمكانية حذفها، لذلك لا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatToolchains => 'أدوات البناء';

  @override
  String get termuxStorageNoteToolchains =>
      'تثبيتات Android SDK وJava وFlutter. قد تستخدمها مشاريع أخرى ولا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatAiTeam => 'فريق الذكاء الاصطناعي';

  @override
  String get termuxStorageNoteAiTeam =>
      'قد تحتوي مجلدات الفريق وقواعد البيانات والأدوات على عمل تحتاج إلى الاحتفاظ به. لا يمكن حذفها من هنا، حتى عند إيقاف الفريق.';

  @override
  String get termuxStorageCatOpenCode => 'OpenCode نفسه';

  @override
  String get termuxStorageNoteOpenCode =>
      'الخادم وتسجيلات دخوله وسجل المحادثات. لا تُحذف من هنا؛ فللمحادثات شاشتها الخاصة.';

  @override
  String get termuxStorageCatProjects => 'المشاريع (ملفاتك)';

  @override
  String get termuxStorageNoteProjects =>
      'مُدرجة لترى حجمها. لا تُحذف من هنا أبدًا.';

  @override
  String get termuxStorageWillRemove => 'ما الذي يحذفه التنظيف';

  @override
  String get termuxStorageNothingHere => 'لا شيء هنا';

  @override
  String termuxStorageCleanConfirmTitle(String size, String category) {
    return 'هل تريد حذف $size من $category؟';
  }

  @override
  String get termuxStorageCleanConfirmBody =>
      'هل تريد حذف ملفات Gradle المؤقتة وذاكرة محتوى npm المعروضة فقط؟ قد يلزم تنزيل الملفات مجددًا وقد يتأثر البناء دون اتصال. أوقف عمليات البناء وتثبيت الحزم أولًا، ثم أعد الفحص لتحديث الأحجام المقاسة.';

  @override
  String termuxStorageCleanConfirm(String size) {
    return 'حذف $size';
  }

  @override
  String get termuxStorageKeep => 'إبقاء';

  @override
  String get termuxStorageCleaning => 'جارٍ الحذف…';

  @override
  String termuxStorageFreed(String size) {
    return 'تم حذف $size';
  }

  @override
  String get termuxStorageFreedNothing => 'لم يُحذف شيء';

  @override
  String termuxStorageInUse(String process) {
    return 'قيد الاستخدام بواسطة $process. أوقفه أولًا من «يعمل الآن».';
  }

  @override
  String termuxStorageRefusedCount(int count) {
    return 'تُركت $count مسارات في مكانها';
  }

  @override
  String termuxStorageProjectBuild(String size, String build) {
    return '$size · $build في مجلدات مرتبطة بالبناء';
  }

  @override
  String get termuxStorageOpenRunning => 'فتح «يعمل الآن»';

  @override
  String termuxStorageBytesGb(String value) {
    return '$value غ.ب';
  }

  @override
  String termuxStorageBytesMb(String value) {
    return '$value م.ب';
  }

  @override
  String termuxStorageBytesKb(String value) {
    return '$value ك.ب';
  }

  @override
  String termuxStorageBytesB(String value) {
    return '$value بايت';
  }

  @override
  String get termuxProcsTitle => 'يعمل الآن';

  @override
  String get termuxProcsRowLoading => 'جارٍ التحقق…';

  @override
  String get termuxProcsAutoRefresh => 'يتحدّث كل 10 ثوانٍ أثناء فتح الشاشة';

  @override
  String termuxProcsStopSemantics(String name) {
    return 'إيقاف $name';
  }

  @override
  String termuxProcsStopOneTitle(String name) {
    return 'هل تريد إيقاف $name؟';
  }

  @override
  String get termuxProcsStopOneBody =>
      'تتلقى إيقافًا لطيفًا، ثم إيقافًا قسريًا بعد 5 ثوانٍ.';

  @override
  String get termuxProcsProtected => 'محمية · افتح «على هذا الهاتف»';

  @override
  String termuxProcsOrphanParentGone(String elapsed) {
    return 'العملية الأم غير موجودة · تعمل منذ $elapsed';
  }

  @override
  String termuxProcsOrphanCpu(String cpu) {
    return '$cpu من وقت المعالج دون مالك';
  }

  @override
  String get termuxProcsStopping => 'جارٍ الإيقاف…';

  @override
  String termuxProcsStopped(int count) {
    return 'أُوقفت $count';
  }

  @override
  String termuxProcsStoppedForced(int count, int forced) {
    return 'أُوقفت $count (احتاجت $forced إلى إيقاف قسري)';
  }

  @override
  String termuxProcsRemaining(int count) {
    return '$count لم تتوقف';
  }

  @override
  String termuxProcsRefused(int count) {
    return '$count محمية، لم تُوقف';
  }

  @override
  String get termuxProcsEmpty => 'لا شيء يعمل في خادم الهاتف';

  @override
  String get termuxProcsCommand => 'الأمر';

  @override
  String get termuxProcsFolder => 'المجلد';

  @override
  String termuxProcsDurationSeconds(int seconds) {
    return '$seconds ث';
  }

  @override
  String termuxProcsDurationMinutes(int minutes) {
    return '$minutes د';
  }

  @override
  String termuxProcsDurationHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String termuxProcsMemoryMb(int mb) {
    return '$mb م.ب';
  }

  @override
  String get teamUiPhoneOptionalTag => 'اختياري · تجريبي';

  @override
  String get teamUiPhoneChooseProjectTitle => 'اختر مشروعًا';

  @override
  String get teamUiPhoneContinue => 'متابعة';

  @override
  String get teamUiPhoneSuccessTitle =>
      'فريق الذكاء الاصطناعي يعمل على هذا الهاتف';

  @override
  String get teamUiPhoneRetry => 'إعادة المحاولة';

  @override
  String teamUiPhoneFailedChecksum(String name) {
    return 'الملف المنزَّل $name لم يطابق المجموع الاختباري المثبّت في هذا الإصدار، فلم يُثبَّت. لم يُحتفظ بشيء منه؛ تحقّق من الشبكة وحاول مجددًا.';
  }

  @override
  String get teamUiPhoneFailedUnsupportedArch =>
      'معالج هذا الهاتف ليس ARM بنظام 64 بت، وهو ما يحتاجه وقت تشغيل الفريق.';

  @override
  String get teamUiPhoneFailedDownload =>
      'لم يكتمل التنزيل. تحقّق من الاتصال وحاول مجددًا.';

  @override
  String teamUiPhoneFailedDownloadDns(String host) {
    return 'تعذّر على الهاتف العثور على خادم التنزيل $host. تأكّد من اتصال الهاتف بالإنترنت ومن أن DNS الخاص أو مانع الإعلانات لا يحجبه، ثم حاول مجددًا.';
  }

  @override
  String teamUiPhoneFailedDownloadConnect(String host) {
    return 'تعذّر على الهاتف الوصول إلى خادم التنزيل $host. تحقّق من الاتصال ثم حاول مجددًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String teamUiPhoneFailedDownloadTimeout(String host) {
    return 'استغرق خادم التنزيل $host وقتًا طويلًا للرد. حاول مجددًا على اتصال أكثر ثباتًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String teamUiPhoneFailedDownloadTls(String host) {
    return 'تعذّر إنشاء اتصال آمن مع $host. تأكّد من صحة تاريخ الهاتف ووقته ومن عدم وجود وكيل (proxy) يعترض الاتصال، ثم حاول مجددًا.';
  }

  @override
  String teamUiPhoneFailedDownloadHttp(String host, String code) {
    return 'رفض خادم التنزيل $host الملف (HTTP $code). حاول مجددًا لاحقًا؛ وإن تكرر ذلك فتنزيل فريق الذكاء الاصطناعي غير متاح لهذا الإصدار من التطبيق.';
  }

  @override
  String teamUiPhoneFailedDownloadInterrupted(String host) {
    return 'انقطع الاتصال مع $host أثناء التنزيل. حاول مجددًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String get teamUiPhoneFailedDownloadWrite =>
      'تعذّر حفظ التنزيل على هذا الهاتف. حرّر بعض المساحة ثم حاول مجددًا.';

  @override
  String teamUiPhoneFailedDownloadOther(String host, String code) {
    return 'فشل التنزيل من $host (الخطأ $code). تحقّق من الاتصال ثم حاول مجددًا.';
  }

  @override
  String get teamUiPhoneFailedPackages =>
      'لم يتمكن Ubuntu من تثبيت المتطلبات (tmux وjq وlsof وprocps). يوضح المخرج أدناه أيها.';

  @override
  String get teamUiPhoneFailedProject =>
      'مجلد المشروع مفقود أو ليس مستودع git.';

  @override
  String get teamUiPhoneFailedCity =>
      'لم تتمكن Gas City من إنشاء المدينة. يوضح المخرج أدناه السبب.';

  @override
  String get teamUiPhoneFailedSupervisorExited =>
      'توقف المشرف مباشرة بعد بدئه. يوضح المخرج أدناه السبب.';

  @override
  String teamUiPhoneFailedHealth(String url) {
    return 'بدأ المشرف لكنه لم يُجب قط على $url.';
  }

  @override
  String get teamUiPhoneFailedInterrupted =>
      'توقف الإعداد قبل اكتماله. ربما أوقف أندرويد Termux أثناء غياب التطبيق؛ لا يُفقد شيء.';

  @override
  String teamUiPhoneFailedReason(String reason) {
    return 'السبب: $reason';
  }

  @override
  String get teamUiPhoneDispatchFailed =>
      'لم يبدأ Termux الخطوة. افتح Termux مرة واحدة ثم حاول مجددًا.';

  @override
  String get teamUiPhoneSectionTitle => 'على هذا الهاتف';

  @override
  String get teamUiPhoneStatusChecking => 'جارٍ التحقق…';

  @override
  String get teamUiPhoneStatusNotInstalled => 'غير مثبّت';

  @override
  String get teamUiPhoneStatusInstalled => 'مثبّت · لا مدينة بعد';

  @override
  String get teamUiPhoneStatusStopped => 'متوقف';

  @override
  String get teamUiPhoneStatusStarting => 'جارٍ البدء…';

  @override
  String get teamUiPhoneStatusStopping => 'جارٍ الإيقاف…';

  @override
  String teamUiPhoneStatusWorking(String verb) {
    return 'جارٍ العمل… ($verb)';
  }

  @override
  String teamUiPhoneStatusRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يعمل · $count وكيل',
      many: 'يعمل · $count وكيلًا',
      few: 'يعمل · $count وكلاء',
      two: 'يعمل · وكيلان',
      one: 'يعمل · وكيل واحد',
      zero: 'يعمل',
    );
    return '$_temp0';
  }

  @override
  String get teamUiPhoneStatusFailed => 'لا يعمل · فشلت الخطوة الأخيرة';

  @override
  String teamUiPhoneStatusUnreachable(String url) {
    return 'بدأ، لكنه لا يُجيب على $url';
  }

  @override
  String get teamUiPhoneStatusUnknown => 'تعذّرت قراءة الحالة';

  @override
  String teamUiPhoneVersions(String gc, String bd, String dolt) {
    return 'gc $gc · bd $bd · dolt $dolt';
  }

  @override
  String get teamUiPhoneStopTitle => 'إيقاف الفريق على هذا الهاتف؟';

  @override
  String get teamUiPhoneStopBody =>
      'يتوقف الوكلاء العاملون حيث هم. لا يُفقد شيء؛ وتُستأنف التشغيلات عند بدئه من جديد.';

  @override
  String get teamUiPhoneKilled =>
      'أوقف أندرويد الفريق أثناء غياب التطبيق. لم يُفقد شيء.';

  @override
  String get teamUiPhoneKeepRunningTitle => 'أبقِه يعمل';

  @override
  String get teamUiPhoneKeepRunningSubtitle =>
      'قفل الاستيقاظ وإعداد البطارية وقاتل العمليات الشبحية';

  @override
  String get teamUiPhoneTipsIntro =>
      'يوقف أندرويد أعمال الخلفية التي يعدّها مفرطة، والفريق كذلك تمامًا: عشرات عمليات gc وbd القصيرة ووكيل يستهلك المعالج بالكامل. ثلاثة أمور تبقيه حيًّا.';

  @override
  String get teamUiPhoneTipWakeLock =>
      'أبقِ Termux في المقدمة، أو فعّل قفل الاستيقاظ فيه: شغّل termux-wake-lock داخل Termux، أو انقر Acquire wakelock في إشعاره. إطفاء الشاشة من دونه ينهي التشغيل.';

  @override
  String get teamUiPhoneTipBattery =>
      'الإعدادات › التطبيقات › Termux › البطارية › غير مقيّد، وأوقف التنظيف التلقائي الخاص بالشركة المصنّعة لهاتفك عن Termux.';

  @override
  String get teamUiPhoneTipPhantom =>
      'لا يزال أندرويد 12 وما بعده يقتل العمليات الفرعية لتطبيق في الخلفية (قاتل العمليات الشبحية). أوقف ذلك مرة واحدة من Termux نفسه عبر تصحيح الأخطاء اللاسلكي؛ لا حاجة إلى حاسوب:';

  @override
  String get teamUiPhoneTipsCopy => 'نسخ الأوامر';

  @override
  String get teamUiPhoneRemoveTitle =>
      'حذف فريق الذكاء الاصطناعي من هذا الهاتف؟';

  @override
  String teamUiPhoneActionFailed(String reason) {
    return 'لم ينجح ذلك: $reason';
  }

  @override
  String get teamUiPhoneNotAvailable =>
      'غير متاح على هذا الهاتف. يحتاج تشغيل فريق إلى بيئة لينكس 64 بت؛ ولا يستطيع هذا الجهاز أو هذا الإصدار توفيرها.';

  @override
  String teamUiPhoneFailedNoSpace(String detail) {
    return 'لا توجد مساحة كافية على هذا الهاتف. $detail حرّر بعض المساحة (يمكن لقسم «التخزين على هذا الهاتف» تنظيف ذاكرة البناء المؤقتة)، ثم حاول مجددًا.';
  }

  @override
  String get phoneServerConnect => 'اتصال';

  @override
  String get phoneServerOpen => 'فتح';

  @override
  String get phoneServerStart => 'تشغيل';

  @override
  String get phoneServerStop => 'إيقاف';

  @override
  String get phoneServerMore => 'إجراءات أخرى للخادم';

  @override
  String get phoneServerForget => 'نسيان بيانات الدخول المحفوظة';

  @override
  String get phoneServerStartFailed =>
      'لم يبدأ تشغيل الخادم. افتح «إدارة الإعداد» لمعرفة السبب.';

  @override
  String get phoneServerRestartFailed =>
      'لم تتم إعادة تشغيل الخادم. افتح «إدارة الإعداد» لمعرفة السبب.';

  @override
  String get phoneServerStopFailed => 'تعذّر إيقاف الخادم. أعد المحاولة.';

  @override
  String get termuxStorageCatSharedCaches => 'ملفات مؤقتة وبيانات حزم أخرى';

  @override
  String get termuxStorageNoteSharedCaches =>
      'قد تستخدم أدوات أخرى الملفات المؤقتة المشتركة والحزم المثبتة ومجلدات التنزيل، أو قد تحتوي على ملفات تحتاج إلى الاحتفاظ بها. لا يمكن حذفها من هنا.';

  @override
  String get termuxStorageRescanRequired =>
      'فحص سابق · أعد الفحص قبل تنظيف المزيد';

  @override
  String get safetyStopSharingTitle => 'إيقاف مشاركة هذه المحادثة؟';

  @override
  String get safetyStopSharingBody =>
      'يتوقف الرابط عن العمل لدى كل من يملكه. لا يتغير شيء في المحادثة نفسها.';

  @override
  String get safetyStopSharingKeep => 'مواصلة المشاركة';

  @override
  String get safetyStopLocalServerTitle => 'إيقاف الخادم المحلي؟';

  @override
  String get safetyStopLocalServerBody =>
      'يتوقف كل ما ينفذه الوكيل على هذا الهاتف، وينقطع اتصال التطبيق بالخادم. تبقى مشاريعك ومحادثاتك على الهاتف؛ شغّل الخادم مرة أخرى للمتابعة.';

  @override
  String get safetyStopLocalServerKeep => 'إبقاؤه قيد التشغيل';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return 'قطع الاتصال بـ $server؟';
  }

  @override
  String get safetyMcpDisconnectBody =>
      'تفقد الوكلاء أدواته حتى تعيد توصيله، وقد يفشل استدعاء أداة قيد التنفيذ. تبقى إعداداته محفوظة.';

  @override
  String get safetyMcpDisconnectKeep => 'البقاء متصلًا';

  @override
  String get safetyStopOrphanBody =>
      'لا شيء ينتظرها، لكن ما كانت تنفذه يضيع. تتلقى إيقافًا لطيفًا، ثم إيقافًا قسريًا بعد 5 ثوانٍ.';

  @override
  String get settingsHubGroupNotifications => 'الإشعارات والخلفية';

  @override
  String get settingsHubGroupUsage => 'الاستخدام';

  @override
  String get settingsHubGroupHelp => 'المساعدة';

  @override
  String get settingsHubThisServer => 'هذا الخادم';

  @override
  String get settingsHubAccounts => 'الحسابات';

  @override
  String get settingsHubModelAndMode => 'النموذج والوضع';

  @override
  String get settingsHubVoice => 'الصوت';

  @override
  String get settingsHubPrivacyRow => 'الخصوصية والبيانات المحلية';

  @override
  String get settingsHubSearchServerAliases =>
      'server host url address password profile connection health status version update service خادم مضيف عنوان كلمة مرور ملف اتصال حالة إصدار تحديث خدمة';

  @override
  String get settingsHubSearchSavedServersAliases =>
      'servers profiles host url password switch add edit remove خوادم ملفات مضيف عنوان كلمة مرور تبديل إضافة تعديل إزالة profile connection';

  @override
  String get settingsHubSearchPhoneAliases =>
      'phone termux local on-device on device android install setup storage services هاتف محلي جهاز أندرويد تثبيت إعداد تخزين خدمات';

  @override
  String get settingsHubSearchAccountsAliases =>
      'account codex sign in login logout حساب تسجيل دخول خروج';

  @override
  String get settingsHubSearchExternalAgentsAliases =>
      'a2a external agents remote وكلاء خارجيون';

  @override
  String get settingsHubSearchTailscaleAliases =>
      'tailscale vpn network remote private شبكة خاصة بعيد';

  @override
  String get settingsHubSearchDisconnectAliases =>
      'disconnect leave server قطع الاتصال مغادرة خادم';

  @override
  String get settingsHubSearchModelModeAliases =>
      'model mode agent variant thinking default selected نموذج وضع وكيل تفكير افتراضي session chat conversation جلسة دردشة محادثة';

  @override
  String get settingsHubSearchShellAliases =>
      'shell terminal bash zsh command صدفة طرفية أوامر';

  @override
  String get settingsHubSearchPermissionsAliases =>
      'permissions approvals always allow allowed revoke أذونات موافقات سماح دائم إلغاء';

  @override
  String get settingsHubSearchTranscriptAliases =>
      'transcript display thinking reasoning timestamps usage سجل عرض تفكير استدلال طوابع زمنية استخدام';

  @override
  String get settingsHubSearchVoiceAliases =>
      'voice speech microphone dictation model صوت كلام ميكروفون إملاء نموذج';

  @override
  String get settingsHubSearchNotificationsAliases =>
      'notifications alerts quiet hours background check-in check in wi-fi wifi monitor saved servers finished runs approvals questions quota thresholds إشعارات تنبيهات ساعات الهدوء خلفية متابعة واي فاي مراقبة خوادم محفوظة العمليات المنتهية موافقات أسئلة حدود الحصص';

  @override
  String get settingsHubSearchAppearanceAliases =>
      'appearance theme dark light language arabic english colors مظهر سمة داكن فاتح لغة عربية إنجليزية ألوان';

  @override
  String get settingsHubSearchModelsAliases =>
      'models agents provider AI reasoning favorites recent نماذج وكلاء مزود ذكاء اصطناعي استدلال مفضلة';

  @override
  String get settingsHubSearchProvidersAliases =>
      'provider api key keys authentication connect مزود مفتاح مفاتيح مصادقة اتصال';

  @override
  String get settingsHubSearchMcpAliases =>
      'mcp integrations servers tools تكاملات خوادم أدوات';

  @override
  String get settingsHubSearchCommandsAliases =>
      'commands tools skills references slash capabilities أوامر أدوات مهارات مراجع إمكانات';

  @override
  String get settingsHubSearchPluginsAliases =>
      'plugins plugin installed source status AI Team Gas City إضافات مثبت مصدر حالة فريق';

  @override
  String get settingsHubSearchUsageAliases =>
      'usage cost tokens budget quota limit spent remaining threshold quota monitoring provider استخدام تكلفة رموز ميزانية حصة حد منفق متبقي مراقبة الحصص مزوّد plans subscriptions spending alerts خطط اشتراكات إنفاق تنبيهات';

  @override
  String get settingsHubSearchPrivacyAliases =>
      'privacy drafts queue queued prompts read state storage clear delete خصوصية مسودات قائمة انتظار حالة القراءة تخزين مسح حذف';

  @override
  String get settingsHubSearchGuideAliases =>
      'help guide connect tutorial start مساعدة دليل اتصال بدء';

  @override
  String get settingsHubSearchBugAliases =>
      'bug feedback issue support report خطأ ملاحظات مشكلة دعم بلاغ';

  @override
  String get settingsHubSearchDiagnosticsAliases =>
      'diagnostics debug errors log تشخيص أخطاء سجل';

  @override
  String get settingsHubSearchAboutAliases =>
      'about version licenses open source notices privacy data حول إصدار تراخيص مفتوح المصدر إشعارات خصوصية بيانات';

  @override
  String get pluginsSectionOnServer => 'على الخادم';

  @override
  String get notifySectionWhat => 'ما الذي يُشعرني';

  @override
  String get notifyFinishedRuns => 'العمل المكتمل';

  @override
  String get notifyFinishedRunsDetail => 'تصل عدة محادثات في إشعار واحد.';

  @override
  String get notifyRequests => 'أسئلة لك';

  @override
  String get notifyRequestsDetail => 'أجب عنها من الإشعار.';

  @override
  String get notifyQuotaAlerts => 'تنبيهات الاستخدام';

  @override
  String get notifyQuotaAlertsDetail => 'تُضبط في الاستخدام › التنبيهات.';

  @override
  String get notifyBlockedTitle => 'الإشعارات مُعطَّلة لهذا التطبيق';

  @override
  String get notifyBlockedMessage =>
      'لا يسمح Android لـ OpenCode بإشعارك، لذا لن تصلك الأسئلة ولا المحادثات المكتملة.';

  @override
  String get notifySendTest => 'إرسال إشعار تجريبي';

  @override
  String get notifySendTestDetail => 'تحقّق من أن Android يوصلها.';

  @override
  String get notifyQuietDetail => 'بلا صوت في الساعات التي تختارها.';

  @override
  String get notifySectionBackground => 'الخلفية';

  @override
  String get notifySectionServers => 'الخوادم المحفوظة';

  @override
  String get notifyWifiOnly => 'فحص الخوادم عبر Wi-Fi فقط';

  @override
  String get usageSectionSpent => 'المصروف';

  @override
  String get usageSectionRemaining => 'المتبقي';

  @override
  String get serverSwitcherManage => 'إدارة الخوادم';

  @override
  String get serverSwitcherOpen => 'تبديل الخادم';

  @override
  String get discoverSearchGoTo => 'انتقل إلى';

  @override
  String discoverSearchIn(String parent) {
    return 'في $parent';
  }

  @override
  String get discoverProjectAliases =>
      'project tools code folder المشروع أدوات الشيفرة مجلد ملفات تغييرات طرفية';

  @override
  String get discoverFilesAliases =>
      'files browse folder tree preview ملفات تصفح مجلد شجرة معاينة شيفرة';

  @override
  String get discoverChangesAliases =>
      'changes review diff git تغييرات مراجعة التغييرات فروقات غير مودعة تعديلات';

  @override
  String get discoverTerminalAliases =>
      'terminal shell console طرفية صدفة سطر الأوامر';

  @override
  String get discoverHealthAliases =>
      'project health branch lsp صحة المشروع فرع ملفات متغيرة خدمات اللغة منسقات';

  @override
  String get discoverWorktreesAliases =>
      'worktrees branches git أشجار العمل فروع معزولة';

  @override
  String get discoverSearchFilesAliases =>
      'search files find بحث في الملفات اعثر على ملف اسم';

  @override
  String get discoverAllConversationsAliases =>
      'all conversations sessions chats history كل المحادثات السجل كل المشاريع بحث';

  @override
  String get discoverTeamAliases =>
      'ai team agents runs فريق الذكاء الاصطناعي وكلاء عمليات بانتظارك';

  @override
  String get discoverNotifyServersTitle => 'إشعارات الخوادم المحفوظة';

  @override
  String get discoverNotifyWhatAliases =>
      'finished approvals questions check-in quota العمليات المنتهية موافقات أسئلة متابعة تنبيهات الحصص ما الذي ينبهني';

  @override
  String get discoverNotifyQuietAliases =>
      'quiet hours do not disturb mute ساعات الهدوء عدم الإزعاج ليل كتم جدول';

  @override
  String get discoverNotifyBackgroundAliases =>
      'background keep alive الخلفية البقاء متصلا خدمة';

  @override
  String get discoverNotifyServersAliases =>
      'monitor saved servers wi-fi wifi مراقبة الخوادم المحفوظة انتباه واي فاي فحص في الخلفية';

  @override
  String get discoverAppearanceModeAliases =>
      'light dark mode system فاتح داكن الوضع النظام ليلي';

  @override
  String get discoverLanguageAliases =>
      'language arabic english locale اللغة العربية الإنجليزية ترجمة';

  @override
  String get discoverThemeAliases => 'theme colors palette السمة ألوان لوحة';

  @override
  String get discoverSpentAliases =>
      'spent cost tokens usage المصروف التكلفة الرموز الاستخدام إحصاءات';

  @override
  String get discoverRemainingAliases =>
      'plans subscriptions remaining quota limit provider plan left reset الخطط الاشتراكات المتبقي الحصة الحد المزود الخطة التجدد';

  @override
  String get discoverBudgetAliases =>
      'budget usd token ميزانية ميزانيات دولار رموز حد الإنفاق';

  @override
  String get discoverQuotaMonitorAliases =>
      'usage alerts quota monitoring threshold alert تنبيهات الاستخدام مراقبة الحصص حد تنبيه منخفض ساعات الهدوء';

  @override
  String get discoverCommandsAliases =>
      'commands slash أوامر أوامر الخادم تشغيل';

  @override
  String get discoverToolsAliases =>
      'tools capabilities inventory أدوات الأدوات والإمكانات';

  @override
  String get discoverSkillsAliases => 'skills skill مهارات مهارة تعليمات';

  @override
  String get discoverReferencesAliases =>
      'references docs sources مراجع مرجع مستندات مصادر سياق';

  @override
  String get discoverRunningNowAliases =>
      'running processes termux يعمل الآن عمليات خدمات إيقاف على هذا الهاتف';

  @override
  String get discoverStorageAliases =>
      'storage disk space termux التخزين على هذا الهاتف مساحة القرص تنظيف';

  @override
  String get discoverMonitorAliases =>
      'monitor attention انتباه الخوادم المحفوظة مراقبة خوادم أخرى بانتظارك';

  @override
  String get discoverConnectionHelpAliases =>
      'connection help troubleshooting مساعدة الاتصال تعذر الاتصال استكشاف الأخطاء الشبكة';

  @override
  String gestureEquivFileRowActions(String name) {
    return 'إجراءات $name';
  }

  @override
  String get gestureEquivShortcutFindMatch =>
      'التطابق التالي / السابق أثناء البحث في المحادثة';

  @override
  String get gestureEquivShortcutPromptHistory =>
      'الطلب الأقدم / الأحدث، والمؤشر في بداية مربع الرسالة أو نهايته';

  @override
  String get emptyTeachChangesTitle => 'لا توجد تغييرات بعد';

  @override
  String get emptyTeachChangesMessage =>
      'تظهر هنا التعديلات التي يجريها الوكيل لتراجعها.';

  @override
  String get emptyTeachWorktreesMessage =>
      'شجرة العمل نسخة منفصلة من هذا المشروع على فرع خاص بها، فلا تختلط الأعمال المتوازية. تظهر هنا أشجار عمل هذا المشروع.';

  @override
  String get emptyTeachAllowedMessage =>
      'عندما تختار «السماح دائمًا» في طلب موافقة ضمن هذا المشروع، يظهر هنا لتتمكن من التراجع عنه.';

  @override
  String get emptyTeachTeamRunsMessage =>
      'اكتب ما تحتاج إليه، فيقسّمه الفريق إلى خطوات ويعرض تقدّمه هنا.';

  @override
  String get emptyTeachSkillsMessage =>
      'المهارات تعليمات قابلة لإعادة الاستخدام يتبعها الوكيل. تظهر هنا مهارات هذا المشروع وهذا الخادم.';

  @override
  String get emptyTeachToolsMessage =>
      'تظهر هنا الأدوات التي يستطيع الوكيل استدعاءها مع هذا النموذج. لا أدوات لهذا النموذج.';

  @override
  String get capabilityScreenTitle => 'المتاح على هذا الخادم';

  @override
  String get capabilityScreenAliases =>
      'available supported missing feature capabilities المتاح مدعوم غير متاح ميزة مفقودة مخفية لماذا إمكانات الخادم';

  @override
  String capabilityScreenIntro(String server) {
    return 'يحدد $server ما يظهر في هذا التطبيق. ما لا يستطيعه يُحذف من القوائم وعلامات التبويب بدل عرضه معطلا.';
  }

  @override
  String get capabilityAllAvailable => 'يدعم هذا الخادم كل ما يقدمه التطبيق.';

  @override
  String get capabilityFiles => 'الملفات';

  @override
  String get capabilityFilesDetail => 'تصفح ملفات المشروع وابحث فيها وعاينها';

  @override
  String get capabilityChanges => 'التغييرات';

  @override
  String get capabilityChangesDetail => 'راجع ما عدّله الوكيل';

  @override
  String get capabilityTerminal => 'الطرفية';

  @override
  String get capabilityTerminalDetail => 'شغّل أوامر داخل المشروع';

  @override
  String get capabilityShell => 'الصدفة الافتراضية';

  @override
  String get capabilityShellDetail =>
      'اختر الصدفة التي تستخدمها الأوامر والطرفيات';

  @override
  String get capabilityAttachments => 'المرفقات';

  @override
  String get capabilityAttachmentsDetail => 'أرسل ملفات وصورا مع الطلب';

  @override
  String get capabilitySubagents => 'التفويض إلى وكيل فرعي';

  @override
  String get capabilitySubagentsDetail => 'اذكر وكيلا بعلامة @ في الطلب';

  @override
  String get capabilityCompact => 'الضغط';

  @override
  String get capabilityCompactDetail => 'لخّص محادثة طويلة لتحرير السياق';

  @override
  String get capabilityShare => 'المشاركة';

  @override
  String get capabilityShareDetail => 'انشر رابطا لمحادثة';

  @override
  String get capabilityFork => 'التفريع';

  @override
  String get capabilityForkDetail => 'فرّع محادثة من رسالة سابقة';

  @override
  String get capabilityRevert => 'التراجع';

  @override
  String get capabilityRevertDetail => 'تراجع عن طلب وعن التعديلات التي أجراها';

  @override
  String get capabilityArchive => 'الأرشفة';

  @override
  String get capabilityArchiveDetail => 'ضع المحادثات المنتهية جانبا دون حذفها';

  @override
  String get capabilityTodos => 'المهام';

  @override
  String get capabilityTodosDetail => 'اطلع على قائمة مهام الوكيل للمحادثة';

  @override
  String get capabilityNotes => 'ملاحظة للوكيل';

  @override
  String get capabilityNotesDetail => 'احتفظ بتعليمات دائمة مع المحادثة';

  @override
  String get capabilityImportExport => 'الاستيراد والتصدير';

  @override
  String get capabilityImportExportDetail => 'انقل محادثة بين الخوادم في ملف';

  @override
  String get capabilitySearchAll => 'كل المحادثات';

  @override
  String get capabilitySearchAllDetail => 'ابحث في المحادثات عبر كل المشاريع';

  @override
  String get capabilityAlwaysAllow => 'الإجراءات المسموح بها دائما';

  @override
  String get capabilityAlwaysAllowDetail =>
      'تذكّر موافقة حتى لا يُسأل عنها مجددا';

  @override
  String get capabilityModels => 'النماذج والمزودون';

  @override
  String get capabilityModelsDetail =>
      'تصفح النماذج وسجّل الدخول إلى المزودين من التطبيق';

  @override
  String get capabilitySkills => 'المهارات والأوامر';

  @override
  String get capabilitySkillsDetail => 'اعرض مهارات الخادم وأوامره ومراجعه';

  @override
  String get capabilityMcp => 'MCP';

  @override
  String get capabilityMcpDetail => 'اعرض خوادم MCP واتصل بها';

  @override
  String get capabilityPlugins => 'الإضافات';

  @override
  String get capabilityPluginsDetail => 'اعرض الإضافات المثبتة على الخادم';

  @override
  String get capabilityCloud => 'البيئات السحابية';

  @override
  String get capabilityCloudDetail => 'شغّل مشروعا في بيئة مُدارة';

  @override
  String get capabilityProjects => 'المشاريع';

  @override
  String get capabilityProjectsDetail => 'بدّل بين المشاريع وافحص صحة المشروع';

  @override
  String get capabilityWorktrees => 'أشجار العمل';

  @override
  String get capabilityWorktreesDetail => 'امنح المهمة فرعا معزولا خاصا بها';

  @override
  String get capabilityUsage => 'الاستخدام';

  @override
  String get capabilityUsageDetail => 'اطلع على تكلفة المحادثات';

  @override
  String get capabilityOfflineQueue => 'الإرسال لاحقا';

  @override
  String get capabilityOfflineQueueDetail =>
      'ضع الطلب في قائمة الانتظار دون اتصال وأرسله عند عودة الاتصال';

  @override
  String get capabilityContinueOnComputer => 'المتابعة على الحاسوب';

  @override
  String get capabilityContinueOnComputerDetail =>
      'احصل على أمر يعيد فتح المحادثة على مكتبك';

  @override
  String get capabilityServerUpdates => 'تحديثات الخادم';

  @override
  String get capabilityServerUpdatesDetail => 'حدّث الخادم من التطبيق';

  @override
  String get capabilityBackgroundNotifications => 'الإشعارات في الخلفية';

  @override
  String get capabilityBackgroundNotificationsDetail =>
      'تلقَّ إشعارا عند انتهاء العمل أو حاجته إليك والتطبيق مغلق';

  @override
  String get capabilityOnThisPhone => 'على هذا الهاتف';

  @override
  String get capabilityOnThisPhoneDetail => 'شغّل خادم الوكيل على هذا الجهاز';

  @override
  String get capabilityVoice => 'الصوت';

  @override
  String get capabilityVoiceDetail =>
      'أملِ الطلبات بنماذج كلام تعمل على الجهاز';

  @override
  String get discoverShowTipsAgain => 'إظهار التلميحات مجددا';

  @override
  String get discoverShowTipsSubtitle =>
      'ستظهر التلميحات التي تُعرض مرة واحدة من جديد في وقتها';

  @override
  String get discoverShowTipsAliases =>
      'tips hints nudges تلميحات نصائح مساعدة إعادة تعيين إظهار مجددا';

  @override
  String get discoverShowTipsDone => 'ستظهر التلميحات مجددا.';

  @override
  String nudgeApprovals(String action) {
    return 'طُلب «$action» 3 مرات: يمكن لهذه المحادثة الموافقة على الطلبات نيابةً عنك.';
  }

  @override
  String get nudgeReviewChanges =>
      'غيّر هذا التشغيل ملفات: راجع ما تغيّر قبل أن تتابع.';

  @override
  String get nudgeLeave =>
      'يمكنك المغادرة: سيخبرك هذا الهاتف عند انتهاء التشغيل.';

  @override
  String nudgeCompact(String percent) {
    return 'السياق ممتلئ بنسبة $percent%: اختصره لتتابع.';
  }

  @override
  String get nudgeDismiss => 'إخفاء التلميح';

  @override
  String get firstRunWhereQuestion => 'أين يجب أن يعمل وكيلك؟';

  @override
  String get firstRunOnComputer => 'على حاسوبي';

  @override
  String get firstRunJustShowMe => 'أرني فقط';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => 'على حاسوبك، شغّل:';

  @override
  String get firstRunPairingNextScan =>
      'سيعرض رمزًا. امسحه، أو انسخه والصقه هنا.';

  @override
  String get firstRunPairingNextPaste => 'ثم الصق الرمز الذي يعرضه.';

  @override
  String get firstRunNotSameNetwork => 'لست على الشبكة نفسها؟';

  @override
  String get firstRunShowCommands => 'عرض الأوامر';

  @override
  String get firstRunCommandsPaseoNetwork =>
      'للوصول إليه من هذا الهاتف عبر شبكتك الخاصة، استمع على ذلك العنوان وعيّن كلمة مرور:';

  @override
  String get firstRunCommandsCodexToken => 'أنشئ رمز الاتصال قبل تشغيله:';

  @override
  String get firstRunCommandsCodexUsb => 'يصل إليه هاتف موصول بكابل USB عبر:';

  @override
  String get firstRunNotifyTitle => 'هل نُعلمك عندما يجهز الرد؟';

  @override
  String get firstRunNotifyBody =>
      'غادر التطبيق بينما يعمل الوكيل. يصلك إشعار عندما ينتهي أو يحتاج إليك. يعرض Android إشعارًا صغيرًا دائمًا ما دام الاتصال قائمًا.';

  @override
  String get firstRunNotifyAccept => 'أشعِرني';

  @override
  String get firstRunNotifyDecline => 'ليس الآن';

  @override
  String get localAgentTitle => 'Claude Code على هذا الهاتف';

  @override
  String get localAgentOfferBody =>
      'شغّل Claude Code هنا من دون حاسوب. يثبّت التطبيق Node.js وخدمة Paseo وClaude Code داخل Ubuntu الذي يديره أصلًا، ويصل إليها من هذا الهاتف فقط.';

  @override
  String get localAgentOfferSize =>
      'نحو 60 م.ب لـ Node.js إضافةً إلى الحزم؛ ونحو 1 غ.ب بعد التثبيت. يلزم 2 غ.ب خالية.';

  @override
  String get localAgentOfferWarning =>
      'قد يوقف أندرويد Termux في الخلفية. إعدادات البطارية التي تُبقي خادم OpenCode يعمل تُبقي هذا يعمل أيضًا.';

  @override
  String get localAgentSetUp => 'إعداد Claude Code';

  @override
  String get localAgentNotNow => 'ليس الآن';

  @override
  String get localAgentNeedsUbuntuBody =>
      'يعمل Claude Code في «على هذا الهاتف». أكمل إعداد «على هذا الهاتف» أولًا ثم عد إلى هنا.';

  @override
  String get localAgentOpenSetup => 'فتح إعداد الهاتف';

  @override
  String get localAgentNeedsTermuxBody =>
      'يحتاج Claude Code إلى Termux حاليًا؛ «على هذا الهاتف» لا يشغّله بعد. أعدّ Termux لاستخدامه هنا.';

  @override
  String get localAgentSetUpWithTermux => 'الإعداد عبر Termux';

  @override
  String get localAgentStepNode => 'Node.js';

  @override
  String get localAgentStepPaseo => 'خدمة Paseo';

  @override
  String get localAgentStepClaude => 'Claude Code';

  @override
  String get localAgentStepSignIn => 'تسجيل الدخول إلى Claude';

  @override
  String get localAgentStepStart => 'التشغيل على هذا الهاتف';

  @override
  String get localAgentInstalling => 'جارٍ إعداد Claude Code';

  @override
  String get localAgentLeaveNote =>
      'يمكنك مغادرة هذه الشاشة. يستمر التثبيت في Termux.';

  @override
  String get localAgentSignInBody =>
      'يفتح Termux ويعرض Claude Code رابطًا. وافق عليه في متصفحك، ثم الصق الرمز في Termux وعد إلى هنا. لا يرى هذا التطبيق تسجيل دخولك إلى Claude ولا يحفظه.';

  @override
  String get localAgentSignInAlready => 'سجّلت الدخول بالفعل';

  @override
  String localAgentSignInOpenFailed(String command) {
    return 'تعذّر فتح Termux. افتح Termux بنفسك وشغّل: $command';
  }

  @override
  String get localAgentSignInMissing =>
      'لم يُعثر على تسجيل دخول إلى Claude بعد.';

  @override
  String get localAgentReadyTitle => 'يعمل Claude Code على هذا الهاتف';

  @override
  String get localAgentReadyBody =>
      'يستمع على هذا الهاتف فقط (127.0.0.1)، خلف كلمة مرور يحتفظ بها هذا التطبيق.';

  @override
  String localAgentVersions(String claude, String paseo, String node) {
    return 'Claude Code $claude · Paseo $paseo · Node.js $node';
  }

  @override
  String get localAgentInstalledTitle => 'Claude Code مثبّت ومتوقف';

  @override
  String get localAgentKilled =>
      'أوقف أندرويد Claude Code أثناء غياب التطبيق. لم يُفقد شيء.';

  @override
  String get localAgentFailedTitle => 'توقف إعداد Claude Code';

  @override
  String localAgentFailedNoSpace(String detail) {
    return 'لا توجد مساحة كافية على هذا الهاتف. $detail أفرغ بعض المساحة ثم حاول مجددًا.';
  }

  @override
  String get localAgentFailedDownload =>
      'تعذّر تنزيل Node.js. تحقق من الشبكة ثم حاول مجددًا.';

  @override
  String get localAgentFailedChecksum =>
      'لم يطابق تنزيل Node.js بصمته المثبّتة، فلم يُثبَّت شيء. حاول مجددًا؛ وإن تكرر ذلك فهناك ما يغيّر الملف على الشبكة.';

  @override
  String get localAgentFailedNativeBuild =>
      'تحتاج إحدى الحزم إلى وحدة أصلية لا يتوفر لها بناء جاهز لهذا الهاتف. لم يُبنَ شيء؛ والمخرجات أدناه تسمّيها.';

  @override
  String get localAgentFailedPackages =>
      'تعذّر تثبيت الحزم. تحقق من الشبكة ثم حاول مجددًا.';

  @override
  String get localAgentFailedPortInUse =>
      'المنفذ 6767 على هذا الهاتف يستخدمه برنامج آخر. أوقف ذلك البرنامج ثم حاول مجددًا.';

  @override
  String get localAgentFailedTimeout =>
      'لم يستجب Claude Code خلال دقيقتين. تعرض المخرجات أدناه ما طبعه.';

  @override
  String get localAgentFailedInterrupted =>
      'أوقف أندرويد الخطوة قبل اكتمالها. حاول مجددًا؛ وستُستكمل من حيث توقفت.';

  @override
  String get localAgentFailedUnsupported => 'يحتاج Claude Code إلى هاتف 64 بت.';

  @override
  String get localAgentFailedDaemon =>
      'توقف Claude Code أو لا يستجيب. شغّله من جديد.';

  @override
  String localAgentFailedReason(String detail) {
    return 'توقف: $detail';
  }

  @override
  String get localAgentProjectTitle => 'اختر مجلد مشروع';

  @override
  String get localAgentProjectBody =>
      'يعمل Claude Code داخل مجلد مشروع واحد على هذا الهاتف.';

  @override
  String get localAgentProjectPathLabel => 'أو اكتب مسارًا داخل Ubuntu';

  @override
  String get localAgentProjectPathInvalid =>
      'أدخل مسارًا كاملًا، مثل ‎/root/projects/my-app.';

  @override
  String localAgentConnectFailed(String detail) {
    return 'تعذّر الاتصال بـ Claude Code على هذا الهاتف. $detail';
  }

  @override
  String localAgentCardActionFailed(String detail) {
    return 'لم ينجح ذلك. $detail';
  }

  @override
  String get localAgentRestartTitle =>
      'إعادة تشغيل Claude Code على هذا الهاتف؟';

  @override
  String get localAgentRestartBody =>
      'يتوقف Claude Code لحظات ويُقاطَع ما يفعله الآن. تبقى محادثاتك على الهاتف.';

  @override
  String get localAgentStopTitle => 'إيقاف Claude Code على هذا الهاتف؟';

  @override
  String get localAgentStopBody =>
      'يُقاطَع كل ما يفعله Claude Code على هذا الهاتف، وينقطع اتصال هذا التطبيق به. تبقى مشاريعك ومحادثاتك وتسجيل دخولك إلى Claude؛ شغّله من جديد للمتابعة.';

  @override
  String get localAgentRemove => 'إزالة من هذا الهاتف';

  @override
  String get localAgentRemoveTitle => 'إزالة Claude Code من هذا الهاتف؟';

  @override
  String get localAgentRemoveBody =>
      'يوقفه ويحذف Node.js وPaseo وClaude Code من Ubuntu، نحو 1 غ.ب. تبقى مشاريعك وتسجيل دخولك إلى Claude.';

  @override
  String get localAgentRemoveKeep => 'إبقاؤه';

  @override
  String get localAgentMore => 'إجراءات أخرى لـ Claude Code';

  @override
  String get firstRunAgentsSideBySide =>
      'تعمل جنبًا إلى جنب على الحاسوب نفسه. ابدأ بواحد، وأضف البقية في أي وقت من اسم الخادم في الأعلى.';

  @override
  String get firstRunPaseoTitle => 'الوكلاء عبر Paseo';

  @override
  String get agentErrorConnectionDropped => 'انقطع الاتصال بالنموذج.';

  @override
  String get agentErrorConnectionDroppedHint =>
      'تحقّق من اتصال الإنترنت على الجهاز الذي يشغّل OpenCode. إذا توقف الرد، فتحقّق من المحادثة قبل الإرسال مجددًا.';

  @override
  String get agentErrorTimedOut => 'استغرق النموذج وقتًا طويلًا للرد.';

  @override
  String get agentErrorTimedOutHint =>
      'إذا توقف الرد، فجرّب طلبًا أصغر أو نموذجًا آخر.';

  @override
  String get agentErrorProviderUnreachable =>
      'تعذّر على الخادم الوصول إلى مزوّد النموذج.';

  @override
  String get agentErrorProviderUnreachableHint =>
      'تحقق من اتصال الإنترنت على الجهاز الذي يشغّل OpenCode ثم حاول مجددًا.';

  @override
  String get agentErrorRequestTooLarge =>
      'كان هذا الطلب أكبر من أن يُرسل إلى النموذج.';

  @override
  String get agentErrorRequestTooLargeHint =>
      'أرسل مرفقات أقل أو أصغر، أو اضغط المحادثة، ثم حاول مجددًا.';

  @override
  String get agentErrorRateLimited => 'يحدّ مزوّد النموذج من سرعة الإرسال.';

  @override
  String get agentErrorRateLimitedHint =>
      'انتظر قليلًا. إذا توقف الرد، فحاول مجددًا أو اختر نموذجًا آخر.';

  @override
  String get agentErrorProviderBusy => 'مزوّد النموذج مثقل بالطلبات الآن.';

  @override
  String get agentErrorProviderBusyHint =>
      'إذا توقف الرد، فحاول مجددًا لاحقًا أو اختر نموذجًا آخر.';

  @override
  String get agentErrorOutOfCredit => 'نفد رصيد حساب المزوّد أو حصته.';

  @override
  String get agentErrorOutOfCreditHint =>
      'اشحن الحساب، أو انتقل إلى مزوّد أو نموذج آخر.';

  @override
  String get agentErrorUsageLimit =>
      'بلغ هذا النموذج حدّ الاستخدام في الوقت الحالي.';

  @override
  String get agentErrorUsageLimitHint =>
      'اختر نموذجًا آخر لتتابع، أو تابع بعد أن يُعاد ضبط الحد.';

  @override
  String get agentErrorServerDiskFull =>
      'نفدت مساحة القرص على الجهاز الذي يشغّل OpenCode.';

  @override
  String get agentErrorServerDiskFullHint =>
      'حرّر بعض المساحة هناك ثم حاول مجددًا.';

  @override
  String get agentErrorRecovered => 'تابع الوكيل عمله بعد ذلك.';

  @override
  String chatUiRetryingSoon(String attempt) {
    return 'إعادة المحاولة$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return 'إعادة المحاولة$attempt خلال $time';
  }

  @override
  String get chatUiCompactAgain => 'اضغط المحادثة مجددًا';

  @override
  String get chatUiCompactionFailedHint =>
      'ما زالت المحادثة أطول مما يسمح به النموذج، لذا ستُعاد المحاولة في الدور التالي.';

  @override
  String get chatStripContextPending => 'سياق قيد الانتظار';

  @override
  String get chatStripAutoApprove => 'موافقة تلقائية';

  @override
  String get chatChipAutoApproveOn => 'الموافقة التلقائية مفعّلة';

  @override
  String get chatStripAutoApprovePaused => 'الموافقة التلقائية متوقفة';

  @override
  String get approvalModeMenuLabel => 'وضع الموافقة';

  @override
  String get approvalModeAskTitle => 'اسأل أولًا';

  @override
  String get approvalModeAutoTitle => 'موافقة تلقائية هنا';

  @override
  String get approvalModeAutoDetail => 'يُسمح مرة واحدة فور وصولها.';

  @override
  String get approvalModeEverythingTitle => 'وافق على كل شيء';

  @override
  String get approvalModeEverythingDetail => 'كل المحادثات على هذا الخادم.';

  @override
  String approvalModeEverythingAgentDetail(String agent) {
    return 'كل محادثات $agent على هذا الهاتف.';
  }

  @override
  String get approvalModeSettings => 'إعدادات الموافقة…';

  @override
  String get approvalModeConfirmEverythingTitle => 'الموافقة على كل شيء؟';

  @override
  String get approvalModeConfirmEverythingAction => 'وافق على كل شيء';

  @override
  String get approvalModeNowAsk => 'هذه المحادثة تسأل أولًا.';

  @override
  String get approvalModeNowAuto => 'هذه المحادثة توافق تلقائيًا.';

  @override
  String get approvalModeNowEverything => 'الموافقة على كل شيء على هذا الخادم.';

  @override
  String get approvalModeChange => 'تغيير وضع الموافقة';

  @override
  String get chatStripBackground => 'في الخلفية';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return 'محتويات $name، قُرئت على الهاتف: قيم ونص فقط، دون تنسيق أو مخططات أو صيغ.';
  }

  @override
  String get chatAttachmentOfficeTruncated =>
      'الملف أكبر مما يتسع له الطلب، لذا اقتُطع أدناه.';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return 'تعذّرت قراءة $name. قد يكون محميًا بكلمة مرور أو تالفًا أو بصيغة Excel أو Word القديمة؛ احفظه بصيغة ‎.xlsx أو ‎.docx أو CSV ثم حاول مجددًا.';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return 'لا يحتوي $name على ما يمكن إرفاقه.';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return 'أُرفق $name كنص.';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets ورقة',
      few: '$sheets أوراق',
      two: 'ورقتان',
      one: 'ورقة واحدة',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows صفًا',
      few: '$rows صفوف',
      two: 'صفان',
      one: 'صف واحد',
    );
    return 'أُرفق $name كنص · $_temp0، $_temp1';
  }

  @override
  String get localAgentUpdate => 'تحديث Claude Code';

  @override
  String get localAgentUpdateAvailable =>
      'يضيف التحديث أحدث نماذج Claude، مثل Opus 5.5.';

  @override
  String get localAgentUpdateNow => 'تحديث';

  @override
  String get modelNewBadge => 'جديد';

  @override
  String get modelEffortNone => 'بلا تفكير';

  @override
  String get modelEffortMinimal => 'أدنى';

  @override
  String get modelEffortLow => 'منخفض';

  @override
  String get modelEffortMedium => 'متوسط';

  @override
  String get modelEffortHigh => 'مرتفع';

  @override
  String get modelEffortExtraHigh => 'مرتفع جدًا';

  @override
  String get modelEffortMax => 'أقصى';

  @override
  String get setupStoppedByRestart =>
      'أُعيد تشغيل الهاتف فتوقف الخادم المحلي. شغّله مجددًا عند الحاجة.';

  @override
  String get phoneServerStoppedTitle => 'الخادم على هذا الهاتف متوقف';

  @override
  String get phoneServerStoppedBody =>
      'يتوقف عند إعادة تشغيل الهاتف أو عندما يغلق Android تطبيق Termux لتوفير البطارية. محادثاتك محفوظة؛ شغّله مجددًا للمتابعة.';

  @override
  String get phoneServerStartAndConnect => 'تشغيل واتصال';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قيد العمل',
      one: 'محادثة قيد العمل',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return 'اسأل $agent…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض $countString سطرًا سابقًا',
      one: 'عرض سطر سابق',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'فتح كل الأسطر ($countString)';
  }

  @override
  String get setupProgressViewOverallLabel => 'تقدّم الإعداد';

  @override
  String get setupProgressViewGettingStarted => 'جارٍ البدء…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'بقيت $minutes دقيقة تقريبًا',
      few: 'بقيت $minutes دقائق تقريبًا',
      two: 'بقيت دقيقتان تقريبًا',
      one: 'بقيت دقيقة تقريبًا',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => 'أقل من دقيقة';

  @override
  String get setupProgressViewDone => 'اكتمل كل شيء';

  @override
  String get setupProgressViewFailedTitle => 'لم يكتمل الإعداد';

  @override
  String get setupProgressViewInterrupted => 'توقّف الإعداد. ما اكتمل محفوظ.';

  @override
  String get setupProgressViewCancelled =>
      'أُوقف الإعداد. ما اكتمل يبقى مثبّتًا.';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done من $total';
  }

  @override
  String setupProgressViewPercent(int percent) {
    return '$percent٪';
  }

  @override
  String setupProgressViewStageMeasured(String stage, String measured) {
    return '$stage · $measured';
  }

  @override
  String get setupProgressViewChecking => 'جارٍ الفحص';

  @override
  String get setupProgressViewStarting => 'جارٍ البدء';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return 'توقّف أثناء: $stage';
  }

  @override
  String get setupProgressViewFailedUnknown =>
      'حدث خطأ. اعرض التفاصيل لرؤية السجل.';

  @override
  String get setupProgressViewNoInternet =>
      'لا يوجد اتصال بالإنترنت — تابع عند عودة الاتصال';

  @override
  String get setupProgressViewContinue => 'متابعة الإعداد';

  @override
  String get setupProgressViewCancel => 'إلغاء';

  @override
  String get setupProgressViewNoLog => 'لا يوجد سجل بعد.';

  @override
  String get phoneSetupProgressTitle => 'جارٍ إعداد OpenCode على هذا الهاتف';

  @override
  String get phoneSetupProgressStopTitle => 'إيقاف الإعداد؟';

  @override
  String get phoneSetupProgressStopMessage => 'ما اكتمل يبقى مثبّتًا.';

  @override
  String get phoneSetupProgressStopConfirm => 'إيقاف الإعداد';

  @override
  String get phoneSetupProgressKeepGoing => 'متابعة';

  @override
  String get builtinServerChooseRuntime => 'أي إصدار من OpenCode';

  @override
  String get builtinServerStopped => 'متوقف.';

  @override
  String builtinServerStartFailed(String reason) {
    return 'لم يستجب OpenCode: $reason. افتح السجل لمعرفة السبب.';
  }

  @override
  String get builtinServerExited => 'توقف الخادم';

  @override
  String builtinServerTimedOut(int seconds) {
    return 'لا استجابة خلال $seconds ثانية';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return 'تعذّر الاتصال: $reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return 'هذا الهاتف، مدمج ($runtime)';
  }

  @override
  String get phoneSetupProfileName => 'هذا الهاتف';

  @override
  String get phoneSetupLinuxTitle => 'قاعدة لينكس';

  @override
  String get phoneSetupLinuxWhy => 'كل ما عداها يعمل داخلها.';

  @override
  String get phoneSetupEssentialsTitle => 'Git وSSH والشهادات';

  @override
  String get phoneSetupEssentialsShort => 'Git وSSH';

  @override
  String get phoneSetupEssentialsWhy =>
      'يستخدم الوكلاء Git وSSH للعمل على مشاريعك.';

  @override
  String get phoneSetupNodeWhy => 'يعمل OpenCode على Node.js.';

  @override
  String get phoneSetupOpenCodeWhy => 'وكيل البرمجة نفسه.';

  @override
  String get phoneSetupStartTitle => 'تشغيل OpenCode';

  @override
  String get phoneSetupStartWhy => 'ينتهي الإعداد وOpenCode يعمل.';

  @override
  String get phoneSetupStageDownloadingLinux => 'جارٍ تنزيل قاعدة لينكس';

  @override
  String get phoneSetupStageUnpackingLinux => 'جارٍ فك قاعدة لينكس';

  @override
  String get phoneSetupStageStarting => 'جارٍ تشغيل OpenCode';

  @override
  String get phoneSetupNotificationChannel => 'إعداد الهاتف';

  @override
  String get phoneSetupNotificationTitle =>
      'جارٍ إعداد OpenCode على هذا الهاتف';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return 'اكتمل $percent٪';
  }

  @override
  String get phoneSetupNotificationDone => 'OpenCode جاهز على هذا الهاتف';

  @override
  String get phoneSetupNotificationStopped =>
      'توقف الإعداد. افتح التطبيق للمتابعة.';

  @override
  String get phoneSetupErrorNoInternet =>
      'لا يوجد اتصال بالإنترنت. تابع عندما يعود الاتصال.';

  @override
  String phoneSetupErrorOffline(String name) {
    return 'تعذّر تنزيل $name: لا يوجد اتصال بالإنترنت';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return 'تعذّر تثبيت $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return 'تلف تنزيل $name. تابع لتنزيله من جديد.';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'نُزّل OpenCode، لكن التنزيل لم يتضمّن برنامجه. تابع لتنزيله مجددًا.';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'نُزّل OpenCode، لكن برنامجه لا يعمل على هذا الهاتف. تعرض التفاصيل ما أبلغ عنه.';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'ثُبّت OpenCode، لكنه لم يبدأ. تابع للمحاولة مجددًا؛ تعرض التفاصيل ما أبلغ عنه.';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return 'لا توجد مساحة كافية لتثبيت $name. أفرغ بعض مساحة التخزين، ثم تابع الإعداد.';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'تم تثبيت OpenCode لكنه لا يستجيب. تابع الإعداد للمحاولة مجددًا. تعرض التفاصيل سبب التوقف.';

  @override
  String get builtinServerLogTitle => 'سجل الخادم';

  @override
  String get phoneSetupReadyTitle => 'OpenCode جاهز';

  @override
  String get phoneSetupReadyNameTitle => 'سمِّ مشروعك الأول';

  @override
  String get phoneSetupReadyNameLabel => 'اسم المشروع';

  @override
  String get phoneSetupReadyNameHelp => 'أحرف لاتينية وأرقام و - _ .';

  @override
  String get phoneSetupReadyCreating => 'جارٍ إنشاء المشروع';

  @override
  String get phoneSetupReadyCreateOpen => 'إنشاء وفتح';

  @override
  String get phoneSetupReadyOpenFolderInstead => 'فتح مجلد بدلًا من ذلك';

  @override
  String get phoneSetupReadyNameEmpty => 'اكتب اسمًا.';

  @override
  String get phoneSetupReadyNameOneFolder =>
      'استخدم اسمًا واحدًا بلا شرطات مائلة.';

  @override
  String get phoneSetupReadyNameInvalid =>
      'استخدم أحرفًا لاتينية وأرقامًا و - _ . وابدأ بحرف أو رقم (حتى 64).';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return 'تعذّر إنشاء المشروع: $reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return 'تعذّر فتح المشروع: $reason';
  }

  @override
  String get phoneServerCardTitle => 'على هذا الهاتف';

  @override
  String get phoneServerCardRunning => 'يعمل';

  @override
  String get phoneServerCardStopped => 'متوقف';

  @override
  String get phoneServerCardStarting => 'جارٍ التشغيل';

  @override
  String get phoneServerCardStopping => 'جارٍ الإيقاف';

  @override
  String get phoneServerCardRemoving => 'جارٍ الإزالة';

  @override
  String get phoneServerCardChecking => 'جارٍ التحقق';

  @override
  String get phoneServerCardNotSetUp => 'غير مُعَدّ';

  @override
  String get phoneServerCardSettingUp => 'جارٍ الإعداد';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => 'إعداد';

  @override
  String get phoneServerCardShowProgress => 'عرض التقدم';

  @override
  String get phoneServerCardContinueSetup => 'متابعة الإعداد';

  @override
  String get phoneServerCardLogTitle => 'السجل';

  @override
  String get phoneServerCardLogEmpty => 'لا شيء في السجل بعد.';

  @override
  String get phoneServerCardMore => 'المزيد';

  @override
  String get phoneServerCardAddTools => 'إضافة أدوات (Python وAI Team…)';

  @override
  String get phoneServerCardUpdate => 'تحديث OpenCode';

  @override
  String get phoneServerCardRemove => 'إزالة من هذا الهاتف…';

  @override
  String get phoneServerCardRemoveTitle => 'إزالة OpenCode من هذا الهاتف؟';

  @override
  String get serverEditorMoreOptions => 'خيارات أخرى';

  @override
  String get inAppServerStoppedTitle => 'OpenCode داخل التطبيق متوقف';

  @override
  String get inAppServerStoppedBody =>
      'يتوقف عند إغلاق التطبيق لفترة أو تحديثه. محادثاتك محفوظة؛ شغّله مجددًا للمتابعة.';

  @override
  String get inAppServerNotRespondingTitle => 'OpenCode داخل التطبيق لا يستجيب';

  @override
  String get inAppServerNotRespondingBody =>
      'إعادة تشغيله تحل هذا عادةً. محادثاتك محفوظة.';

  @override
  String get inAppServerStartFailedTitle => 'لم يبدأ OpenCode داخل التطبيق';

  @override
  String get inAppServerStartFailedBody =>
      'افتح الإعداد لترى سجل الخادم، أو حاول تشغيله مرة أخرى.';

  @override
  String get inAppServerStarting => 'جارٍ تشغيل OpenCode داخل التطبيق…';

  @override
  String get inAppServerStartingBody => 'يستغرق هذا بضع ثوانٍ.';

  @override
  String get inAppServerOpenSetup => 'فتح الإعداد';

  @override
  String get projectFolderNewProject => 'مشروع جديد';

  @override
  String get projectFolderProjectNameLabel => 'اسم المشروع';

  @override
  String get projectFolderEnterPath => 'إدخال مسار';

  @override
  String get projectFolderMissing => 'هذا المجلد غير موجود بعد.';

  @override
  String get projectFolderCreateIt => 'إنشاؤه';

  @override
  String projectFolderCreateFailed(String reason) {
    return 'تعذّر إنشاء المجلد: $reason';
  }

  @override
  String projectFolderCheckFailed(String reason) {
    return 'تعذّر التحقق من المجلد: $reason';
  }

  @override
  String get folderBrowserUp => 'إلى المجلد الأعلى';

  @override
  String folderBrowserCurrent(String path) {
    return 'المجلد الحالي: $path';
  }

  @override
  String folderBrowserOpen(String name) {
    return 'فتح $name';
  }

  @override
  String get folderBrowserGit => 'مستودع Git';

  @override
  String get folderBrowserProject => 'مشروع OpenCode';

  @override
  String folderBrowserShowInside(String name) {
    return 'عرض المجلدات داخل $name';
  }

  @override
  String get folderBrowserProjectsHere => 'مشاريعك هنا. اضغط على مشروع لفتحه.';

  @override
  String get folderBrowserHomeHere =>
      'لا يمكن أن يكون المجلد الرئيسي أو الجذر مشروعًا. افتح مجلدًا بداخله.';

  @override
  String get folderBrowserNoProjectsTitle => 'لا توجد مشاريع بعد';

  @override
  String get folderBrowserNoProjectsBody => 'ابدأ مشروعًا لإنشائه هنا.';

  @override
  String get folderBrowserEmptyTitle => 'لا توجد مجلدات هنا';

  @override
  String get folderBrowserEmptyBody =>
      'افتحه كمشروع، أو انتقل إلى المجلد الأعلى.';

  @override
  String get folderBrowserErrorTitle => 'تعذّر عرض هذا المجلد';

  @override
  String get folderBrowserErrorNotInstalled => 'لم يُعدّ «على هذا الهاتف» بعد.';

  @override
  String get folderBrowserErrorMissing => 'لم يعد موجودًا.';

  @override
  String get folderBrowserErrorDenied => 'لا يُسمح للتطبيق بقراءته.';

  @override
  String get folderBrowserErrorLinked => 'هذا رابط. أدخل مساره بدلًا من ذلك.';

  @override
  String get folderBrowserErrorFailed =>
      'حاول مرة أخرى، أو أدخل مساره بدلًا من ذلك.';

  @override
  String get folderBrowserErrorTimedOut =>
      'استغرق الرد وقتًا طويلًا. حاول مرة أخرى.';

  @override
  String get folderBrowserRetry => 'إعادة المحاولة';

  @override
  String get phoneSetupStartScreenTitle => 'على هذا الهاتف';

  @override
  String get phoneSetupStartHeadline => 'شغّل وكيل برمجة هنا مباشرة';

  @override
  String phoneSetupStartPromise(String size) {
    return 'لا حاجة إلى حاسوب أو تطبيقات أخرى. ~$size للتنزيل في المرة الأولى.';
  }

  @override
  String get phoneSetupStartPromiseNoSize =>
      'لا حاجة إلى حاسوب أو تطبيقات أخرى.';

  @override
  String phoneSetupStartSteps(String time) {
    return 'ثبّت، سمِّ مشروعًا، تحدّث · $time';
  }

  @override
  String phoneSetupStartStepsTime(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'نحو $minutes دقيقة',
      few: 'نحو $minutes دقائق',
      two: 'نحو دقيقتين',
      one: 'نحو دقيقة',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'نحو $minutes دقيقة',
      few: 'نحو $minutes دقائق',
      two: 'نحو دقيقتين',
      one: 'نحو دقيقة',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartMegabytes(String value) {
    return '$value ميغابايت';
  }

  @override
  String phoneSetupStartGigabytes(String value) {
    return '$value غيغابايت';
  }

  @override
  String get phoneSetupStartSetUp => 'ابدأ الإعداد';

  @override
  String phoneSetupStartIncludes(String tools) {
    return 'يشمل $tools.';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first و$last';
  }

  @override
  String get phoneSetupStartListSeparator => '، ';

  @override
  String get phoneSetupStartCustomize => 'تخصيص';

  @override
  String get phoneSetupStartOtherWays => 'طرق أخرى';

  @override
  String get phoneSetupStartUseTermux => 'استخدام Termux بدلًا من ذلك';

  @override
  String get phoneSetupStartByAddress => 'الاتصال بحاسوب عبر عنوانه';

  @override
  String get phoneSetupStartSetUpHere => 'إعداده داخل هذا التطبيق بدلًا من ذلك';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return 'اكتمل الإعداد بنسبة $percent٪';
  }

  @override
  String get phoneSetupStartRunningBody =>
      'يستمر العمل وأنت تستخدم تطبيقات أخرى.';

  @override
  String get phoneSetupStartStoppedBody =>
      'توقّف الإعداد قبل أن يكتمل. المتابعة تبدأ من حيث توقّف.';

  @override
  String get phoneSetupStartContinue => 'متابعة الإعداد';

  @override
  String get phoneSetupStartReadyHeadline => 'OpenCode جاهز على هذا الهاتف';

  @override
  String get phoneSetupStartReadyBody => 'افتحه لبدء محادثة.';

  @override
  String get phoneSetupStartOpen => 'فتح';

  @override
  String get phoneSetupStartTermuxHeadline => 'OpenCode مُعدّ مسبقًا في Termux';

  @override
  String get phoneSetupStartTermuxBody =>
      'أعددته عبر Termux من قبل. اتصل لمواصلة استخدامه.';

  @override
  String get phoneSetupStartConnect => 'اتصال';

  @override
  String phoneSetupStartFailed(String reason) {
    return 'لم ينجح ذلك: $reason';
  }

  @override
  String get phoneSetupStartEntryDetail =>
      'شغّل وكيل برمجة هنا مباشرة. لا تحتاج إلى حاسوب.';

  @override
  String get phoneSetupStartCustomizeTitle => 'اختر ما تريد تثبيته';

  @override
  String get phoneSetupStartAddTitle => 'إضافة أدوات';

  @override
  String get phoneSetupStartInstalled => 'مثبّت';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => 'لم تختر شيئًا بعد';

  @override
  String get phoneSetupStartDone => 'تم';

  @override
  String get phoneSetupStartAdd => 'إضافة';

  @override
  String get phoneSetupStartChecking => 'جارٍ التحقق مما هو مثبّت…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline =>
      'هذا الهاتف لا يمكنه تشغيله';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return 'لينكس هذا التطبيق يعمل فقط على هاتف Arm أو Intel بمعمارية 64 بت؛ هذا الهاتف يُبلّغ عن $abi.';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline =>
      'لا تتوفّر ذاكرة كافية في هذا الهاتف';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'يحتاج OpenCode إلى هاتف بذاكرة لا تقل عن $minimumString ميغابايت؛ تتوفّر في هذا الهاتف $actualString ميغابايت. شغّله على حاسوب ووصل هذا الهاتف به.';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return 'قد يكون بطيئًا على هذا الهاتف، الذي تتوفّر فيه ذاكرة بسعة $memoryString ميغابايت.';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline => 'لا توجد مساحة كافية';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return 'حرّر نحو $size على هذا الهاتف، ثم عد لإكمال الإعداد.';
  }

  @override
  String get phoneSetupPreflightOpenStorage => 'فتح إعدادات التخزين';

  @override
  String get phoneSetupOpenWelcomeShowProgress => 'عرض التقدّم';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => 'أنشئ صفحة ويب صغيرة';

  @override
  String get chatStartPythonScript => 'اكتب سكربت بايثون…';

  @override
  String get chatStartNodeProject => 'ابدأ مشروع Node.js';

  @override
  String get chatStartReadme => 'أنشئ ملف README';

  @override
  String get chatStartExplainProject => 'اشرح هذا المشروع';

  @override
  String get chatStartWhatChanged => 'ما الذي تغيّر مؤخرًا؟';

  @override
  String get chatStartFindBug => 'اعثر على خطأ وأصلحه';

  @override
  String get chatStartAddTests => 'أضف اختبارات';

  @override
  String get chatStartListFolder => 'اعرض محتويات هذا المجلد';

  @override
  String get chatStartEmptyFolder => 'مجلد فارغ';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر',
      many: '$count عنصرًا',
      few: '$count عناصر',
      two: 'عنصران',
      one: 'عنصر واحد',
    );
    return '$_temp0';
  }

  @override
  String get chatStartGit => 'Git';

  @override
  String chatStartChangeCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تغيير',
      many: '$count تغييرًا',
      few: '$count تغييرات',
      two: 'تغييران',
      one: 'تغيير واحد',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => 'جارٍ فحص المجلد…';

  @override
  String get chatStartServerFolder => 'مجلد الخادم';

  @override
  String get chatStartTip =>
      'اكتب / للأوامر · اضغط مطولًا على رسالة لإجراءاتها';

  @override
  String get chatLoadingConversation => 'جارٍ تحميل المحادثة';

  @override
  String get chatLoadFailedTitle => 'تعذّر فتح هذه المحادثة';

  @override
  String get chatLoadFailedBody =>
      'لم يضِع شيء. حاول مرة أخرى عندما يستجيب OpenCode.';

  @override
  String get chatSendFailed => 'لم تُرسَل رسالتك';

  @override
  String get chatSendFailedKept => 'أُعيدت إلى مربع الرسالة.';

  @override
  String get chatStartSuggestionsLabel => 'طرق للبدء';

  @override
  String get perfTraceTitle => 'الأداء';

  @override
  String get perfTraceBody =>
      'كم استغرقت كل خطوة منذ فتح التطبيق: الاتصال والتحميل وكل طلب إلى الخادم. يُحفظ في الذاكرة فقط ويُمسح عند إغلاق التطبيق. يحتوي التقرير على الأسماء والأوقات فقط، ولا يحتوي أبدًا على الرسائل أو كلمات المرور.';

  @override
  String get perfTraceCopy => 'نسخ التقرير';

  @override
  String get perfTraceCopied => 'تم نسخ تقرير الأداء';

  @override
  String get perfTraceEmpty => 'لم يُقَس شيء بعد.';

  @override
  String get perfTraceSlowest => 'أبطأ الخطوات';

  @override
  String get perfTraceRecent => 'أحدث الخطوات';

  @override
  String perfTraceStatLine(int count, String p50, String p95, String max) {
    return '$count× · المعتاد $p50 · البطيء $p95 · الأطول $max';
  }

  @override
  String perfTraceFailed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'فشلت $count مرات',
      one: 'فشلت مرة واحدة',
    );
    return '$_temp0';
  }

  @override
  String perfTraceAt(String time) {
    return 'عند $time';
  }

  @override
  String perfTraceWithin(String parent) {
    return 'ضمن $parent';
  }

  @override
  String get aiteamComponentTitle => 'الفريق الذكي';

  @override
  String aiteamComponentStageDownloading(String index, String total) {
    return 'تنزيل الفريق الذكي · $index من $total';
  }

  @override
  String get aiteamComponentStagePreparing => 'تجهيز الفريق الذكي';

  @override
  String aiteamComponentAddingTitle(String names) {
    return 'إضافة $names';
  }

  @override
  String get aiteamComponentNotice =>
      'OpenCode والفريق الذكي يعملان على هذا الهاتف';

  @override
  String get aiteamComponentSectionTitle => 'الفريق الذكي على هذا الهاتف';

  @override
  String aiteamComponentOfferBody(String size) {
    return 'عدة وكلاء يتقاسمون العمل على مشروع واحد، هنا على الهاتف. التنزيل نحو $size.';
  }

  @override
  String get aiteamComponentAdd => 'إضافة الفريق الذكي';

  @override
  String aiteamComponentTurnOn(String project) {
    return 'تشغيل الفريق الذكي لـ $project';
  }

  @override
  String get aiteamComponentTurnOnBody =>
      'يعمل الفريق على فروعه الخاصة ويحتفظ بنسخة من سجل المشروع على هذا الهاتف.';

  @override
  String get aiteamComponentNoProject =>
      'افتح مشروعًا أولًا، ثم شغّل الفريق الذكي له.';

  @override
  String get aiteamComponentStageTeam => 'تجهيز الفريق';

  @override
  String aiteamComponentStageProject(String project) {
    return 'إضافة $project';
  }

  @override
  String get aiteamComponentStageStarting => 'بدء الفريق الذكي';

  @override
  String get aiteamComponentStageWaiting => 'انتظار رد الفريق الذكي';

  @override
  String get aiteamComponentTurnOnExpectation =>
      'يستغرق هذا من 5 إلى 10 دقائق في المرة الأولى. يمكنك مغادرة هذه الشاشة؛ يستمر العمل.';

  @override
  String get aiteamComponentStartExpectation =>
      'يستغرق هذا بضع دقائق. يمكنك مغادرة هذه الشاشة؛ يستمر العمل.';

  @override
  String aiteamComponentStageSoFar(String time) {
    return '$time حتى الآن';
  }

  @override
  String aiteamComponentStageTook(String time) {
    return 'استغرق $time';
  }

  @override
  String get aiteamComponentRunning => 'الفريق الذكي · يعمل';

  @override
  String get aiteamComponentStopped => 'الفريق الذكي · متوقف';

  @override
  String aiteamComponentProjects(String projects) {
    return 'يعمل على $projects';
  }

  @override
  String get aiteamComponentStart => 'بدء الفريق الذكي';

  @override
  String aiteamComponentFailed(String reason) {
    return 'تعذّر بدء الفريق الذكي: $reason';
  }

  @override
  String get aiteamComponentFailedExited => 'توقف من تلقاء نفسه';

  @override
  String get aiteamComponentFailedTimeout => 'لم يرد في الوقت المحدد';

  @override
  String get aiteamComponentShowDetails => 'عرض التفاصيل';

  @override
  String get aiteamComponentChildProcesses =>
      'يوقف أندرويد البرامج الإضافية للتطبيق حين يشغّل كثيرًا منها معًا، والفريق يشغّل عدة برامج. إذا توقف الفريق أثناء العمل، فعّل خيارات المطوّرين › تعطيل قيود العمليات الفرعية.';

  @override
  String workOpenLiveConversation(String title) {
    return 'فتح «$title»';
  }

  @override
  String get workLoadingLabel => 'جارٍ التحميل';

  @override
  String agentNotAnsweringPhone(String agent) {
    return '$agent على هذا الهاتف لا يستجيب';
  }

  @override
  String get workServerNotAnsweringPhone => 'OpenCode على هذا الهاتف لا يستجيب';

  @override
  String workServerNotAnswering(String server) {
    return '$server لا يستجيب';
  }

  @override
  String get workServerKeepsTrying => 'يواصل التطبيق المحاولة في الخلفية.';

  @override
  String get workServerRestart => 'إعادة التشغيل';

  @override
  String get connectionRestartAgent => 'إعادة تشغيل Claude Code';

  @override
  String get connectionRestartPhoneServer => 'إعادة تشغيل OpenCode';

  @override
  String get workServerRestartTitle => 'إعادة تشغيل OpenCode على هذا الهاتف؟';

  @override
  String get workServerRestartBody =>
      'سيتوقف أي دور قيد التشغيل للوكيل. تبقى محادثاتك محفوظة.';

  @override
  String workRunaway(String duration) {
    return 'OpenCode مشغول منذ $duration دون عمل ينتظره';
  }

  @override
  String workRunawayInProject(String project, String duration) {
    return 'OpenCode مشغول في $project منذ $duration دون عمل ينتظره';
  }

  @override
  String get connectStartingPhone => 'جارٍ تشغيل OpenCode على هذا الهاتف…';

  @override
  String get connectStartingBody =>
      'تبقى محادثاتك محفوظة. قد يستغرق هذا دقيقة.';

  @override
  String aiteamBringInDone(String project, String commit) {
    return 'أصبح أحدث عمل للفريق ($commit) في $project.';
  }

  @override
  String aiteamBringInDirty(String commit, String project, String files) {
    return 'لم يُدخَل عمل الفريق ($commit) إلى $project بعد: في $project تغييرات خاصة به ($files)، لذا تُرك كما هو.';
  }

  @override
  String aiteamBringInDiverged(String commit, String project) {
    return 'لم يُدخَل عمل الفريق ($commit) إلى $project: في $project إيداعات خاصة به. ادمج الاثنين باستخدام git.';
  }

  @override
  String aiteamBringInFailed(String project, String reason) {
    return 'تعذّر إدخال عمل الفريق إلى $project: $reason';
  }

  @override
  String aiteamBringInAction(String project) {
    return 'أدخل عمل الفريق إلى $project';
  }

  @override
  String get teamUiHostPhrasePhone => 'على هذا الهاتف';

  @override
  String teamUiHostPhraseComputerNamed(String name) {
    return 'على $name';
  }

  @override
  String get teamUiHostPhraseComputer => 'على حاسوبك';

  @override
  String get teamUiHostPhrasePaused => 'متوقف مؤقتًا';

  @override
  String get teamUiHostPhraseNotAnswering => 'لا يستجيب';

  @override
  String teamUiTaskSteps(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: 'اكتملت $done من $total خطوة',
      many: 'اكتملت $done من $total خطوة',
      few: 'اكتملت $done من $total خطوات',
      two: 'اكتملت $done من خطوتين',
      one: 'اكتملت $done من خطوة واحدة',
    );
    return '$_temp0';
  }

  @override
  String teamUiTaskDoneAgo(String when) {
    return 'اكتملت $when';
  }

  @override
  String teamUiTaskMergedAgo(String when) {
    return 'اكتملت · دُمجت $when';
  }

  @override
  String teamUiTaskCancelledAgo(String when) {
    return 'أُلغيت $when';
  }

  @override
  String teamUiHomeDoneMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض $count مهمة أخرى',
      many: 'عرض $count مهمة أخرى',
      few: 'عرض $count مهام أخرى',
      two: 'عرض مهمتين أخريين',
      one: 'عرض مهمة أخرى',
    );
    return '$_temp0';
  }

  @override
  String get teamUiHomeSearchClose => 'إغلاق البحث';

  @override
  String get teamUiHomeNeedsYouAnswer => 'أجب';

  @override
  String teamUiHomeNeedsYouAnnouncement(String question) {
    return 'يحتاجك: $question';
  }

  @override
  String get teamUiAgentRoleWorker => 'عامل';

  @override
  String get teamUiAgentRoleReviewer => 'مراجع';

  @override
  String get teamUiAgentRolePlanner => 'مخطِّط';

  @override
  String get teamUiAgentRoleSupervisor => 'مشرف';

  @override
  String get teamUiAgentRoleHelper => 'مساعد';

  @override
  String get teamUiAgentRoleOther => 'وكيل';

  @override
  String get teamUiRunStageWaiting => 'في الانتظار';

  @override
  String get teamUiRunStageWorking => 'قيد العمل';

  @override
  String get teamUiRunStageReviewing => 'قيد المراجعة';

  @override
  String get teamUiRunStageDone => 'مكتملة';

  @override
  String teamUiRunStageSemantics(int position, String stage) {
    return 'المرحلة $position من 4: $stage';
  }

  @override
  String get teamUiRunStepsHeading => 'الخطوات';

  @override
  String get teamUiRunDetailsUsage => 'الاستخدام';

  @override
  String get serverRowConnected => 'متصل';

  @override
  String phoneServerRowStatus(String runtime, String state) {
    return '$runtime · $state';
  }

  @override
  String get phoneServerRowRestarting => 'جارٍ إعادة التشغيل';

  @override
  String get phoneServerRowNotAnswering => 'لا يستجيب';

  @override
  String get phoneServerRowNotRunning => 'لا يعمل';

  @override
  String get phoneServerRowDetails => 'التفاصيل';

  @override
  String get localAgentRowOptional => 'اختياري';

  @override
  String get localAgentPageTitle => 'Claude Code';

  @override
  String get managedRecoveryRowTitle => 'إعادة التشغيل بعد التعطل';

  @override
  String get managedRecoveryRowDetail =>
      'حتى 3 محاولات، وفقط أثناء فتح التطبيق. لا يثبّت شيئًا ولا يحدّثه.';

  @override
  String pluginsTeamRowFound(String server) {
    return 'موجود على $server';
  }

  @override
  String get pluginsLoading => 'جارٍ تحميل الإضافات';

  @override
  String get pluginsBuiltinGroup => 'مدمجة';

  @override
  String pluginsBuiltinActive(int count) {
    return '$count نشطة';
  }

  @override
  String pluginsBuiltinFailed(int count) {
    return 'تعذّر تحميل $count';
  }

  @override
  String get pluginsStatusFailedToLoad => 'تعذّر التحميل';

  @override
  String get pluginsDetailsId => 'المعرّف';

  @override
  String get localTerminalSourcePhone => 'لينكس المدمج';

  @override
  String get localTerminalSourceServer => 'خادم OpenCode';

  @override
  String localTerminalShellName(int number) {
    return 'طرفية $number';
  }

  @override
  String get localTerminalNewShell => 'طرفية جديدة';

  @override
  String get localTerminalStopShell => 'إيقاف هذه الطرفية';

  @override
  String get localTerminalCloseShell => 'إغلاق هذه الطرفية';

  @override
  String get localTerminalStopBody => 'تتوقف البرامج التي تعمل فيها أيضًا.';

  @override
  String get localTerminalStop => 'إيقاف';

  @override
  String get localTerminalStarting => 'جارٍ بدء الطرفية';

  @override
  String get localTerminalNotSetUpTitle => 'لم يُجهَّز لينكس على هذا الهاتف';

  @override
  String get localTerminalNotSetUpBody =>
      'تعمل الطرفية داخل لينكس الذي يثبّته إعداد الهاتف.';

  @override
  String get localTerminalEndedTitle => 'انتهت الطرفية';

  @override
  String localTerminalEndedBody(int code) {
    return 'انتهت برمز الخروج $code.';
  }

  @override
  String get localTerminalRestart => 'إعادة التشغيل';

  @override
  String get localTerminalFailedTitle => 'لم تبدأ الطرفية';

  @override
  String get localTerminalFailedBody =>
      'حاول مرة أخرى. إن تكرر الفشل، فستجد السبب في «التفاصيل».';

  @override
  String get localTerminalTryAgain => 'إعادة المحاولة';

  @override
  String localTerminalCost(int perShell, int limit) {
    return 'عدد البرامج لكل طرفية: $perShell. مع تشغيل فريق الذكاء الاصطناعي، قد يوقف أندرويد برامج التطبيق إذا تجاوز عددها $limit.';
  }

  @override
  String localTerminalCostNow(int perShell, int count, int limit) {
    return 'عدد البرامج لكل طرفية: $perShell. مع تشغيل فريق الذكاء الاصطناعي، يشغّل التطبيق الآن $count، وقد يوقفها أندرويد إذا تجاوز عددها $limit.';
  }

  @override
  String get localTerminalKeysLabel => 'مفاتيح الطرفية';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => 'صفحة لأعلى';

  @override
  String get localTerminalKeyPageDown => 'صفحة لأسفل';

  @override
  String get localTerminalSemantics =>
      'الطرفية على هذا الهاتف. اضغط للكتابة، واضغط مطولًا لتحديد النص.';

  @override
  String get phoneServerTermuxTitle => 'Termux';

  @override
  String get globalSessionsLoadFailedTitle => 'تعذّر تحميل المحادثات';

  @override
  String get filesLoadFailedTitle => 'تعذّر فتح هذا المجلد';

  @override
  String get filesSymbolsFailedTitle => 'تعذّر البحث في الرموز';

  @override
  String get terminalListFailedTitle => 'تعذّر عرض الطرفيات';

  @override
  String get addServerConnectTo => 'الاتصال بـ';

  @override
  String get addServerTypeOpenCode => 'OpenCode على حاسوب';

  @override
  String get addServerTypeOpenCodeDetail => 'اقترن برمز، أو أدخل عنوانه';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail => 'Codex app-server على حاسوبك';

  @override
  String get addServerTypePaseo => 'Claude Code أو Pi';

  @override
  String get addServerTypePaseoDetail => 'عبر Paseo على حاسوبك';

  @override
  String get addServerScan => 'امسح الرمز';

  @override
  String get addServerPaste => 'الصق الرمز';

  @override
  String get addServerManual => 'أدخل العنوان بدلًا من ذلك';

  @override
  String get addServerChecking => 'جارٍ فحص الاتصال…';

  @override
  String addServerCheckingHost(String host) {
    return 'جارٍ فحص $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return 'جارٍ الاتصال بـ $host…';
  }

  @override
  String get addServerSaveAnyway => 'احفظ على أي حال';

  @override
  String get effectsSection => 'المؤثرات';

  @override
  String get effectsAnimations => 'الحركة';

  @override
  String get effectsMotionFull => 'كاملة';

  @override
  String get effectsMotionFullHint =>
      'تتحرك الرسوم وتتنفس شاشات الانتظار وتُحتفل اللحظات المكتملة';

  @override
  String get effectsMotionCalm => 'هادئة';

  @override
  String get effectsMotionCalmHint =>
      'تظهر الرسوم ولا يبقى شيء متحركًا ولا احتفالات';

  @override
  String get effectsMotionOff => 'متوقفة';

  @override
  String get effectsMotionOffHint => 'يظهر كل شيء فورًا';

  @override
  String get effectsMotionSystemOff =>
      'خيار «إزالة الرسوم المتحركة» مفعّل في هاتفك، لذا لا يتحرك شيء أيًّا كان اختيارك هنا';

  @override
  String get effectsActivityGlow => 'حدّ متوهّج أثناء الرد';

  @override
  String get effectsSaveFailed =>
      'تعذّر حفظ هذا الاختيار على هذا الجهاز. حاول مرة أخرى.';

  @override
  String get teamDiscoverEntryTitle => 'كلّف فريقًا بمهمة أكبر';

  @override
  String get teamDiscoverIntroBody =>
      'صِف ما تريد إنجازه. يقسّمه فريق من الوكلاء إلى خطوات، ويعمل عليها جنبًا إلى جنب، ثم يضيف العمل المنجز إلى مشروعك.';

  @override
  String get teamDiscoverHowHeading => 'كيف يعمل';

  @override
  String get teamDiscoverStepPlanTitle => 'يخطّط';

  @override
  String get teamDiscoverStepPlanBody => 'يقسّم المخطِّط مهمتك إلى خطوات.';

  @override
  String get teamDiscoverStepWorkTitle => 'يعمل';

  @override
  String get teamDiscoverStepWorkBody =>
      'يتولّى العاملون الخطوات، كلٌّ على نسخته من المشروع.';

  @override
  String get teamDiscoverStepCheckTitle => 'يراجع';

  @override
  String get teamDiscoverStepCheckBody => 'يراجع المراجِع عمل كل خطوة.';

  @override
  String get teamDiscoverStepMergeTitle => 'يدمج';

  @override
  String get teamDiscoverStepMergeBody =>
      'يصل العمل المنجز إلى مشروعك. وحين يحتاج إلى قرار يسألك.';

  @override
  String get teamDiscoverNeedsPhone => 'ما يحتاجه على هذا الهاتف';

  @override
  String teamDiscoverNeedsServer(String server) {
    return 'ما يحتاجه على $server';
  }

  @override
  String teamDiscoverDownloadTitle(String size) {
    return 'تنزيل بنحو $size';
  }

  @override
  String get teamDiscoverInAppDownloadBody =>
      'يُثبَّت مرة واحدة بجوار OpenCode على هذا الهاتف.';

  @override
  String get teamDiscoverTermuxDownloadBody =>
      'يُثبَّت داخل Termux بجوار OpenCode.';

  @override
  String get teamDiscoverBatteryTitle => 'بطارية أكثر أثناء عمله';

  @override
  String get teamDiscoverInAppBatteryBody =>
      'يعمل عدة وكلاء معًا، وقد يوقف Android بعضهم إن كثروا.';

  @override
  String get teamDiscoverTermuxBatteryBody =>
      'أبقِ Termux مفتوحًا أثناء عمله؛ قد يوقفه Android في الخلفية. لا يضيع شيء.';

  @override
  String get teamDiscoverProjectTitle => 'أنت تختار المشاريع';

  @override
  String get teamDiscoverProjectBody => 'شغّله لكل مشروع تريده أن يعمل عليه.';

  @override
  String teamDiscoverComputerTitle(String server) {
    return 'يعمل على $server';
  }

  @override
  String get teamDiscoverComputerBody =>
      'ثبّت Gas City هناك مرة واحدة، وسيعثر عليه التطبيق بنفسه.';

  @override
  String get teamDiscoverSpeedTitle => 'بسرعة حاسوبك';

  @override
  String get teamDiscoverSpeedBody => 'أبقِه مستيقظًا أثناء عمل الفريق.';

  @override
  String teamDiscoverLooking(String server) {
    return 'جارٍ البحث عنه على $server…';
  }

  @override
  String get teamDiscoverFoundBody => 'إنه جاهز للتشغيل.';

  @override
  String get teamDiscoverEnterAddress => 'أدخِل عنوانه';

  @override
  String get teamDiscoverOnComputer => 'شغّله على حاسوب';

  @override
  String get teamDiscoverTurningOn => 'جارٍ التشغيل…';

  @override
  String get teamNowChecksEveryMinute => 'يتفقّد الفريق كل دقيقة';

  @override
  String teamNowChecksEvery(String minutes) {
    return 'يتفقّد الفريق كل $minutes د';
  }

  @override
  String get teamNowNextCheck => 'يبدأ عامل عند تفقّد الفريق التالي';

  @override
  String get teamNowNoWorkerStarted => 'لم يبدأ أي عامل';

  @override
  String get teamNowPausedLine =>
      'الفريق موقوف مؤقتًا · لن يبدأ شيء حتى تستأنفه';

  @override
  String get teamNowStartWorker => 'شغّل عاملًا';

  @override
  String get teamNowWhy => 'لماذا؟';

  @override
  String get teamAgentDidNotStartTitle => 'لم يبدأ العامل';

  @override
  String get teamAgentDidNotStartBody =>
      'هناك مهمة تنتظر، لكن هذا العامل لا يعمل.';

  @override
  String get teamAgentStartIt => 'شغّله';

  @override
  String teamOutputNotRunning(String name) {
    return '$name لا يعمل، لذا لا مخرجات';
  }

  @override
  String get teamOutputSilent => 'لا مخرجات بعد · قد يستغرق البدء دقيقة';

  @override
  String teamAgentTitle(String role, String name) {
    return '$role · $name';
  }

  @override
  String teamAgentWorksOn(String title) {
    return 'على «$title»';
  }

  @override
  String teamOutputStartingPhone(String age) {
    return 'يبدأ التشغيل · منذ $age · قد يستغرق ذلك بضع دقائق على الهاتف';
  }

  @override
  String get chatWatchEmptyTitle => 'لا شيء هنا بعد';

  @override
  String get chatWatchEmptyBody => 'تمتلئ هذه المحادثة بينما يعمل الوكيل.';

  @override
  String teamWatchBannerRole(String role, String state) {
    return 'تشاهد $role · فريق الذكاء';
  }

  @override
  String get teamWatchFallbackUnreadable =>
      'لا يمكن قراءة محادثته من الخادم المتصل به التطبيق، لذا هذا هو الناتج المباشر للفريق.';

  @override
  String get teamWatchFallbackNotFound =>
      'محادثته ليست بعد على الخادم المتصل به التطبيق (قد يكون ما زال يبدأ، أو أن الفريق يعمل على حاسوب آخر)، لذا هذا هو الناتج المباشر للفريق.';

  @override
  String get teamOpenConversation => 'افتح المحادثة';

  @override
  String teamOpenConversationHint(String name) {
    return 'شاهد عمل $name في المحادثة';
  }

  @override
  String get teamOpenConversationFinding => 'جارٍ العثور على محادثته…';

  @override
  String get teamChatUntitled => 'مهمة الفريق';

  @override
  String teamChatSubtitle(String host) {
    return 'فريق الذكاء · $host';
  }

  @override
  String get teamChatOpenTeam => 'فريق الذكاء';

  @override
  String get teamChatTaskDetails => 'تفاصيل المهمة';

  @override
  String get teamChatStopTask => 'إيقاف المهمة';

  @override
  String get teamChatStopConfirmTitle => 'هل تريد إيقاف هذه المهمة؟';

  @override
  String teamChatStopConfirmBody(String task) {
    return 'ستتوقف «$task» على جهاز الفريق. يتوقف الآن العاملون الذين ما زالوا يعملون عليها، ويبقى العمل المنجز كما هو. لا يمكن التراجع عن ذلك من الهاتف.';
  }

  @override
  String get teamChatStopKeepRunning => 'دعها تعمل';

  @override
  String get teamChatLoading => 'جارٍ تحميل المهمة';

  @override
  String get teamChatLeadName => 'الفريق';

  @override
  String get teamChatLeadSent => 'أُرسلت إلى الفريق';

  @override
  String get teamChatLeadNothingYet =>
      'لا شيء بعد. لم يخطط الفريق لهذه المهمة.';

  @override
  String teamChatLeadPlanned(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'خُططت $count خطوة',
      many: 'خُططت $count خطوة',
      few: 'خُططت $count خطوات',
      two: 'خُططت خطوتان',
      one: 'خُططت خطوة واحدة',
      zero: 'لا خطوات',
    );
    return '$_temp0';
  }

  @override
  String teamChatLeadRouted(String title) {
    return 'أُرسلت «$title» إلى العمال';
  }

  @override
  String teamChatLeadStarting(String title) {
    return 'بدأ عامل على «$title»';
  }

  @override
  String teamChatLeadClaimedWorker(String title) {
    return 'تولّى عامل «$title»';
  }

  @override
  String teamChatLeadPushed(String title) {
    return 'تغييرات «$title» على فرع';
  }

  @override
  String teamChatLeadReview(String title) {
    return 'سُلّمت «$title» للمراجعة';
  }

  @override
  String teamChatLeadMerged(String title) {
    return 'دُمجت «$title»';
  }

  @override
  String teamChatLeadStepFailed(String title) {
    return 'فشلت «$title»';
  }

  @override
  String teamChatLeadStepCancelled(String title) {
    return 'أُلغيت «$title»';
  }

  @override
  String teamChatLeadNeedsYou(String question) {
    return 'يحتاجك: $question';
  }

  @override
  String get teamChatLeadTaskMerged => 'دُمجت. انتهت المهمة.';

  @override
  String get teamChatLeadTaskFinished => 'انتهت المهمة.';

  @override
  String get teamChatLeadTaskFailed => 'فشلت المهمة.';

  @override
  String get teamChatLeadTaskCancelled => 'أُلغيت المهمة.';

  @override
  String get teamChatAWorker => 'عامل';

  @override
  String teamChatStepsSummary(int count, int done) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count خطوة',
      many: '$count خطوة',
      few: '$count خطوات',
      two: 'خطوتان',
      one: 'خطوة واحدة',
      zero: 'لا خطوات',
    );
    return '$_temp0 · $done منتهية';
  }

  @override
  String get teamChatComposerHint => 'راسل الفريق…';

  @override
  String teamChatComposerGoesTo(String name) {
    return 'رسالتك تصل إلى $name';
  }

  @override
  String get teamChatComposerNobody =>
      'لا يوجد وكيل على هذه المهمة لمراسلته بعد.';

  @override
  String get teamChatComposerCannot => 'لا يمكن مراسلة هذا الفريق من هنا.';

  @override
  String get teamBoardTitle => 'اللوحة';

  @override
  String get teamBoardOpenTooltip => 'اللوحة';

  @override
  String get teamBoardColumnBacklog => 'قائمة الانتظار';

  @override
  String get teamBoardColumnReady => 'جاهزة';

  @override
  String get teamBoardColumnWorking => 'قيد العمل';

  @override
  String get teamBoardColumnReview => 'المراجعة';

  @override
  String get teamBoardColumnDone => 'منجزة';

  @override
  String get teamBoardEmptyBacklogTitle => 'لا شيء بالانتظار';

  @override
  String get teamBoardEmptyBacklogBody =>
      'المهام التي تضيفها ولم تبدأها تنتظر هنا.';

  @override
  String get teamBoardEmptyReadyTitle => 'لا شيء في الدور';

  @override
  String get teamBoardEmptyReadyBody =>
      'المهام التي أُعطيت للفريق تنتظر هنا عاملًا.';

  @override
  String get teamBoardEmptyWorkingTitle => 'لا أحد يعمل الآن';

  @override
  String get teamBoardEmptyWorkingBody =>
      'تنتقل المهمة إلى هنا عندما يتولاها عامل.';

  @override
  String get teamBoardEmptyReviewTitle => 'لا شيء للمراجعة';

  @override
  String get teamBoardEmptyReviewBody => 'العمل المكتمل ينتظر هنا فحصه ودمجه.';

  @override
  String get teamBoardEmptyDoneTitle => 'لم يكتمل شيء هذا الأسبوع';

  @override
  String get teamBoardEmptyDoneBody =>
      'تظهر هنا المهام المدمجة والمنجزة والملغاة في آخر 7 أيام.';

  @override
  String get teamBoardEmptyTitle => 'لا مهام بعد';

  @override
  String get teamBoardEmptyBody =>
      'تظهر هنا المهام التي تعطيها للفريق، حسب مرحلتها.';

  @override
  String get teamBoardPriorityUrgent => 'عاجلة';

  @override
  String get teamBoardPriorityHigh => 'عالية';

  @override
  String get teamBoardPriorityNormal => 'عادية';

  @override
  String get teamBoardPriorityLow => 'منخفضة';

  @override
  String get teamBoardPrioritySomeday => 'يومًا ما';

  @override
  String get teamBoardTypeBug => 'خلل';

  @override
  String get teamBoardTypeFeature => 'ميزة';

  @override
  String get teamBoardTypeEpic => 'مهمة كبرى';

  @override
  String get teamBoardTypeChore => 'مهمة روتينية';

  @override
  String teamBoardEpicProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get teamBoardFlagNeedsYou => 'يحتاج إليك';

  @override
  String get teamBoardFlagBlocked => 'متوقفة';

  @override
  String teamBoardFlagBlockedBy(String title) {
    return 'متوقفة حتى تنتهي «$title»';
  }

  @override
  String teamBoardFlagBlockedByMore(String title, int count) {
    return 'متوقفة حتى تنتهي «$title» و$count غيرها';
  }

  @override
  String get teamBoardFlagFailed => 'توقفت بسبب خطأ';

  @override
  String teamBoardFlagInEpic(String epic) {
    return 'ضمن «$epic»';
  }

  @override
  String teamBoardFlagMoving(String column) {
    return 'جارٍ النقل إلى $column…';
  }

  @override
  String get teamBoardFlagCancelled => 'ملغاة';

  @override
  String get teamBoardMoveMenuTooltip => 'نقل أو تغيير';

  @override
  String teamBoardMoveSheetWhere(String column) {
    return 'في $column';
  }

  @override
  String get teamBoardMoveStartNow => 'ابدأ الآن';

  @override
  String get teamBoardMoveStartNowHint => 'أعطها لعمّال الفريق';

  @override
  String get teamBoardMoveBackToBacklog => 'أعدها إلى قائمة الانتظار';

  @override
  String get teamBoardMoveBackToBacklogHint => 'لن يتولاها الفريق حتى تبدأها';

  @override
  String get teamBoardMovePriority => 'الأولوية';

  @override
  String get teamBoardMoveCancel => 'ألغِ المهمة';

  @override
  String get teamBoardMoveCancelHint => 'تنقلها إلى المنجزة كمهمة ملغاة';

  @override
  String get teamBoardMoveReopen => 'أعدها إلى قائمة الانتظار';

  @override
  String get teamBoardMoveReopenHint => 'لن يعمل عليها أحد حتى تبدأها';

  @override
  String get teamBoardOpenConversation => 'افتح المحادثة';

  @override
  String get teamBoardOpenDetails => 'افتح التفاصيل';

  @override
  String get teamBoardTeamMoves =>
      'الفريق هو من ينقل هذه المهمة. افتح محادثتها لمراسلة الفريق أو إيقافه.';

  @override
  String get teamBoardReadOnlyNote =>
      'هذا المضيف لا يسمح للتطبيق بتغيير المهام، لذا اللوحة للقراءة فقط هنا.';

  @override
  String get teamBoardStatusReadOnly =>
      'للقراءة فقط هنا · هذا المضيف لا يسمح للتطبيق بتغيير المهام';

  @override
  String teamBoardCancelTitle(String title) {
    return 'إلغاء «$title»؟';
  }

  @override
  String get teamBoardCancelBody =>
      'لن يعمل الفريق عليها. ستنتقل إلى المنجزة كمهمة ملغاة، ويمكنك إعادتها إلى قائمة الانتظار لاحقًا.';

  @override
  String get teamBoardCancelKeep => 'أبقِها';

  @override
  String teamBoardMoveFailedTitle(String title) {
    return 'تعذّر تغيير «$title»';
  }

  @override
  String get teamBoardMoveFailedBody =>
      'رفض مضيف الفريق الطلب، لذا بقيت في مكانها.';

  @override
  String get teamBoardPriorityTitle => 'الأولوية';

  @override
  String get teamBoardAddFailed => 'تعذّرت الإضافة. رفض مضيف الفريق الطلب.';

  @override
  String get teamBoardProjectTooltip => 'اختر المشروع';

  @override
  String appExitForceStopped(String time) {
    return 'أغلق أندرويد OpenCode Mobile $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'أغلق أندرويد OpenCode Mobile $time لتحرير الذاكرة';
  }

  @override
  String appExitCrashed(String time) {
    return 'توقّف OpenCode Mobile على نحو غير متوقع $time';
  }

  @override
  String appExitKilled(String time) {
    return 'أوقف أندرويد OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what. توقّف OpenCode على هاتفك معه، وهو يبدأ من جديد.';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what. توقّف OpenCode على هاتفك وفريق الذكاء الاصطناعي معه، وهما يبدآن من جديد.';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what. توقف OpenCode على هاتفك معه. شغّله مجددًا عندما تكون جاهزًا.';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what. توقف OpenCode وAI Team على هاتفك معه. شغّلهما مجددًا عندما تكون جاهزًا.';
  }

  @override
  String appExitServerBack(String what) {
    return '$what. توقف OpenCode على هاتفك معه ثم عاد إلى العمل.';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what. توقف OpenCode وAI Team على هاتفك معه؛ عاد OpenCode إلى العمل.';
  }

  @override
  String appExitAtTime(String time) {
    return 'عند $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return 'يوم $day عند $time';
  }

  @override
  String get appExitSwitchToPhone => 'التبديل إلى هذا الهاتف';

  @override
  String get appExitKeepRunning => 'أبقِه يعمل';

  @override
  String get keepRunningTitle => 'الاستمرار في العمل في الخلفية';

  @override
  String get keepRunningRowSubtitle =>
      'ما يجب السماح به كي لا يغلق هاتفك التطبيق';

  @override
  String get keepRunningIntro =>
      'يعمل OpenCode وفريق الذكاء الاصطناعي على هاتفك داخل هذا التطبيق، لذا يتوقفان عندما يغلقه أندرويد. على هذا الهاتف، اسمح بما يلي:';

  @override
  String get keepRunningSwipeWarning =>
      'يغلق هذا الهاتف التطبيق الذي تسحبه بعيدًا من التطبيقات الحديثة حتى وهو يعمل. اقفله هناك بدلًا من سحبه.';

  @override
  String get keepRunningBatteryTitle => 'عدم تحسين البطارية';

  @override
  String get keepRunningBatteryDetail =>
      'يتيح للتطبيق الاستمرار في العمل أثناء استخدامك تطبيقات أخرى.';

  @override
  String get keepRunningBatteryDone => 'مسموح';

  @override
  String get keepRunningLockTitle => 'اقفله في التطبيقات الحديثة';

  @override
  String get keepRunningLockNubia =>
      'افتح التطبيقات الحديثة واسحب بطاقة OpenCode Mobile للأسفل حتى يظهر القفل.';

  @override
  String get keepRunningLockSamsung =>
      'افتح التطبيقات الحديثة، واضغط أيقونة OpenCode Mobile فوق بطاقته واختر إبقاء مفتوحًا.';

  @override
  String get keepRunningLockOther =>
      'افتح التطبيقات الحديثة، واضغط مطولًا على بطاقة OpenCode Mobile ثم اضغط القفل.';

  @override
  String get keepRunningAutostartTitle => 'السماح بالتشغيل التلقائي';

  @override
  String get keepRunningAutostartDetail =>
      'فعّله لتطبيق OpenCode Mobile كي لا يوقفه الهاتف في الخلفية.';

  @override
  String get keepRunningAutostartHuawei =>
      'في تشغيل التطبيقات، اضبط OpenCode Mobile على الإدارة يدويًا وفعّل المفاتيح الثلاثة.';

  @override
  String get keepRunningBackgroundTitle => 'السماح بالنشاط في الخلفية';

  @override
  String get keepRunningBackgroundXiaomi =>
      'في معلومات التطبيق › موفّر البطارية، اختر بلا قيود.';

  @override
  String get keepRunningBackgroundOppo =>
      'في معلومات التطبيق › استخدام البطارية، اسمح بالنشاط في الخلفية.';

  @override
  String get keepRunningBackgroundVivo =>
      'في معلومات التطبيق › البطارية، اسمح بالاستهلاك العالي للطاقة في الخلفية.';

  @override
  String get keepRunningBackgroundSamsung =>
      'في معلومات التطبيق › البطارية، اختر غير مقيّد، وأبقِ التطبيق خارج التطبيقات النائمة.';

  @override
  String get keepRunningBackgroundOther =>
      'في معلومات التطبيق › البطارية، اختر غير مقيّد أو اسمح بالعمل في الخلفية.';

  @override
  String get keepRunningOpen => 'فتح';

  @override
  String get keepRunningOpenFailed =>
      'لا توجد هذه الشاشة على هذا الهاتف. افتح الإعدادات › التطبيقات › OpenCode Mobile بدلًا من ذلك.';

  @override
  String get keepRunningFootnote =>
      'إذا أغلق أندرويد التطبيق رغم ذلك، فسيعيد تشغيل OpenCode على هاتفك في المرة التالية التي تفتحه فيها.';

  @override
  String teamAgentLastStep(String step) {
    return 'آخر خطوة: $step';
  }

  @override
  String teamAgentLastActive(String elapsed) {
    return 'آخر نشاط قبل $elapsed';
  }

  @override
  String teamChatLeadEarlier(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تحديثات سابقة',
      one: 'تحديث سابق واحد',
    );
    return '$_temp0';
  }

  @override
  String get appExitQuietRestarted => 'أُعيد التشغيل بعد إغلاق التطبيق';

  @override
  String get appExitQuietRestarting => 'جارٍ إعادة التشغيل بعد إغلاق التطبيق';

  @override
  String get thermalQuietPaused =>
      'الهاتف ساخن · فريق الذكاء الاصطناعي متوقف حتى يبرد';

  @override
  String get thermalQuietStopped =>
      'الهاتف ساخن جدًا · أُوقف فريق الذكاء الاصطناعي حتى يبرد';

  @override
  String get thermalQuietResumed => 'برد الهاتف · استأنف فريق الذكاء الاصطناعي';

  @override
  String get thermalPausedNotice =>
      'هاتفك ساخن — أوقفنا فريق الذكاء الاصطناعي مؤقتًا ليبرد. سيستأنف عمله من تلقاء نفسه.';

  @override
  String get thermalStoppedNotice =>
      'هاتفك ساخن جدًا — أوقفنا فريق الذكاء الاصطناعي لحمايته. عمله محفوظ، وسيبدأ من جديد عندما يبرد الهاتف.';

  @override
  String get thermalResumedNotice =>
      'استأنف فريق الذكاء الاصطناعي عمله — لقد برد هاتفك.';

  @override
  String get thermalGuardSetting =>
      'إيقاف فريق الذكاء الاصطناعي مؤقتًا عندما يسخن الهاتف';

  @override
  String get thermalGuardSettingDetail =>
      'يتوقف الفريق مؤقتًا مع حفظ عمله ويستأنف من تلقاء نفسه عندما يبرد الهاتف.';

  @override
  String get kitSheetClose => 'إغلاق';

  @override
  String get kitSheetDismiss => 'إخفاء';

  @override
  String get kitSheetLoading => 'جارٍ التحميل';

  @override
  String get kitConfirmCancel => 'إلغاء';

  @override
  String get kitConfirmKeepRunning => 'مواصلة التشغيل';

  @override
  String get kitConfirmKeepEditing => 'متابعة التحرير';

  @override
  String kitConfirmTypeName(String name) {
    return 'اكتب $name للتأكيد';
  }

  @override
  String get kitConfirmTypeNameReason =>
      'اكتب الاسم تمامًا كما يظهر لتفعيل هذا الزر.';

  @override
  String get kitConfirmFailed => 'لم يكتمل ذلك. يمكنك إعادة المحاولة.';

  @override
  String get kitTryAgain => 'إعادة المحاولة';

  @override
  String get kitDetails => 'التفاصيل';

  @override
  String get kitDiscardTitle => 'هل تريد تجاهل تغييراتك؟';

  @override
  String get kitDiscardBody =>
      'ما غيّرته هنا غير محفوظ، ولا يمكن التراجع عن تجاهله.';

  @override
  String get kitDiscardConfirm => 'تجاهل التغييرات';

  @override
  String get kitCopied => 'تم النسخ';

  @override
  String get kitMore => 'المزيد';

  @override
  String kitChipRemove(String label) {
    return 'إزالة $label';
  }

  @override
  String get kitCopy => 'نسخ';

  @override
  String get kitWorking => 'قيد التنفيذ';

  @override
  String get kitImageUnavailable => 'يتعذر عرض هذه الصورة';

  @override
  String get kitZoomIn => 'تكبير';

  @override
  String get kitZoomOut => 'تصغير';

  @override
  String get kitZoomReset => 'إعادة ضبط التكبير';

  @override
  String get kitZoomFit => 'ملائمة الشاشة';

  @override
  String get kitZoomAtStart => 'في العرض الكامل بالفعل';

  @override
  String get kitZoomAtMax => 'أقصى تكبير';

  @override
  String kitZoomLevel(String percent) {
    return '$percent٪';
  }

  @override
  String kitZoomShortcut(String action, String key) {
    return '$action · Ctrl+$key';
  }

  @override
  String get kitMenu => 'قائمة';

  @override
  String get kitCopyDetails => 'نسخ التفاصيل';

  @override
  String get kitReportProblem => 'الإبلاغ عن مشكلة';

  @override
  String kitProgressStep(int step, int of) {
    final intl.NumberFormat stepNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String stepString = stepNumberFormat.format(step);
    final intl.NumberFormat ofNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String ofString = ofNumberFormat.format(of);

    return 'الخطوة $stepString من $ofString';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: 'بقيت $secondsString ثانية تقريبًا',
      many: 'بقيت $secondsString ثانية تقريبًا',
      few: 'بقيت $secondsString ثوانٍ تقريبًا',
      two: 'بقيت ثانيتان تقريبًا',
      one: 'بقيت ثانية تقريبًا',
      zero: 'بقي أقل من ثانية',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaMinutes(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'بقيت $minutesString دقيقة تقريبًا',
      many: 'بقيت $minutesString دقيقة تقريبًا',
      few: 'بقيت $minutesString دقائق تقريبًا',
      two: 'بقيت دقيقتان تقريبًا',
      one: 'بقيت دقيقة تقريبًا',
      zero: 'بقي أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'بقيت $hoursString ساعة تقريبًا',
      many: 'بقيت $hoursString ساعة تقريبًا',
      few: 'بقيت $hoursString ساعات تقريبًا',
      two: 'بقيت ساعتان تقريبًا',
      one: 'بقيت ساعة تقريبًا',
      zero: 'بقي أقل من ساعة',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong => 'هذا أطول مما يسعه رمز QR. انسخ الرابط بدلًا منه.';

  @override
  String kitSinceStillWaiting(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    return 'لا يزال الانتظار مستمرًا بعد $secondsString ث';
  }

  @override
  String kitSinceWaitingFor(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'جارٍ الانتظار منذ $minutesString دقيقة',
      many: 'جارٍ الانتظار منذ $minutesString دقيقة',
      few: 'جارٍ الانتظار منذ $minutesString دقائق',
      two: 'جارٍ الانتظار منذ دقيقتين',
      one: 'جارٍ الانتظار منذ دقيقة واحدة',
      zero: 'جارٍ الانتظار منذ أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String kitSinceAge(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutesString دقيقة',
      many: '$minutesString دقيقة',
      few: '$minutesString دقائق',
      two: 'دقيقتين',
      one: 'دقيقة واحدة',
      zero: 'أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String get kitMarkWaiting => 'بانتظار';

  @override
  String get kitMarkWorking => 'يعمل';

  @override
  String get kitMarkDone => 'انتهى';

  @override
  String get kitMarkFailed => 'فشل';

  @override
  String get kitMarkPaused => 'متوقف مؤقتًا';

  @override
  String get kitTaskNeedsYou => 'يحتاجك';

  @override
  String get kitTaskStopped => 'متوقف';

  @override
  String get kitSwatchInUse => 'قيد الاستخدام';

  @override
  String get kitThemePreviewTitle => 'إصلاح خطأ تسجيل الدخول';

  @override
  String get kitThemePreviewWorking => 'قيد التنفيذ · دقيقتان';

  @override
  String get kitThemePreviewNeedsYou => 'يحتاجك';

  @override
  String get kitThemePreviewPrimary => 'إرسال';

  @override
  String get kitThemePreviewSecondary => 'إرفاق';

  @override
  String get kitThemePreviewSegment => 'مفعّل';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => 'مفتاح الشرطة المائلة';

  @override
  String get kitTerminalViewKeyDash => 'مفتاح الشرطة';

  @override
  String get kitTerminalViewKeyPipe => 'مفتاح الخط العمودي';

  @override
  String get kitTerminalViewKeyTilde => 'مفتاح التلدة';

  @override
  String get kitTerminalViewKeyHome => 'مفتاح Home';

  @override
  String get kitTerminalViewKeyEnd => 'مفتاح End';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تُعرض آخر $countString من الأسطر',
      one: 'يُعرض آخر سطر فقط',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => 'تراجع';

  @override
  String kitUndoFailed(String message) {
    return 'تعذّر التراجع. $message';
  }

  @override
  String get kitUndoWorking => 'جارٍ التراجع';

  @override
  String get kitTermHint => 'يتوفر شرح';

  @override
  String get kitTermShow => 'إظهار الشرح';

  @override
  String get kitTermClose => 'إغلاق الشرح';

  @override
  String kitFieldShowNamed(String label) {
    return 'إظهار $label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return 'إخفاء $label';
  }

  @override
  String get kitFieldPaste => 'لصق';

  @override
  String get kitFieldSaved => 'محفوظ';

  @override
  String get kitFieldReplace => 'استبدال';

  @override
  String get kitFieldChecking => 'جارٍ التحقق…';

  @override
  String kitFieldStillChecking(int seconds) {
    return 'لا يزال التحقق جاريًا بعد $seconds ث';
  }

  @override
  String kitFieldCount(int count, int max) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);
    final intl.NumberFormat maxNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String maxString = maxNumberFormat.format(max);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString من $maxString',
      one: '1 من $maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => 'بلغ الحد';

  @override
  String get kitFieldErrorLabel => 'خطأ';

  @override
  String get kitTappableShowActions => 'إظهار الإجراءات';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الملفات',
      one: 'ملفًا واحدًا',
    );
    return 'قرأ $_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المرات',
      one: 'مرة واحدة',
    );
    return 'بحث $_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المجلدات',
      one: 'مجلد واحد',
    );
    return 'عرض محتويات $_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الملفات',
      one: 'ملفًا واحدًا',
    );
    return 'عدّل $_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الأوامر',
      one: 'أمرًا واحدًا',
    );
    return 'شغّل $_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الصفحات',
      one: 'صفحة واحدة',
    );
    return 'جلب $_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المهام',
      one: 'مهمة واحدة',
    );
    return 'فوّض $_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الخطوات الأخرى',
      one: 'خطوة أخرى واحدة',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count لم تُنفّذ',
      one: 'واحدة لم تُنفّذ',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الخطوات',
      one: 'خطوة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => 'بانتظارك';

  @override
  String get kitWorkStopped => 'متوقف';

  @override
  String get kitWorkDidntFinish => 'لم يكتمل';

  @override
  String get kitWorkWorking => 'قيد العمل';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الخطوات السابقة',
      one: 'خطوة سابقة واحدة',
    );
    return 'عرض $_temp0';
  }

  @override
  String get kitReceiptSending => 'جارٍ الإرسال…';

  @override
  String get kitReceiptSent => 'أُرسل';

  @override
  String get kitReceiptConfirmed => 'اكتمل';

  @override
  String get kitReceiptNotConfirmed => 'لم يُؤكّد بعد';

  @override
  String get kitReceiptRefused => 'لم يُقبل';

  @override
  String kitReceiptRefusedReason(String reason) {
    return 'لم يُقبل: $reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return 'أُجيب عنه على $where';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown => 'أُجيب عنه على جهاز آخر';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act: $reason';
  }

  @override
  String kitReceiptAt(String time) {
    return 'في $time';
  }

  @override
  String get kitDetailsHide => 'إخفاء التفاصيل';

  @override
  String get kitCopyAll => 'نسخ الكل';

  @override
  String kitCopyValue(String label) {
    return 'نسخ $label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض الأسطر الـ $count كلها',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label: $value';
  }

  @override
  String get kitProgressRowLoading => 'جارٍ التحميل';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString بالمئة';
  }

  @override
  String get kitProgressRowNearLimit => 'قريب من الحد';

  @override
  String get kitProgressRowAtLimit => 'بلغ الحد';

  @override
  String kitProgressRowAsOf(String time) {
    return 'حتى $time';
  }

  @override
  String get kitProgressRowOther => 'أخرى';

  @override
  String get kitModelServerDefault => 'إعداد الخادم الافتراضي';

  @override
  String get kitModelSignIn => 'تسجيل الدخول إلى نموذج';

  @override
  String get kitModelChoose => 'اختيار نموذج';

  @override
  String get kitModelChange => 'تغيير النموذج';

  @override
  String get kitModelActions => 'اختصارات النموذج';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String kitAttachmentOpen(String label) {
    return 'معاينة $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return 'صورة، $label';
  }

  @override
  String kitAttachmentFile(String label) {
    return 'ملف، $label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return 'مجلد، $label';
  }

  @override
  String kitAttachmentReference(String label) {
    return 'مرجع، $label';
  }

  @override
  String get kitSuggestionsShowAll => 'عرض الكل';

  @override
  String get kitSuggestionsLabel => 'اقتراحات';

  @override
  String get kitNeedsYouReasonDecision => 'يحتاج إلى قرارك';

  @override
  String get kitNeedsYouReasonBlocked => 'عالق: يحتاج إليك';

  @override
  String get kitNeedsYouReasonConsent => 'يحتاج إلى موافقتك';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString يحتاجون إليك · ',
      one: 'يحتاج إليك · ',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouBadgeSuffix(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '، $countString يحتاجون إليك',
      one: '، واحد يحتاج إليك',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return 'ينتظر منذ $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'ينتظر منذ $minutesString من الدقائق',
      one: 'ينتظر منذ دقيقة واحدة',
      zero: 'ينتظر منذ أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$who على $server';
  }

  @override
  String get kitWorkGraph => 'مخطط العمل';

  @override
  String get kitWorkGraphEmpty => 'لا توجد عناصر عمل بعد';

  @override
  String kitWorkGraphNode(String title, String state) {
    return '$title، $state';
  }

  @override
  String kitWorkGraphNeeds(String title) {
    return 'يحتاج إلى $title';
  }

  @override
  String kitWorkGraphNeedsMore(String title, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: 'واحد',
    );
    return 'يحتاج إلى $title و$_temp0 آخر';
  }

  @override
  String get kitJumpLatest => 'الانتقال إلى الأحدث';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count جديدة · الانتقال إلى الأحدث',
      one: 'واحد جديد · الانتقال إلى الأحدث',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => 'الحالي';

  @override
  String get kitChoiceRecommended => 'موصى به';

  @override
  String get kitChoiceOtherSend => 'إرسال الإجابة';

  @override
  String kitChoiceSelectedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حُدّد $count',
      one: 'حُدّد واحد',
      zero: 'لم يُحدَّد شيء',
    );
    return '$_temp0';
  }

  @override
  String get kitComposerField => 'الرسالة';

  @override
  String get kitComposerSend => 'إرسال';

  @override
  String get kitComposerSending => 'جارٍ الإرسال';

  @override
  String get kitComposerSendOffline => 'الإرسال عند عودة الاتصال';

  @override
  String get kitComposerSendAfter => 'الإرسال بعد هذا الرد';

  @override
  String get kitComposerAddToTurn => 'إضافة إلى هذه الجولة';

  @override
  String get kitComposerStop => 'إيقاف الرد';

  @override
  String get kitComposerSendsAfter => 'يُرسل بعد هذا الرد';

  @override
  String get kitComposerStopAndSend => 'إيقاف وإرسال';

  @override
  String get kitComposerStopAndSendNote =>
      'يوقف الرد الحالي ويبدأ من جديد برسالتك.';

  @override
  String get kitComposerCannotSendYet => 'يمكنك الإرسال عند انتهاء هذا الرد';

  @override
  String get kitComposerTools => 'الإرفاق والمزيد';

  @override
  String get kitComposerVoice => 'التحدث بدلًا من الكتابة';

  @override
  String get kitComposerEditor => 'فتح محرّر بملء الشاشة';

  @override
  String get kitVoiceLeave => 'مغادرة وضع الصوت';

  @override
  String get kitVoiceStarting => 'جارٍ تجهيز الميكروفون…';

  @override
  String get kitVoiceListening => 'جارٍ الاستماع…';

  @override
  String get kitVoiceTranscribing => 'جارٍ كتابة ما قلته…';

  @override
  String get kitVoiceWaitingReply => 'بانتظار الرد…';

  @override
  String get kitVoiceSpeaking => 'جارٍ قراءة الرد بصوت عالٍ';

  @override
  String get kitVoiceReplyReady => 'الرد جاهز';

  @override
  String get kitVoicePaused => 'متوقف مؤقتًا · يحتاج الوكيل إليك';

  @override
  String get kitVoiceMicDenied => 'الميكروفون معطّل لهذا التطبيق';

  @override
  String get kitVoiceFailed => 'توقف الصوت';

  @override
  String get kitVoiceSend => 'إرسال';

  @override
  String get kitVoiceDone => 'تم';

  @override
  String get kitVoiceStopReading => 'إيقاف القراءة';

  @override
  String get kitVoiceReadReply => 'قراءته بصوت عالٍ';

  @override
  String get kitVoiceListen => 'استماع';

  @override
  String get kitVoiceReadAloud => 'قراءة الردود بصوت عالٍ';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => 'مسح البحث';

  @override
  String get kitSearchFilter => 'تصفية';

  @override
  String kitSearchFilterActive(String name) {
    return 'التصفية: $name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من النتائج',
      one: 'نتيجة واحدة',
      zero: 'لا توجد نتائج',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return 'حُمّل $count · جارٍ البحث في الخادم…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return 'لا شيء يطابق $query';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return 'لا شيء في $what يطابق $query';
  }

  @override
  String get kitTopBarBack => 'رجوع';

  @override
  String get kitErrorFallbackMessage =>
      'حدث خطأ في هذا الجزء من OpenCode. ارجع وحاول مرة أخرى، أو افتح تشخيصات التطبيق لمعرفة التفاصيل.';

  @override
  String get kitTopBarClose => 'إغلاق';

  @override
  String get kitTopBarSearch => 'بحث';

  @override
  String get kitTopBarSwitchServer => 'تغيير الخادم';

  @override
  String get kitTopBarSwitchProject => 'تغيير المشروع';

  @override
  String get kitTopBarMore => 'المزيد من الإجراءات';

  @override
  String get kitAgentStripLabel => 'الوكلاء في هذه المهمة';

  @override
  String kitAgentOpen(String name) {
    return 'فتح محادثة $name';
  }

  @override
  String kitAgentLabel(String hasRole, String name, String role, String state) {
    String _temp0 = intl.Intl.selectLogic(hasRole, {
      'yes': '$name، $role، $state',
      'other': '$name، $state',
    });
    return '$_temp0';
  }

  @override
  String get kitBreadcrumb => 'مسار المجلد';

  @override
  String kitBreadcrumbOpen(String folder) {
    return 'فتح المجلد $folder';
  }

  @override
  String kitBreadcrumbOpenRoot(String root) {
    return 'فتح $root';
  }

  @override
  String kitBreadcrumbCurrent(String folder) {
    return 'المجلد الحالي: $folder';
  }

  @override
  String kitBreadcrumbMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المجلدات الأخرى',
      one: 'مجلد آخر واحد',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeCopyCode => 'نسخ الشيفرة';

  @override
  String get kitCodeCopyCommand => 'نسخ الأمر';

  @override
  String get kitCodeCopyOutput => 'نسخ المخرجات';

  @override
  String get kitCodeCopyFailedCode => 'تعذّر نسخ الشيفرة. حاول مجددًا.';

  @override
  String get kitCodeCopyFailedCommand => 'تعذّر نسخ الأمر. حاول مجددًا.';

  @override
  String get kitCodeCopyFailedOutput => 'تعذّر نسخ المخرجات. حاول مجددًا.';

  @override
  String kitCodeShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض الأسطر الـ $count كلها',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => 'فتح المخرجات كاملة';

  @override
  String get kitWrapLines => 'التفاف الأسطر';

  @override
  String kitCodeChanges(int added, int removed) {
    return 'أُضيف $added، وأُزيل $removed';
  }

  @override
  String get kitCodeEmpty => 'فارغ';

  @override
  String kitTabLabel(String label, int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$label، $countString';
  }

  @override
  String kitQueuedTitle(int count) {
    return 'بانتظار الإرسال · $count';
  }

  @override
  String get kitQueuedOffline => 'يُرسل عند عودة الاتصال';

  @override
  String get kitQueuedWaiting => 'بانتظار الإرسال';

  @override
  String get kitQueuedReachedServer => 'وصل إلى الخادم';

  @override
  String get kitQueuedAfterReply => 'يُرسل بعد هذا الرد';

  @override
  String get kitQueuedAddToTurn => 'يُضاف إلى هذه الجولة';

  @override
  String get kitQueuedUpdate => 'بانتظار التحديث';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المرفقات',
      one: 'مرفق واحد',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return 'الرسالة المنتظرة $index من $count: $text. $state';
  }

  @override
  String get kitQueuedActions => 'إجراءات الرسالة';

  @override
  String get kitLogTitle => 'المخرجات';

  @override
  String get kitLogShowOutput => 'عرض المخرجات';

  @override
  String get kitLogLive => 'مباشر';

  @override
  String kitLogQuiet(String age) {
    return 'آخر سطر منذ $age';
  }

  @override
  String kitLogQuietSeconds(int seconds) {
    return 'آخر سطر منذ $seconds ث';
  }

  @override
  String get kitLogEnded => 'انتهى';

  @override
  String kitLogEndedExit(String code) {
    return 'انتهى · رمز الخروج $code';
  }

  @override
  String get kitLogFailed => 'فشل';

  @override
  String kitLogFailedExit(String code) {
    return 'فشل · رمز الخروج $code';
  }

  @override
  String get kitLogEmpty => 'لا توجد مخرجات بعد';

  @override
  String kitLogNewLines(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الأسطر الجديدة',
      one: 'سطر جديد واحد',
    );
    return '$_temp0';
  }

  @override
  String kitLogDropped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الأسطر السابقة غير معروضة',
      one: 'سطر سابق واحد غير معروض',
    );
    return '$_temp0';
  }

  @override
  String get kitLogReadFailed => 'تعذّر قراءة المخرجات';

  @override
  String kitLogWarningLine(String line) {
    return 'تحذير: $line';
  }

  @override
  String kitLogErrorLine(String line) {
    return 'خطأ: $line';
  }

  @override
  String get kitUntilOff => 'حتى أوقفه';

  @override
  String get kitUntilConversation => 'لهذه المحادثة';

  @override
  String get kitUntilHour => 'لمدة ساعة';

  @override
  String get kitRiskTurnOn => 'تشغيل الميزة';

  @override
  String get kitRiskNotNow => 'ليس الآن';

  @override
  String get kitRiskTurnOff => 'إيقاف الميزة';

  @override
  String get safetyDisconnectBody =>
      'تتوقف التحديثات المباشرة وتعود إلى قائمة الخوادم. يبقى الخادم قيد التشغيل ولا يتغيّر شيء عليه.';

  @override
  String get safetyDisconnectBodyPhone =>
      'تتوقف التحديثات المباشرة وتعود إلى قائمة الخوادم. يبقى OpenCode قيد التشغيل على هذا الهاتف ويستهلك البطارية حتى توقفه.';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'تبقى $count من الرسائل التي تنتظر الإرسال على هذا الهاتف حتى تتصل مجددًا.',
      one: 'تبقى رسالة واحدة تنتظر الإرسال على هذا الهاتف حتى تتصل مجددًا.',
    );
    return '$_temp0';
  }

  @override
  String get folderBrowserSlowTitle => 'لا تزال قراءة هذا المجلد جارية';

  @override
  String get folderBrowserSlowBody =>
      'قد يستغرق عرض محتويات المجلدات على هذا الهاتف حتى 15 ثانية.';

  @override
  String get folderBrowserFirstProject => 'سمِّ مشروعك الأول';

  @override
  String kitChecklistNext(String step) {
    return 'التالي: $step';
  }

  @override
  String kitChecklistNeedsYou(String action) {
    return 'يحتاج إليك، $action';
  }

  @override
  String get kitChecklistShowSteps => 'إظهار الخطوات';

  @override
  String get kitChecklistHideSteps => 'إخفاء الخطوات';

  @override
  String get kitRequestAllowOnce => 'السماح مرة واحدة';

  @override
  String get kitRequestReject => 'رفض الطلب';

  @override
  String get kitRequestApprove => 'اعتماد الطلب';

  @override
  String get kitRequestSendBack => 'إعادة الطلب';

  @override
  String get kitRequestAnswer => 'إرسال الإجابة';

  @override
  String get kitRequestSend => 'إرسال الرد';

  @override
  String get kitRequestReplyEmptyReason => 'اكتب ردًا أولًا.';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString من الإجابات الأخرى',
      one: 'إجابة أخرى واحدة',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => 'انتهت الصلاحية · توقف الوكيل عن الانتظار';

  @override
  String kitRequestAge(String age) {
    return 'ينتظر منذ $age';
  }

  @override
  String kitDiffFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الملفات',
      one: 'ملف واحد',
    );
    return '$_temp0';
  }

  @override
  String kitDiffChangeOf(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'التغيير $indexString من $countString';
  }

  @override
  String get kitDiffPreviousChange => 'التغيير السابق';

  @override
  String get kitDiffNextChange => 'التغيير التالي';

  @override
  String kitDiffLines(int start, int end) {
    final intl.NumberFormat startNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String startString = startNumberFormat.format(start);
    final intl.NumberFormat endNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String endString = endNumberFormat.format(end);

    return 'الأسطر $startString–$endString';
  }

  @override
  String get kitDiffHideUnchanged => 'إخفاء الأسطر دون تغيير';

  @override
  String get kitDiffNoChanges => 'لا توجد تغييرات';

  @override
  String get kitDiffBinary => 'ملف ثنائي · غير معروض';

  @override
  String kitDiffRenamed(String path) {
    return 'أُعيدت تسميته من $path';
  }

  @override
  String get kitDiffAddedFile => 'ملف جديد';

  @override
  String get kitDiffDeletedFile => 'محذوف';

  @override
  String kitDiffTooBig(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'يُعرض $shownString من $totalString من الأسطر';
  }

  @override
  String get kitDiffOpenAll => 'فتح الكل';

  @override
  String kitDiffLineAdded(int number) {
    return 'أُضيف السطر $number';
  }

  @override
  String kitDiffLineRemoved(int number) {
    return 'أُزيل السطر $number';
  }

  @override
  String kitDiffLinesAdded(int start, int end) {
    return 'أُضيفت الأسطر $start–$end';
  }

  @override
  String kitDiffLinesRemoved(int start, int end) {
    return 'أُزيلت الأسطر $start–$end';
  }

  @override
  String get kitDiffShowSource => 'المصدر';

  @override
  String get kitDiffShowFormatted => 'منسّق';

  @override
  String get kitDiffComment => 'تعليق';

  @override
  String get kitDiffAddToPrompt => 'إضافة إلى الطلب';

  @override
  String get kitDiffCopyLines => 'نسخ الأسطر';

  @override
  String get kitDiffClearSelection => 'مسح التحديد';

  @override
  String kitDiffSelected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حُدّد $count من الأسطر',
      one: 'حُدّد سطر واحد',
    );
    return '$_temp0';
  }

  @override
  String kitDiffCounts(int added, int removed) {
    return 'أُضيف $added، وأُزيل $removed';
  }

  @override
  String get kitDiffLoadFailed => 'تعذّر تحميل التغييرات';

  @override
  String kitDiffLine(int number) {
    return 'السطر $number';
  }

  @override
  String kitBoardLane(String column, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المهام',
      one: 'مهمة واحدة',
      zero: 'لا توجد مهام',
    );
    return '$column، $_temp0';
  }

  @override
  String kitBoardLaneLoading(String column) {
    return 'جارٍ تحميل $column';
  }

  @override
  String get kitMarkdownOpenFile => 'فتح الملف';

  @override
  String kitMarkdownTable(int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: 'جدول، $rows من الصفوف',
      one: 'جدول، صف واحد',
    );
    return '$_temp0';
  }

  @override
  String get kitToolNotRun => 'لم يُنفّذ';

  @override
  String get kitToolWaiting => 'بانتظار';

  @override
  String get kitToolRunning => 'قيد التشغيل';

  @override
  String get kitToolWaitingForYou => 'بانتظارك';

  @override
  String get kitToolDone => 'مكتمل';

  @override
  String get kitToolFailed => 'فشل';

  @override
  String get kitToolDeclined => 'رفضتَ هذا';

  @override
  String get kitToolStopped => 'متوقف';

  @override
  String get kitToolBackground => 'بدأ في الخلفية';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString من الثواني',
      one: 'ثانية واحدة',
    );
    return '$_temp0';
  }

  @override
  String kitToolTookMinutes(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString من الدقائق',
      one: 'دقيقة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => 'فتح محادثته';

  @override
  String get kitViewerFind => 'بحث في الملف';

  @override
  String kitViewerFindCount(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$indexString من $countString';
  }

  @override
  String get kitViewerFindNone => 'لا توجد مطابقات';

  @override
  String get kitViewerFindPrevious => 'المطابقة السابقة';

  @override
  String get kitViewerFindNext => 'المطابقة التالية';

  @override
  String get kitViewerFindClose => 'إغلاق البحث';

  @override
  String get kitViewerCopyContents => 'نسخ المحتوى';

  @override
  String get kitViewerShowSource => 'عرض المصدر';

  @override
  String get kitViewerEmpty => 'هذا الملف فارغ';

  @override
  String kitViewerTruncated(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'تُعرض الأسطر الأولى: $shownString من $totalString';
  }

  @override
  String get kitViewerPartial => 'يُعرض جزء من هذا الملف';

  @override
  String get kitViewerOpenAll => 'فتح الملف كاملًا';

  @override
  String get kitViewerCantShow => 'تعذّر عرض هذا الملف';

  @override
  String kitViewerCantShowBody(String type, String size) {
    return '$type · $size';
  }

  @override
  String get kitViewerUnknownType => 'نوع غير معروف';

  @override
  String get kitViewerUnknownSize => 'الحجم غير معروف';

  @override
  String kitViewerLoadFailed(String name) {
    return 'تعذّر فتح $name';
  }

  @override
  String kitViewerPage(int page, int count) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'الصفحة $pageString من $countString';
  }

  @override
  String kitViewerPageFailed(int page) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);

    return 'تعذّر عرض الصفحة $pageString';
  }

  @override
  String get kitCapServerAnyTitle => 'خادم للعمل عليه';

  @override
  String get kitCapServerAnyWhy =>
      'لا يوجد خادم بعد. أعدّ خادمًا على هذا الهاتف أو اتصل بحاسوب.';

  @override
  String get kitCapServerAnyEnable => 'إضافة خادم';

  @override
  String get kitCapServerAnyOffer => 'أضف خادمًا لبدء العمل مع وكيل.';

  @override
  String get kitCapServerOc2Title => 'خادم OpenCode 2';

  @override
  String get kitCapServerOc2Why => 'يحتاج إلى خادم يشغّل OpenCode 2.';

  @override
  String get kitCapServerOc2Enable => 'الاتصال بـ OpenCode 2';

  @override
  String get kitCapServerOc2Offer =>
      'يحتاج هذا إلى OpenCode 2. هل تريد تحويل خادم هذا الهاتف إليه؟';

  @override
  String get kitCapServerCodexTitle => 'خادم Codex';

  @override
  String get kitCapServerCodexWhy => 'يحتاج إلى حاسوب يشغّل Codex.';

  @override
  String get kitCapServerCodexEnable => 'توصيل Codex';

  @override
  String get kitCapServerCodexOffer => 'اتصل بحاسوب يشغّل Codex لاستخدامه هنا.';

  @override
  String get kitCapServerPaseoTitle => 'Claude Code أو Pi';

  @override
  String get kitCapServerPaseoWhy =>
      'يحتاج إلى Paseo على حاسوب أو على هذا الهاتف.';

  @override
  String get kitCapServerPaseoEnable => 'توصيل Paseo';

  @override
  String get kitCapServerPaseoOffer =>
      'اعمل مع Claude Code أو Pi. هل تريد توصيل Paseo؟';

  @override
  String get kitCapPhoneBuiltinTitle => 'خادم على هذا الهاتف';

  @override
  String get kitCapPhoneBuiltinWhy => 'لا يوجد خادم خاص بهذا الهاتف بعد.';

  @override
  String get kitCapPhoneBuiltinEnable => 'إعداد هذا الهاتف';

  @override
  String get kitCapPhoneBuiltinOffer =>
      'شغّل الوكلاء على هذا الهاتف مباشرةً. هل تريد إعداده؟';

  @override
  String get kitCapPhoneTermuxTitle => 'خادم في Termux';

  @override
  String get kitCapPhoneTermuxWhy => 'يحتاج إلى Termux على هذا الهاتف.';

  @override
  String get kitCapPhoneTermuxEnable => 'الإعداد باستخدام Termux';

  @override
  String get kitCapPhoneTermuxOffer =>
      'هل تريد تشغيل خادم هذا الهاتف في Termux بدلًا من ذلك؟';

  @override
  String get kitCapPhoneAnyTitle => 'خادم على هذا الهاتف';

  @override
  String get kitCapPhoneAnyWhy => 'يحتاج إلى خادم يعمل على هذا الهاتف.';

  @override
  String get kitCapModelAuthTitle => 'تسجيل الدخول إلى نموذج';

  @override
  String get kitCapModelAuthWhy =>
      'سجّل الدخول إلى مزوّد نموذج ليتمكن الوكيل من الرد.';

  @override
  String get kitCapModelAuthEnable => 'تسجيل الدخول إلى نموذج';

  @override
  String get kitCapModelAuthOffer =>
      'يحتاج الوكيل إلى نموذج للرد. هل تريد تسجيل الدخول إلى نموذج؟';

  @override
  String get kitCapTeamOnTitle => 'AI Team';

  @override
  String get kitCapTeamOnWhy => 'AI Team متوقف على هذا الخادم.';

  @override
  String get kitCapTeamOnEnable => 'تشغيل AI Team';

  @override
  String get kitCapTeamOnOffer =>
      'يستطيع هذا الخادم أيضًا تشغيل فريق ذكاء اصطناعي. هل تريد تشغيله؟';

  @override
  String get kitCapTeamPhoneTitle => 'AI Team على هذا الهاتف';

  @override
  String get kitCapTeamPhoneWhy => 'يعمل فقط على خادم هذا الهاتف نفسه.';

  @override
  String get kitCapTeamControlTitle => 'أدوات التحكم بالفريق';

  @override
  String get kitCapTeamControlWhy =>
      'أجب عن هذا على الحاسوب الذي يشغّل الفريق.';

  @override
  String get kitCapTeamControlEnable => 'عرض طريقة الإعداد';

  @override
  String get kitCapTeamControlOffer =>
      'تحكّم بالفريق من هنا بعد إعداد الحاسوب. هل تريد معرفة الطريقة؟';

  @override
  String get kitCapClaudeLocalTitle => 'Claude Code على هذا الهاتف';

  @override
  String get kitCapClaudeLocalWhy =>
      'يحتاج إلى Termux على هذا الهاتف واشتراك Claude.';

  @override
  String get kitCapClaudeLocalEnable => 'إضافة Claude Code';

  @override
  String get kitCapClaudeLocalOffer =>
      'هل تريد إضافة Claude Code إلى هذا الهاتف؟ يحتاج إلى اشتراك Claude.';

  @override
  String get kitCapVoiceModelTitle => 'الكتابة بالصوت';

  @override
  String get kitCapVoiceModelWhy => 'يحتاج إلى نموذج صوتي على هذا الهاتف.';

  @override
  String get kitCapVoiceModelEnable => 'تنزيل نموذج صوتي';

  @override
  String get kitCapVoiceModelOffer =>
      'اكتب بالصوت على هذا الهاتف. هل تريد تنزيل نموذج صوتي؟';

  @override
  String get kitCapMcpAnyTitle => 'أدوات إضافية';

  @override
  String get kitCapMcpAnyWhy =>
      'لا يستطيع هذا الخادم إضافة أدوات أخرى من التطبيق.';

  @override
  String get kitCapMcpAnyEnable => 'إضافة أداة';

  @override
  String get kitCapMcpAnyOffer =>
      'امنح الوكيل أدوات إضافية. هل تريد إضافة أداة؟';

  @override
  String get kitCapProjectOpenTitle => 'المشروع';

  @override
  String get kitCapProjectOpenWhy => 'اختر مجلدًا للعمل فيه أولًا.';

  @override
  String get kitCapProjectOpenEnable => 'اختيار مشروع';

  @override
  String get kitCapProjectOpenOffer => 'اختر مجلد مشروع لبدء العمل.';

  @override
  String get kitCapProjectGitTitle => 'مشروع Git';

  @override
  String get kitCapProjectGitWhy => 'هذا المجلد ليس مشروع Git بعد.';

  @override
  String get kitCapProjectGitEnable => 'تحويل المجلد إلى مشروع Git';

  @override
  String get kitCapProjectGitOffer =>
      'يحتاج هذا إلى مشروع Git. هل تريد تحويل هذا المجلد إلى مشروع Git؟';

  @override
  String get kitCapPermNotificationsTitle => 'الإشعارات';

  @override
  String get kitCapPermNotificationsWhy => 'الإشعارات متوقفة لهذا التطبيق.';

  @override
  String get kitCapPermNotificationsEnable => 'السماح بالإشعارات';

  @override
  String get kitCapPermNotificationsOffer =>
      'تلقَّ تنبيهًا عندما يحتاجك وكيل أو ينتهي من العمل. هل تريد السماح بالإشعارات؟';

  @override
  String get kitCapPermBatteryTitle => 'العمل في الخلفية';

  @override
  String get kitCapPermBatteryWhy =>
      'قد يوقف Android التطبيق أثناء وجوده في الخلفية.';

  @override
  String get kitCapPermBatteryEnable => 'السماح بالعمل في الخلفية';

  @override
  String get kitCapPermBatteryOffer =>
      'هل تريد إبقاء الوكلاء يعملون عند إغلاق التطبيق؟';

  @override
  String get kitCapPermCameraTitle => 'الكاميرا';

  @override
  String get kitCapPermCameraWhy => 'الوصول إلى الكاميرا متوقف لهذا التطبيق.';

  @override
  String get kitCapPermCameraEnable => 'السماح بالكاميرا';

  @override
  String get kitCapPermCameraOffer =>
      'امسح رموز الاقتران وأضف صورًا. هل تريد السماح بالكاميرا؟';

  @override
  String get kitCapPermMicTitle => 'الميكروفون';

  @override
  String get kitCapPermMicWhy => 'الوصول إلى الميكروفون متوقف لهذا التطبيق.';

  @override
  String get kitCapPermMicEnable => 'السماح بالميكروفون';

  @override
  String get kitCapPermMicOffer =>
      'أمْلِ طلباتك بصوتك. هل تريد السماح بالميكروفون؟';

  @override
  String get kitCapNetworkTailscaleTitle => 'الوصول من أي مكان';

  @override
  String get kitCapNetworkTailscaleWhy =>
      'هاتفك وحاسوبك ليسا على الشبكة نفسها.';

  @override
  String get kitCapNetworkTailscaleEnable => 'إعداد Tailscale';

  @override
  String get kitCapNetworkTailscaleOffer =>
      'اتصل بحاسوبك من أي مكان باستخدام Tailscale. هل تريد إعداده؟';

  @override
  String get kitCapQuotaCollectorTitle => 'الاستخدام المتبقي';

  @override
  String get kitCapQuotaCollectorWhy =>
      'لا يُبلّغ هذا الخادم عن المتبقي من خطتك.';

  @override
  String get kitCapQuotaCollectorEnable => 'عرض طريقة الإضافة';

  @override
  String get kitCapQuotaCollectorOffer =>
      'اعرض المتبقي من خطتك هنا. هل تريد إضافته على الخادم؟';

  @override
  String get kitCapAgentA2aTitle => 'وكلاء آخرون';

  @override
  String get kitCapAgentA2aWhy => 'لم يُضف أي وكلاء آخرين بعد.';

  @override
  String get kitCapAgentA2aEnable => 'إضافة وكيل';

  @override
  String get kitCapAgentA2aOffer =>
      'اعمل مع وكلاء من تطبيقات أخرى. هل تريد إضافة وكيل؟';

  @override
  String get kitCapFlagFileBrowsingTerminalTitle => 'الملفات والطرفية';

  @override
  String get kitCapFlagFileBrowsingTerminalWhy =>
      'لا يتيح هذا الخادم الوصول إلى ملفاته أو طرفيته.';

  @override
  String get kitCapFlagSessionDiffTitle => 'مراجعة التغييرات';

  @override
  String get kitCapFlagSessionDiffWhy =>
      'لا يعرض هذا الخادم التغييرات التي أجراها الوكيل.';

  @override
  String get kitCapFlagServerCatalogTitle => 'إعدادات الخادم';

  @override
  String get kitCapFlagServerCatalogWhy =>
      'لا يتيح هذا الخادم الوصول إلى مزوّدي الخدمة أو أدواته أو أوامره.';

  @override
  String get kitCapFlagUsageStatisticsTitle => 'الإنفاق';

  @override
  String get kitCapFlagUsageStatisticsWhy => 'لا يُبلّغ هذا الخادم عن الإنفاق.';

  @override
  String get kitCapFlagStagedRevertSessionNotesTitle =>
      'الملاحظات والتراجع خطوة بخطوة';

  @override
  String get kitCapFlagStagedRevertSessionNotesWhy =>
      'لا يستطيع هذا الخادم حفظ ملاحظات للوكيل أو التراجع خطوة بخطوة.';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle =>
      'المهام المعزولة والمشاركة';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy =>
      'لا يستطيع هذا الخادم تشغيل مهام معزولة أو مشاركة المحادثات.';

  @override
  String get kitCapFlagDevelopmentServicesTitle => 'خدمات التطوير';

  @override
  String get kitCapFlagDevelopmentServicesWhy =>
      'لا يستطيع هذا الخادم تشغيل خدمات التطوير أو إيقافها.';

  @override
  String get kitCapFlagRemoteUpgradeTitle => 'تحديث الخادم';

  @override
  String get kitCapFlagRemoteUpgradeWhy =>
      'لا يمكن تحديث هذا الخادم من التطبيق.';

  @override
  String get kitCapFlagPromptAttachmentsTitle => 'إرفاق الصور والملفات';

  @override
  String get kitCapFlagPromptAttachmentsWhy =>
      'لا يستطيع هذا الخادم قبول صور أو ملفات مع الطلب.';

  @override
  String get kitHostThisPhone => 'هذا الهاتف';

  @override
  String get kitHostTermux => 'Termux';

  @override
  String get kitHostOpenCode2 => 'الحواسيب التي تشغّل OpenCode 2';

  @override
  String get kitHostCodex => 'Codex';

  @override
  String get kitHostPaseo => 'Paseo';

  @override
  String get kitHostDemo => 'العرض التوضيحي دون اتصال';

  @override
  String get kitHostOpenCode => 'الحواسيب التي تشغّل OpenCode';

  @override
  String kitCapNotOnHost(int count, String feature, String host) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$feature غير متاحة على $host',
      one: '$feature غير متاح على $host',
    );
    return '$_temp0';
  }

  @override
  String kitCapWorksOn(String hosts) {
    return 'يعمل على $hosts';
  }

  @override
  String kitCapWhyElsewhere(String notHere, String worksOn) {
    return '$notHere. $worksOn.';
  }

  @override
  String kitCapAnd(String first, String last) {
    return '$first و$last';
  }

  @override
  String kitCapComma(String first, String next) {
    return '$first, $next';
  }

  @override
  String kitCapServerOnHost(String server, String host) {
    return '$server ($host)';
  }

  @override
  String get kitCapNotNow => 'ليس الآن';

  @override
  String get desktopDropHint => 'أفلت الملف لإرفاقه';

  @override
  String get searchClaudeCodeGateTitle => 'غير متاح على هذا الجهاز';

  @override
  String get searchClaudeCodeGateDevice =>
      'يعمل Claude Code على الهاتف فقط عبر Termux، وهو غير موجود على هذا الجهاز. شغّله على حاسوب باستخدام Paseo وأضف ذلك الحاسوب كخادم.';

  @override
  String get searchClaudeCodeGateDesktop =>
      'Claude Code على هذا الهاتف مخصّص لهواتف Android التي تحتوي على Termux. على الحاسوب، شغّل Claude Code باستخدام Paseo وأضفه كخادم.';

  @override
  String get searchClaudeCodeGateServers => 'فتح الخوادم';

  @override
  String get activitySendOffline => 'أعِد الاتصال بالخادم للإجابة.';

  @override
  String get activityAnswerEveryQuestion => 'أجب عن كل سؤال أولًا.';

  @override
  String get activityQuestionOptional =>
      'اختياري. يمكنك ترك هذا السؤال فارغاً.';

  @override
  String get activitySending => 'جارٍ الإرسال…';

  @override
  String activityQuestionProgress(int index, int total) {
    return 'السؤال $index من $total';
  }

  @override
  String get activityOwnAnswer => 'أو اكتب إجابتك الخاصة';

  @override
  String get shortcutsPaletteSearch => 'ابحث أو انتقل إلى…';

  @override
  String get shortcutsHelpAnywhere => 'في أي مكان';

  @override
  String get shortcutsHelpConversation => 'في محادثة';

  @override
  String get homeShellProjectUnavailable => 'الملفات غير متاحة';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return 'لا يتيح $server الملفات أو التغييرات أو البحث في الشيفرة. اتصل بخادم OpenCode لاستخدامها.';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return 'لا تتوفر أدوات المشروع لدى $server.';
  }

  @override
  String get workspaceContextFolder => 'المجلد';

  @override
  String get workspaceSessionSharedLink => 'رابط المشاركة';

  @override
  String get managedWorkspacesRefresh => 'تحديث البيئات';

  @override
  String get managedWorkspacesDiscovered => 'اكتمل الاكتشاف';

  @override
  String get managedWorkspacesDiscoverFailed => 'تعذّر اكتشاف البيئات';

  @override
  String get managedWorkspacesCreateFailed => 'تعذّر إنشاء البيئة';

  @override
  String managedWorkspacesOpenFailed(String name) {
    return 'تعذّر فتح $name';
  }

  @override
  String managedWorkspacesRemoveTitle(String name) {
    return 'هل تريد إزالة $name؟';
  }

  @override
  String managedWorkspacesRemoveBody(String provider) {
    return 'يطلب الخادم من $provider حذف هذه البيئة ومحتوياتها.';
  }

  @override
  String get managedWorkspacesRemoveLeavesFirst =>
      'البيئة مفتوحة الآن، لذا يعود التطبيق إلى مجلد المشروع أولًا.';

  @override
  String get managedWorkspacesRemoveHistoryStays =>
      'تبقى المحادثات في السجل، لكن لا يعود بإمكانها فتح البيئة.';

  @override
  String get managedWorkspacesRemoveAction => 'إزالة البيئة';

  @override
  String managedWorkspacesRemoved(String name) {
    return 'أُزيلت $name';
  }

  @override
  String get managedWorkspacesProvider => 'مزوّد الخدمة';

  @override
  String get managedWorkspacesProvidersFailed => 'تعذّر تحميل مزوّدي الخدمة';

  @override
  String get managedWorkspacesNoProviderTitle => 'لم يُعَدّ مزوّد خدمة';

  @override
  String get managedWorkspacesNoProviderBody =>
      'ليس لدى هذا الخادم مزوّد بيئات سحابية. أضف مزوّدًا إلى إعدادات OpenCode على الخادم، ثم حدّث.';

  @override
  String managedWorkspacesEmptyBody(String project) {
    return 'تظهر بيئات $project هنا. أنشئ بيئة أو اكتشف البيئات الموجودة لدى مزوّد الخدمة.';
  }

  @override
  String get managedWorkspacesLoadFailed => 'تعذّر تحميل البيئات السحابية';

  @override
  String get managedWorkspacesCreating => 'جارٍ إنشاء بيئة سحابية';

  @override
  String get managedWorkspacesCreatingBody =>
      'يستغرق ذلك بضع دقائق عادةً. تُفتح هنا عندما تصبح جاهزة.';

  @override
  String get managedWorkspacesCreateTakes =>
      'يستغرق إنشاء البيئة بضع دقائق عادةً. تُفتح هنا عندما تصبح جاهزة.';

  @override
  String get managedWorkspacesBranchLabel => 'الفرع';

  @override
  String get managedWorkspacesBranchHelper =>
      'اتركه فارغًا لاستخدام الفرع الافتراضي لمزوّد الخدمة.';

  @override
  String get managedWorkspacesInUse => 'قيد الاستخدام';

  @override
  String get managedWorkspacesCopyId => 'نسخ المعرّف';

  @override
  String get projectHealthGitInitSupporting =>
      'يشغّل git init هنا. لا تُحفظ أي تغييرات في سجل Git.';

  @override
  String get projectHealthSetUp => 'إعداد المشروع';

  @override
  String get projectHealthRunning => 'قيد التشغيل';

  @override
  String get projectHealthNotRunning => 'متوقف';

  @override
  String projectHealthLineCounts(int added, int removed) {
    return 'أُضيف $added سطرًا وحُذف $removed سطرًا';
  }

  @override
  String projectFolderCreateHelper(String directory) {
    return 'يُنشأ في $directory على هذا الهاتف ويُفتح كمشروع.';
  }

  @override
  String get projectFolderMissingTitle => 'هل تريد إنشاء هذا المجلد؟';

  @override
  String get projectFolderCreateFailedTitle => 'تعذّر إنشاء المجلد';

  @override
  String get projectFolderOpenFailedTitle => 'تعذّر فتح المجلد';

  @override
  String get projectsOneFolderTitle => 'يستخدم الخادم مجلدًا واحدًا';

  @override
  String servicesStarted(String name) {
    return 'بدأ تشغيل $name';
  }

  @override
  String servicesRemoved(String name) {
    return 'أُزيلت $name';
  }

  @override
  String servicesStopTitle(String name) {
    return 'هل تريد إيقاف $name؟';
  }

  @override
  String servicesRestartTitle(String name) {
    return 'هل تريد إعادة تشغيل $name؟';
  }

  @override
  String servicesForgetTitle(String name) {
    return 'هل تريد مسح سجل التشغيل الأخير لـ$name؟';
  }

  @override
  String servicesRemoveTitle(String name) {
    return 'هل تريد إزالة $name؟';
  }

  @override
  String get servicesRemoveRunningHint =>
      'يستمر أمرها في العمل على الخادم، ولا يعود بإمكان هذا التطبيق إيقافه. أوقفه أولًا لإنهائه.';

  @override
  String get servicesEmptyTitle => 'لا توجد أوامر تطوير بعد';

  @override
  String get servicesOffline =>
      'لا يستجيب الخادم. لا يمكن بدء الأوامر أو التحقّق منها حتى يُعاد الاتصال.';

  @override
  String get servicesLogFailed => 'تعذّرت قراءة السجل.';

  @override
  String get servicesProjectFolder => 'مجلد المشروع';

  @override
  String get servicesWorkspace => 'البيئة';

  @override
  String get servicesNameRequired => 'أدخل اسمًا.';

  @override
  String get servicesDuplicateName => 'توجد خدمة بهذا الاسم بالفعل.';

  @override
  String get servicesCommandRequired => 'أدخل أمرًا، مثل npm run dev.';

  @override
  String get servicesUrlInvalid =>
      'أدخل عنوان http أو https دون اسم مستخدم أو كلمة مرور.';

  @override
  String get isolatedTaskProjectFolder => 'مجلد المشروع';

  @override
  String get isolatedTaskStageCreate => 'جارٍ إنشاء النسخة';

  @override
  String get isolatedTaskStagePrepare => 'جارٍ إعداد المشروع';

  @override
  String get isolatedTaskStageOpen => 'جارٍ فتح المحادثة';

  @override
  String get isolatedTaskUsually => 'عادةً من 1 إلى 3 دقائق';

  @override
  String get savedPermissionsIntro =>
      'الإجراءات التي يمكن للوكيل تنفيذها في هذا المشروع دون سؤالك أولًا. ألغِ السماح بإجراء ليطلب الوكيل إذنك مجددًا.';

  @override
  String get savedPermissionsLoadFailed =>
      'تعذّر تحميل الإجراءات المسموح بها دائمًا';

  @override
  String get savedPermissionsRevokeBody =>
      'سيطلب الوكيل إذنك مجددًا في المرة القادمة التي يريد فيها تنفيذ هذا الإجراء. يستمر العمل الجاري بالفعل.';

  @override
  String savedPermissionsRevokedDetail(String action) {
    return 'عاد $action إلى طلب إذنك أولًا.';
  }

  @override
  String get savedPermissionsDismiss => 'إغلاق التنبيه';

  @override
  String get savedPermissionsCopyPattern => 'نسخ النمط';

  @override
  String get savedPermissionsBusy => 'انتظر حتى ينتهي التغيير الحالي';

  @override
  String get savedPermissionsLoading =>
      'جارٍ تحميل الإجراءات المسموح بها دائمًا';

  @override
  String get savedPermissionsAllResources =>
      'كل ما يمسّه هذا النوع من الإجراءات';

  @override
  String get settingsHubDetailEmpty => 'اختر مجموعة إعدادات';

  @override
  String get notifyQuietStartPicker => 'تحديد بداية ساعات الهدوء';

  @override
  String get notifyQuietEndPicker => 'تحديد نهاية ساعات الهدوء';

  @override
  String get notifyQuietSet => 'تحديد الوقت';

  @override
  String get notifyQuietAllDay =>
      'البداية والنهاية متطابقتان، لذا تبقى الإشعارات صامتة طوال اليوم.';

  @override
  String get notifySendingTest => 'جارٍ إرسال إشعار تجريبي…';

  @override
  String get notifyNoServersTitle => 'لا توجد خوادم لمتابعتها';

  @override
  String get notifyNoServersDetail =>
      'يمكن متابعة الخوادم التي تحفظها من هنا، لتصلك طلباتها.';

  @override
  String get notifyDismiss => 'إغلاق التنبيه';

  @override
  String get notifySaving => 'جارٍ الحفظ';

  @override
  String get notifyMonitorDetails => 'كيف تعمل متابعة الخوادم';

  @override
  String get notifyRestartBackground => 'إعادة تشغيل الاتصال المباشر';

  @override
  String get privacySharedSection => 'ما يُشارك مع خادمك';

  @override
  String get privacySaving => 'جارٍ الحفظ…';

  @override
  String get privacyDeleting => 'جارٍ الحذف';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حذف الطلبات في قائمة الانتظار وعددها $count',
      one: 'حذف طلب واحد في قائمة الانتظار',
      zero: 'حذف الطلبات في قائمة الانتظار',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حذف المسودات وعددها $count',
      one: 'حذف مسودة واحدة',
      zero: 'حذف المسودات',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => 'ما قلته';

  @override
  String get kitMessageThinking => 'جارٍ التفكير…';

  @override
  String get kitMessageThought => 'التفكير';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ث',
      one: 'ثانية واحدة',
    );
    return 'مدة التفكير: $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count د',
      one: 'دقيقة واحدة',
    );
    return 'مدة التفكير: $_temp0';
  }

  @override
  String get kitMessageActions => 'إجراءات الرسالة';

  @override
  String get kitMessageNoticeFailed => 'فشل الإجراء';

  @override
  String get kitRequestChooseOneReason => 'اختر إجابة واحدة على الأقل.';

  @override
  String get kitRequestSendAnswers => 'إرسال الإجابات';

  @override
  String get serversRemoveBody =>
      'يمسح هذا الهاتف بيانات الخادم: كلمة مروره والنموذج والوكيل المختارين والمشروع ومحادثات أداة الشاشة الرئيسية.';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'ستُحذف المسودات غير المرسلة وعددها $count',
      one: 'ستُحذف مسودة واحدة لم تُرسل',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext =>
      'أنت متصل به: يقطع التطبيق الاتصال ويعرض خوادمك';

  @override
  String get serversRemoveServerKeeps =>
      'لا يُحذف شيء على الخادم أو لدى مزوّدي الذكاء الاصطناعي';

  @override
  String get guideStepTwoScan =>
      'اضغط على إضافة خادم، ثم مسح الرمز ووجّه الكاميرا نحو رمز QR، أو اختر لصق الرمز.';

  @override
  String get guideStepTwoPaste =>
      'انسخ الرمز المطبوع، ثم اضغط على إضافة خادم ولصق الرمز.';

  @override
  String get guidePhonePathTitle => 'استخدام هذا الهاتف بدلًا من ذلك';

  @override
  String get guidePhonePathBody =>
      'ثبّت OpenCode على هذا الهاتف واستخدمه هنا، دون الحاجة إلى حاسوب';

  @override
  String get pairingScannerAllowCamera => 'السماح بالكاميرا';

  @override
  String get pairingScannerStarting => 'جارٍ فتح الكاميرا…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return 'هناك عمل جارٍ على $current. يعرض التبديل $target في هذا التطبيق؛ يستمر العمل على $current.';
  }

  @override
  String get profileMonitorOpenFailedTitle => 'تعذّر فتحه';

  @override
  String get profileMonitorIfIgnored => 'ينتظر الوكيل حتى تجيب';

  @override
  String get serverSettingsRestartedIt => 'أعدت تشغيله';

  @override
  String serverSettingsUpgradeBody(
    String target,
    String server,
    String current,
  ) {
    return 'يثبّت OpenCode $target على $server (الإصدار الحالي $current) باستخدام برنامج التثبيت الخاص بالخادم.';
  }

  @override
  String serverSettingsUpgradeKeepsRunning(String current) {
    return 'يواصل الخادم تشغيل $current أثناء التثبيت';
  }

  @override
  String serverSettingsUpgradeRestartAfter(String target) {
    return 'أعد تشغيل عملية OpenCode على حاسوب الخادم لاستخدام $target';
  }

  @override
  String get serverSettingsUpgradeKeepsData => 'تبقى بيانات الخادم في مكانها';

  @override
  String serverSettingsCopyUpdateCommands(String server) {
    return 'نسخ أوامر تحديث $server';
  }

  @override
  String get serverSettingsAddressLabel => 'العنوان';

  @override
  String get tailscaleSetupAppTitle => 'Tailscale على هذا الهاتف';

  @override
  String get tailscaleSetupVpnTitle => 'تسجيل الدخول والاتصال';

  @override
  String get tailscaleSetupVpnSupporting =>
      'سجّل الدخول واتصل. لا يمكن لـOpenCode التحقّق من ذلك.';

  @override
  String get tailscaleSetupOpenFailed =>
      'لم يُفتح Tailscale. افتحه من قائمة تطبيقاتك، ثم عد.';

  @override
  String get tailscaleSetupAddressHelper =>
      'الصق عنوان HTTPS الذي طبعه Tailscale Serve.';

  @override
  String get tailscaleSetupGetApp => 'الحصول على Tailscale';

  @override
  String get tailscaleSetupContinueReason => 'أدخل عنوان خادمك أولًا.';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return 'مترجمة جزئيًا ($percent %)';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return 'معاينة $name';
  }

  @override
  String get appearancePickerPreviewIn => 'المعاينة في';

  @override
  String get appearancePickerInUse => 'قيد الاستخدام الآن';

  @override
  String appearancePickerThemeApplied(String name) {
    return 'حُدّدت السمة إلى $name';
  }

  @override
  String get serverSwitcherTitle => 'الخوادم';

  @override
  String get serverSwitcherCurrentMenu => 'إجراءات الخادم';

  @override
  String get localAgentEntryStillStarting =>
      'لا يزال يبدأ التشغيل · قد يستغرق دقيقة';

  @override
  String get localAgentEntryDidNotStart => 'لم يبدأ التشغيل';

  @override
  String get localAgentEntryRemoving => 'جارٍ الإزالة';

  @override
  String localAgentEntrySignedOut(String state) {
    return '$state · لم يُسجّل الدخول إلى Claude';
  }

  @override
  String get formRendererSending => 'جارٍ إرسال إجاباتك…';

  @override
  String get formRendererChoose => 'اختيار قيمة';

  @override
  String get formRendererChooseDate => 'اختيار تاريخ';

  @override
  String get formRendererChooseDateTime => 'اختيار تاريخ ووقت';

  @override
  String formRendererDateAndTime(String date, String time) {
    return '$date في $time';
  }

  @override
  String get formRendererUseDate => 'استخدام التاريخ';

  @override
  String get filePreviewPdfIsolated =>
      'لا تُعرض صفحات PDF في هذا العرض المعزول. احفظ الملف لقراءته في تطبيق PDF.';

  @override
  String get filePreviewCopyOriginal => 'نسخ الملف الأصلي';

  @override
  String get filePreviewOpenInFiles => 'فتح الملف في الملفات';

  @override
  String get filePreviewViewMode => 'طريقة عرض الملف';

  @override
  String get filePreviewAttachFailed => 'تعذّر إرفاق الملف';

  @override
  String get filePreviewSaveFailed => 'تعذّر حفظ الملف';

  @override
  String get kitTurnStarting => 'جارٍ بدء تشغيل النموذج…';

  @override
  String kitTurnStillStarting(int seconds) {
    return 'لا يزال الانتظار لاستجابة النموذج جاريًا · $seconds ث';
  }

  @override
  String get kitTurnStopped => 'أوقفت هذا الرد.';

  @override
  String get kitTurnInterrupted => 'انقطع الاتصال قبل اكتمال هذا الرد.';

  @override
  String get kitTurnCopy => 'نسخ الرد';

  @override
  String get kitTurnMore => 'المزيد لهذا الرد';

  @override
  String get kitTurnActions => 'إجراءات الرد';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return 'قطع الاتصال بـ $serverName';
  }

  @override
  String get kitScannerStarting => 'جارٍ فتح الكاميرا…';

  @override
  String get kitScannerSlow => 'لا يزال فتح الكاميرا جاريًا';

  @override
  String get kitScannerPaused => 'الكاميرا متوقفة مؤقتًا';

  @override
  String get kitScannerPreview => 'عرض الكاميرا';

  @override
  String get kitDateSet => 'تحديد التاريخ';

  @override
  String get kitTimeSet => 'تحديد الوقت';

  @override
  String get kitDateTimeSet => 'تحديد التاريخ والوقت';

  @override
  String get kitDateType => 'كتابة تاريخ';

  @override
  String get kitDateCalendar => 'عرض التقويم';

  @override
  String kitDateFormatHint(String example) {
    return 'مثلًا: $example';
  }

  @override
  String get kitDateField => 'التاريخ';

  @override
  String get kitDateInvalid => 'ليس تاريخًا';

  @override
  String kitDateOutOfRange(String first, String last) {
    return 'اختر تاريخًا بين $first و$last';
  }

  @override
  String get kitTimeHour => 'الساعة';

  @override
  String get kitTimeMinute => 'الدقيقة';

  @override
  String get kitTimePeriod => 'صباحًا أو مساءً';

  @override
  String get kitTimeInvalid => 'وقت غير صالح';

  @override
  String get kitDateTimeNotSet => 'غير محدد';

  @override
  String kitDateTimeClear(String title) {
    return 'مسح $title';
  }

  @override
  String get kitDateUnavailable => 'لا يمكن اختيار ذلك اليوم';

  @override
  String get filesLoadingFolder => 'جارٍ فتح المجلد…';

  @override
  String get filesSearching => 'جارٍ البحث…';

  @override
  String get filesShowHidden => 'إظهار الملفات المخفية';

  @override
  String get filesOnlyHidden =>
      'لا يحتوي هذا المجلد إلا على ملفات ومجلدات مخفية.';

  @override
  String get filesCopyName => 'نسخ الاسم';

  @override
  String globalSessionsMoveTitle(String project) {
    return 'هل تريد النقل إلى $project؟';
  }

  @override
  String globalSessionsMoveBody(String title, String from, String to) {
    return 'تُنقل «$title» من $from إلى $to عبر نظام مزامنة الخادم.';
  }

  @override
  String get globalSessionsMoveWhileWorking =>
      'المحادثة تعمل الآن. قد يقاطع نقلها الخطوة الحالية.';

  @override
  String globalSessionsMoveBack(String project) {
    return 'لإعادتها، افتح $project واختر «المتابعة هنا» في كل المحادثات.';
  }

  @override
  String get globalSessionsFilterLabel => 'عرض';

  @override
  String get globalSessionsFilterActive => 'النشطة';

  @override
  String get globalSessionsArchivedNoMatchMessage =>
      'لا توجد محادثة مؤرشفة بهذا العنوان. جرّب بحثًا أقصر.';

  @override
  String get globalSessionsArchivedEmptyTitle => 'لا توجد محادثات مؤرشفة';

  @override
  String get globalSessionsArchivedEmptyMessage =>
      'تظهر هنا المحادثات التي تؤرشفها في المهام.';

  @override
  String get globalSessionsShowActive => 'عرض المحادثات النشطة';

  @override
  String globalSessionsProjectInUse(String project) {
    return '$project · قيد الاستخدام';
  }

  @override
  String get globalSessionsCopyFolder => 'نسخ مسار المجلد';

  @override
  String get worktreesStartConversation => 'بدء محادثة جديدة هنا';

  @override
  String get worktreesCreateHelper =>
      'يُنشأ فرع ومجلد منفصلان وتُشغَّل مهام بدء المشروع. تُستبدل المسافات بشرطات.';

  @override
  String get worktreesFolder => 'المجلد';

  @override
  String get worktreesMainCopy => 'النسخة الرئيسية';

  @override
  String get worktreesCopyFolder => 'نسخ مسار المجلد';

  @override
  String get worktreesLoadFailedTitle => 'تعذّر تحميل أشجار العمل';

  @override
  String get worktreesSetupFailedWord => 'فشل الإعداد';

  @override
  String get importNeedsFile => 'اختر ملف JSON أولًا.';

  @override
  String get importNeedsDestination => 'اختر وجهة الاستيراد أولًا.';

  @override
  String get importFileLabel => 'الملف';

  @override
  String get importPreviewLabel => 'المحادثة';

  @override
  String importMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عدد الرسائل: $count',
      one: 'رسالة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get importNoDestinationsTitle => 'لا توجد وجهة للاستيراد';

  @override
  String get importChangeDestinationShort => 'تغيير الوجهة';

  @override
  String get importConversationId => 'معرّف المحادثة';

  @override
  String get importParentId => 'معرّف المحادثة الأم';

  @override
  String get importFolder => 'المجلد';

  @override
  String get importEnvironmentId => 'معرّف البيئة السحابية';

  @override
  String get phoneSetupStartUseTermuxOne => 'استخدام النسخة في Termux';

  @override
  String get phoneSetupStartUseTermuxOneDetail =>
      'أُعدّ OpenCode أيضًا في Termux. اتصل به بدلًا من ذلك.';

  @override
  String get phoneSetupStartTermuxNotAllowed =>
      'Termux مثبّت، لكنه لم يسمح لهذا التطبيق بالوصول بعد. أكمل إعداده.';

  @override
  String get phoneSetupCustomizeAllInstalled =>
      'كل الأدوات الاختيارية موجودة على هذا الهاتف بالفعل.';

  @override
  String get phoneSetupCustomizeIncluded => 'مطلوب';

  @override
  String get termuxProcsLoadFailedTitle => 'تعذّرت قراءة العمليات الجارية';

  @override
  String get termuxProcsEmptyBody =>
      'عند تشغيل OpenCode أو AI Team أو عملية بناء هنا، تظهر في هذه القائمة.';

  @override
  String get termuxProcsNotStoppedTitle => 'لم تتوقف كل العمليات';

  @override
  String get termuxProcsCopyCommand => 'نسخ الأمر';

  @override
  String get termuxProcsOpenControls => 'فتح هذا الهاتف';

  @override
  String get termuxProcsProcessId => 'معرّف العملية';

  @override
  String get termuxProcsParentId => 'معرّف العملية الأم';

  @override
  String get termuxProcsAboutOpenCode => 'جزء من خادم OpenCode على هذا الهاتف.';

  @override
  String get termuxProcsAboutAiTeam =>
      'جزء من AI Team. إيقافه يوقف العمل الذي ينفّذه الفريق.';

  @override
  String get termuxProcsAboutBuild =>
      'عملية مساعدة للبناء. تبدأ مجددًا عند حاجة عملية البناء التالية إليها.';

  @override
  String get termuxProcsAboutOrphan =>
      'لا توجد عملية تنتظرها، لذا يمكن إيقافها بأمان.';

  @override
  String get termuxProcsAboutOther => 'بدأتها جهة أخرى على هذا الهاتف.';

  @override
  String get termuxProcsNoRestart => 'لا يمكن تشغيلها مجددًا من هنا.';

  @override
  String get termuxProcsStopGroupTeamLost =>
      'تتوقف أيضًا كل مهمة يعمل عليها الفريق.';

  @override
  String get termuxProcsStopGroupTeamRestart =>
      'يمكنك تشغيل الفريق مجددًا من AI Team.';

  @override
  String get phoneSetupProgressStopContinueLater =>
      'يمكنك المتابعة في أي وقت من «على هذا الهاتف».';

  @override
  String get teamMergeFailedNext =>
      'لم يُدمج شيء. عالج ما يذكره المضيف، ثم حاول مجددًا، أو راجع التغييرات.';

  @override
  String get teamStartRunRefusedKept =>
      'لا تزال مهمتك هنا. عدّلها وأرسلها مجددًا.';

  @override
  String get transcriptTogglesReasoningOn =>
      'عند التفعيل، يُفتح استدلال النموذج أسفل كل رد.';

  @override
  String get transcriptTogglesUsageOn =>
      'عند التفعيل، تعرض كل رسالة وقتها ورموزها وتكلفتها.';

  @override
  String get transcriptTogglesScope =>
      'تسري هذه الخيارات على كل المحادثات على هذا الجهاز.';

  @override
  String get handoffSheetCopyCommand => 'نسخ الأمر';

  @override
  String get handoffSheetReloadConversation => 'حاول مجددًا';

  @override
  String get handoffSheetPhoneServerNote =>
      'إذا لم يُحفظ هذا الخادم على الهاتف الآخر بعد، يوضح ذلك ويتيح فتح الخوادم لإضافته.';

  @override
  String get modelPickerChooseFirst => 'اختر نموذجًا أولًا.';

  @override
  String get modelPickerThinking => 'التفكير';

  @override
  String get modelPickerAgentBuild => 'يعدّل الملفات ويشغّل الأوامر';

  @override
  String get modelPickerAgentPlan => 'يقرأ ويخطط؛ لا يغيّر الملفات';

  @override
  String modelPickerDetailsOutput(String count) {
    return 'حتى $count رمزًا لكل إجابة';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '$input لكل مليون رمز مقروء، و$output لكل مليون رمز مكتوب';
  }

  @override
  String get modelPickerCanThink => 'يفكّر قبل الإجابة';

  @override
  String get modelPickerCanUseTools => 'يستخدم الأدوات';

  @override
  String get modelPickerCanReadAttachments => 'يقرأ الصور والملفات التي ترفقها';

  @override
  String get modelPickerCopyId => 'نسخ معرّف النموذج';

  @override
  String get modelPickerInUse => 'قيد الاستخدام';

  @override
  String get modelPickerUnavailableReason => 'غير متاح على هذا الخادم حاليًا.';

  @override
  String get modelPickerCollections => 'النماذج التي تريد عرضها';

  @override
  String get modelPickerSignInTitle => 'يلزم تسجيل الدخول إلى مزوّد الخدمة';

  @override
  String get modelPickerSignInBody =>
      'لا تتوفر نماذج لدى أي مزوّد خدمة على هذا الخادم بعد. سجّل الدخول إلى أحدهم، ثم عد لاختيار نموذج.';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض $count نماذج أخرى',
      one: 'عرض نموذج آخر',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'البناء';

  @override
  String get modelPickerAgentPlanName => 'التخطيط';

  @override
  String phoneServerCardDisconnect(String server) {
    return 'قطع الاتصال بـ $server';
  }

  @override
  String get phoneServerCardStartOpenCode => 'تشغيل OpenCode';

  @override
  String get phoneServerCardStopOpenCode => 'إيقاف OpenCode على هذا الهاتف';

  @override
  String get phoneServerCardShowServerLog => 'عرض سجل الخادم';

  @override
  String get phoneServerCardOpenTerminal => 'فتح الطرفية';

  @override
  String get phoneServerCardFailedTitle => 'تعذّر الإكمال';

  @override
  String get phoneServerRestartFailedTitle => 'تعذّرت إعادة التشغيل';

  @override
  String get setupTerminalTitle => 'مخرجات الإعداد';

  @override
  String get teamPhoneStopTeam => 'إيقاف الفريق';

  @override
  String get teamPhoneStartTeam => 'تشغيل الفريق';

  @override
  String get teamPhoneStartTeamAgain => 'تشغيل الفريق مجددًا';

  @override
  String get teamPhoneDeleteTeam => 'حذف الفريق من هذا الهاتف';

  @override
  String get teamPhoneRemoveBody => 'يتوقف الفريق، ويُوقف AI Team لهذا الخادم.';

  @override
  String get teamPhoneRemoveLost => 'تُحذف برامج الفريق وملفاته وقائمة مهامه';

  @override
  String get teamPhoneRemoveKept => 'تبقى ملفات مشروعك وسجل git الخاص بها';

  @override
  String teamPhoneRemoveFrees(int size) {
    return 'يوفّر نحو $size ميغابايت';
  }

  @override
  String get teamPhoneRemoveConfirm => 'حذف الفريق';

  @override
  String get productStatesActionFailedTitle => 'تعذّر إكمال ذلك';

  @override
  String get productStatesSwitchServer => 'تبديل الخادم';

  @override
  String get externalLinkBlockedTitle => 'الرابط محظور';

  @override
  String get externalLinkBlockedBody =>
      'يفتح هذا التطبيق روابط https:// فقط، وروابط http:// بعد تأكيدك.';

  @override
  String externalLinkOpensHost(String host) {
    return 'يفتح $host خارج هذا التطبيق.';
  }

  @override
  String get externalLinkDontOpen => 'إلغاء الفتح';

  @override
  String get externalLinkCopy => 'نسخ الرابط';

  @override
  String get externalLinkAddress => 'العنوان الكامل';

  @override
  String get externalLinkOpenFailedTitle => 'تعذّر فتح الرابط';

  @override
  String get runCommandReconnecting =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا بعد قليل.';

  @override
  String runCommandArgumentsHelper(String command) {
    return 'النص الذي يُمرّر إلى /$command. اتركه فارغًا إذا لم يحتج الأمر إلى معاملات.';
  }

  @override
  String get runCommandRunsIn => 'يعمل في';

  @override
  String runCommandFailedTitle(String command) {
    return 'تعذّر تشغيل /$command';
  }

  @override
  String get teamNowWakeRefusedNoReason => 'لم يذكر المضيف السبب.';

  @override
  String get teamHostFormTeamLabel => 'اسم الفريق (اختياري)';

  @override
  String get teamHostFormTeamHelper =>
      'اتركه فارغًا لاستخدام الفريق الذي يشغّله الحاسوب.';

  @override
  String get teamHostFormHowAction => 'كيفية إعداد الحاسوب';

  @override
  String get teamHostFormCancelTest => 'إلغاء الاختبار';

  @override
  String get teamHostFormSaveAnyway => 'حفظ العنوان على أي حال';

  @override
  String get teamHostFormSaveAnywayNote =>
      'يعرض AI Team الفريق على أنه لا يستجيب حتى يستجيب الحاسوب.';

  @override
  String get teamHostFormConnectionDetails => 'تفاصيل الاتصال';

  @override
  String teamAgentScreenPause(String agent) {
    return 'إيقاف $agent مؤقتًا';
  }

  @override
  String teamAgentScreenPaused(String agent) {
    return 'أُوقف $agent مؤقتًا';
  }

  @override
  String teamAgentScreenResume(String agent) {
    return 'تشغيل $agent مجددًا';
  }

  @override
  String teamAgentScreenNudge(String agent) {
    return 'تنبيه $agent';
  }

  @override
  String teamAgentScreenRestart(String agent) {
    return 'إعادة تشغيل $agent';
  }

  @override
  String teamAgentScreenStop(String agent) {
    return 'إيقاف $agent';
  }

  @override
  String teamAgentScreenStopBody(String agent, String task) {
    return 'يتوقف $agent الآن عن العمل على «$task». تبقى المهمة على المضيف، ويمكنك تشغيل $agent مجددًا من هذه الصفحة.';
  }

  @override
  String teamAgentScreenStoppedTitle(String agent) {
    return '$agent متوقف';
  }

  @override
  String get teamAgentScreenStoppedBody =>
      'يبقى عمله كما هو. شغّله مجددًا عندما تريد استئناف عمله.';

  @override
  String teamAgentScreenCrashedTitle(String agent) {
    return 'توقف $agent بشكل غير متوقع';
  }

  @override
  String get teamAgentScreenCrashedBody =>
      'انتهت جلسته من تلقاء نفسها. شغّله مجددًا لاستئناف عمله من المضيف.';

  @override
  String get teamAgentScreenRecyclingBody =>
      'يبدأ جلسة جديدة قريبًا ويستأنف عمله من المضيف.';

  @override
  String teamAgentScreenModelFrom(String model, String provider) {
    return '$model من $provider';
  }

  @override
  String teamAgentScreenGateIfIgnored(String agent) {
    return 'ينتظر $agent حتى تجيب';
  }

  @override
  String teamAgentScreenControlsElsewhere(String agent) {
    return 'لا يمكن لهذا الهاتف إيقاف $agent مؤقتًا أو نهائيًا أو مراسلته على هذا المضيف بعد. شغّل واجهة مضيف الفريق على الحاسوب للتحكم فيه من هنا.';
  }

  @override
  String get gateSheetDestructiveBody =>
      'يصنّف المضيف هذا الإجراء على أنه إتلافي. لا يمكن التراجع عن الموافقة عليه من الهاتف.';

  @override
  String get gateSheetAnswerLabel => 'إجابتك';

  @override
  String get gateSheetAfterAnswer =>
      'يتابع الفريق بمجرد أن يؤكّد المضيف إجابتك.';

  @override
  String get gateSheetFixIt => 'طلب إصلاح المشكلة من الفريق';

  @override
  String gateSheetFixItDetail(String agent) {
    return 'يرسل الخطأ إلى $agent ويطلب منه معرفة السبب والمتابعة.';
  }

  @override
  String gateSheetFixRequest(String task, String error) {
    return 'فشلت المهمة «$task» بهذا الخطأ:\n$error\nيرجى معرفة السبب وإصلاحه والمتابعة.';
  }

  @override
  String gateSheetOpenAgent(String agent) {
    return 'فتح صفحة $agent';
  }

  @override
  String get teamIntroTurnOnPhone => 'تشغيل AI Team على هذا الهاتف';

  @override
  String get teamIntroInstalledTitle => 'مثبّت على هذا الهاتف';

  @override
  String get teamIntroInstalledBody =>
      'لم يُشغّل بعد. يؤدي تشغيله إلى بدء عمل الفريق على مشروعك؛ لا يلزم تنزيل أي شيء إضافي.';

  @override
  String get teamIntroSetUpPhone => 'إعداد AI Team على هذا الهاتف';

  @override
  String teamIntroSetUpOn(String server) {
    return 'إعداد AI Team على $server';
  }

  @override
  String teamIntroTurnOn(String server) {
    return 'تشغيل AI Team على $server';
  }

  @override
  String get teamIntroCostTitle => 'قبل الإعداد';

  @override
  String get teamIntroCostTime => 'نحو 8–10 دقائق في المرة الأولى';

  @override
  String get teamIntroCostMemory => 'نحو 550 ميغابايت من الذاكرة لكل عامل';

  @override
  String get teamAgentScreenLabelId => 'معرّف الوكيل';

  @override
  String get gateSheetSendNeedsText => 'اكتب إجابة أولًا';

  @override
  String teamAgentsChecked(String age) {
    return 'آخر تحقّق منذ $age';
  }

  @override
  String get teamWorkSheetMissingTitle => 'لم تعد مهمة العمل موجودة';

  @override
  String get teamWorkSheetMissingBody =>
      'ربما اكتملت أو أُزيلت. أغلق هذه اللوحة لرؤية حالة المهمة الآن.';

  @override
  String get teamWorkSheetNotOnHost => 'لم تعد مُدرجة';

  @override
  String get teamWorkSheetOpenStepConversation => 'فتح محادثة هذه الخطوة';

  @override
  String teamWorkSheetOpenAgentConversation(String name) {
    return 'فتح محادثة $name';
  }

  @override
  String get usageRangeLabel => 'الفترة الزمنية';

  @override
  String get usageAboutNumbers => 'عن هذه الأرقام';

  @override
  String get usageBudgetHelperUsd =>
      'بالدولار الأمريكي لهذه الفترة. يصلك تنبيه عند بلوغ التقرير هذا المبلغ؛ لا يتوقف شيء.';

  @override
  String get usageBudgetHelperTokens =>
      'عدد صحيح من الرموز لهذه الفترة. يصلك تنبيه عند بلوغ التقرير هذا العدد؛ لا يتوقف شيء.';

  @override
  String get usageBudgetClearConfirm => 'مسح الميزانيات';

  @override
  String get usageBudgetNotSet => 'غير محددة';

  @override
  String get usageBudgetWaitReason => 'تتوفّر بعد تحميل الاستخدام.';

  @override
  String get usageBudgetUsdTitle => 'ميزانية بالدولار الأمريكي';

  @override
  String get usageBudgetTokensTitle => 'ميزانية الرموز';

  @override
  String get agentAccountScopeLostTitle => 'تغيّر هذا الخادم';

  @override
  String get agentAccountBackToServers => 'العودة إلى الخوادم';

  @override
  String get agentAccountNotConnected =>
      'اتصل بهذا الخادم لرؤية حساب Codex الخاص به.';

  @override
  String get agentAccountSignInMethod => 'طريقة تسجيل الدخول';

  @override
  String get agentAccountPlanTitle => 'الخطة';

  @override
  String get agentAccountCopyCode => 'نسخ رمز تسجيل الدخول';

  @override
  String agentAccountLimitReached(String reset) {
    return 'بلغت أحد حدود Codex. $reset';
  }

  @override
  String get agentAccountResetDue => 'يُعاد تعيين الحد في أي لحظة';

  @override
  String agentAccountResetInDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'يُعاد تعيين الحد بعد $days أيام',
      one: 'يُعاد تعيين الحد بعد يوم واحد',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInHours(int hours) {
    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'يُعاد تعيين الحد بعد $hours ساعات',
      one: 'يُعاد تعيين الحد بعد ساعة واحدة',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'يُعاد تعيين الحد بعد $minutes دقائق',
      one: 'يُعاد تعيين الحد بعد دقيقة واحدة',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetWhen(String relative, String time) {
    return '$relative ($time)';
  }

  @override
  String get reviewWorkspaceScopes => 'التغييرات المطلوب عرضها';

  @override
  String get reviewWorkspaceRefreshFailed => 'تعذّر تحديث التغييرات';

  @override
  String get reviewWorkspaceSlowTitle => 'لا تزال قراءة التغييرات جارية';

  @override
  String get reviewWorkspaceSlowBody =>
      'يشغّل الخادم git لمقارنة الملفات. قد يستغرق المشروع الكبير دقيقة.';

  @override
  String get reviewWorkspaceAllViewedTitle => 'راجعت كل الملفات';

  @override
  String reviewWorkspaceAllViewedMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'الملاحظات المضافة إلى الطلب وعددها $count جاهزة للإرسال من المحادثة.',
      one: 'ملاحظة واحدة مضافة إلى الطلب وجاهزة للإرسال من المحادثة.',
    );
    return '$_temp0';
  }

  @override
  String get reviewWorkspaceBackToChat => 'العودة إلى المحادثة';

  @override
  String reviewWorkspaceCommentOnFile(String file) {
    return 'التعليق على $file';
  }

  @override
  String reviewWorkspaceAddFileToPrompt(String file) {
    return 'إضافة $file إلى الطلب';
  }

  @override
  String get reviewWorkspaceAddComment => 'إضافة التعليق إلى الطلب';

  @override
  String get reviewWorkspaceCommentEmpty => 'اكتب تعليقًا أولًا.';

  @override
  String get reviewWorkspaceCommentLabel => 'تعليقك';

  @override
  String get reviewWorkspaceCommentHint =>
      'ما الذي ينبغي أن يفحصه الوكيل أو يغيّره؟';

  @override
  String get reviewWorkspaceCommentHelper =>
      'يبقى محفوظًا إذا أغلقت هذا العرض، حتى تضيفه.';

  @override
  String get integrationsMcpTitle => 'خوادم MCP';

  @override
  String get integrationsMcpServersLabel => 'خوادم MCP';

  @override
  String integrationsModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نماذج',
      one: 'نموذج واحد',
    );
    return '$_temp0';
  }

  @override
  String integrationsProviderActions(String name) {
    return 'إجراءات $name';
  }

  @override
  String integrationsManageAccounts(String name) {
    return 'إدارة حسابات $name';
  }

  @override
  String integrationsServerSignIn(String name) {
    return 'تسجيل الدخول إلى $name على الخادم';
  }

  @override
  String get integrationsServerSignInUnavailable =>
      'لا يمكن لهذا الخادم تشغيل أمر تسجيل الدخول من التطبيق.';

  @override
  String integrationsDisconnectNamed(String name) {
    return 'قطع اتصال $name';
  }

  @override
  String integrationsDisconnectBody(String name) {
    return 'يزيل مفتاح $name من هذا الخادم. يكتمل أي رد جارٍ أولًا.';
  }

  @override
  String get integrationsConnectMethodSubtitle => 'اختر طريقة الاتصال';

  @override
  String get integrationsKeyHelper =>
      'يُخزّن المفتاح على هذا الخادم. لا يعرضه التطبيق مرة أخرى.';

  @override
  String get integrationsKeyEmpty => 'الصق المفتاح أولًا.';

  @override
  String get integrationsKeyRejected =>
      'لم يقبل الخادم هذا المفتاح. تحقّق منه وحاول مجددًا.';

  @override
  String integrationsSignInAtHost(String host) {
    return 'هل تريد تسجيل الدخول لدى $host؟';
  }

  @override
  String get integrationsSignInBody =>
      'وافق على منح الوصول في المتصفح، ثم عد إلى هذا التطبيق.';

  @override
  String integrationsSignInInstructions(String instructions) {
    return 'تعليمات الخادم: $instructions';
  }

  @override
  String get integrationsFinishSignInTitle => 'إكمال تسجيل الدخول';

  @override
  String get integrationsFinishSignInAction => 'إكمال تسجيل الدخول';

  @override
  String get integrationsFinishSignInEmpty => 'الصق الرمز أولًا.';

  @override
  String get integrationsFinishSignInMcpHelper =>
      'الصق العنوان الذي وصل إليه المتصفح بعد موافقتك على منح الوصول، أو الرمز الذي عرضه.';

  @override
  String get integrationsFinishSignInProviderHelper =>
      'الصق الرمز الذي عرضته صفحة تسجيل الدخول بعد موافقتك على منح الوصول.';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return 'فتح تسجيل الدخول إلى $name';
  }

  @override
  String get integrationsCancelSignIn => 'إلغاء تسجيل الدخول';

  @override
  String get integrationsPendingNotRecoverable =>
      'أبقِ هذه الشاشة مفتوحة حتى تنتهي: لا يمكن لهذا الخادم استئناف تسجيل الدخول بعد مغادرتك.';

  @override
  String integrationsMcpActions(String name) {
    return 'إجراءات $name';
  }

  @override
  String integrationsMcpSignIn(String name) {
    return 'تسجيل الدخول إلى $name';
  }

  @override
  String integrationsMcpReconnect(String name) {
    return 'إعادة اتصال $name';
  }

  @override
  String integrationsMcpSigningIn(String name) {
    return 'جارٍ تسجيل الدخول إلى $name';
  }

  @override
  String get integrationsMcpSignInOnServer =>
      'سجّل الدخول على حاسوب الخادم؛ لا يمكن لهذا الخادم تنفيذ ذلك من التطبيق.';

  @override
  String integrationsMcpRemoveUntilRestart(String name) {
    return 'إزالة $name حتى إعادة التشغيل';
  }

  @override
  String integrationsMcpRemoveTitle(String name) {
    return 'هل تريد إزالة $name حتى إعادة التشغيل؟';
  }

  @override
  String get integrationsMcpRemoveBody =>
      'تتوقف أدواته عن العمل في هذا المشروع الآن. إذا كان موجودًا في إعدادات الخادم، فسيعود عند إعادة تشغيل الخادم.';

  @override
  String get integrationsMcpRemoveConfirm => 'الإزالة حتى إعادة التشغيل';

  @override
  String get integrationsCopyResourceAddress => 'نسخ العنوان';

  @override
  String get terminalScreenSourceLabel => 'مكان تشغيل مفسّر الأوامر';

  @override
  String get terminalScreenNameLabel => 'الاسم';

  @override
  String get terminalScreenRenameConfirm => 'تغيير الاسم';

  @override
  String get terminalScreenNameEmpty => 'اكتب اسمًا.';

  @override
  String terminalScreenStopTitle(String name) {
    return 'هل تريد إيقاف $name؟';
  }

  @override
  String get terminalScreenStopBody =>
      'يتوقف البرنامج وكل ما شغّله وتختفي الطرفية. لا يمكن استعادة مخرجاتها.';

  @override
  String terminalScreenRemoveTitle(String name) {
    return 'هل تريد إزالة $name؟';
  }

  @override
  String get terminalScreenRemoveBody =>
      'تختفي الطرفية ومخرجاتها. لا يمكن التراجع عن ذلك.';

  @override
  String get terminalScreenStopConfirm => 'إيقاف الطرفية';

  @override
  String get terminalScreenRemoveConfirm => 'إزالة الطرفية';

  @override
  String get terminalScreenCreateFailed => 'تعذّر تشغيل طرفية';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إزالة الطرفيات المنتهية وعددها $count',
      one: 'إزالة طرفية منتهية واحدة',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'هل تريد إزالة الطرفيات المنتهية وعددها $count؟',
      one: 'هل تريد إزالة طرفية منتهية واحدة؟',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody =>
      'تختفي مخرجاتها أيضًا. تبقى الطرفيات قيد التشغيل.';

  @override
  String get terminalScreenUsePhone => 'استخدام طرفية هذا الهاتف';

  @override
  String terminalScreenRowRunning(String command) {
    return 'قيد التشغيل · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return 'انتهت · الرمز $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return 'انتهت · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return 'إجراءات $name';
  }

  @override
  String terminalScreenOpen(String name) {
    return 'فتح $name';
  }

  @override
  String terminalScreenRename(String name) {
    return 'تغيير اسم $name';
  }

  @override
  String terminalScreenStop(String name) {
    return 'إيقاف $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return 'إزالة $name';
  }

  @override
  String get terminalScreenLoading => 'جارٍ تحميل الطرفيات';

  @override
  String get terminalScreenPaused =>
      'متوقفة مؤقتًا أثناء وجود التطبيق في الخلفية';

  @override
  String get terminalScreenConnecting => 'جارٍ الاتصال بالطرفية';

  @override
  String get terminalScreenCopy => 'نسخ المخرجات';

  @override
  String terminalScreenPaste(String name) {
    return 'اللصق في $name';
  }

  @override
  String get terminalScreenDetails => 'تفاصيل الطرفية';

  @override
  String terminalScreenDetailsTitle(String name) {
    return 'تفاصيل $name';
  }

  @override
  String get terminalScreenDetailCommand => 'الأمر';

  @override
  String get terminalScreenDetailFolder => 'المجلد';

  @override
  String get terminalScreenDetailPid => 'معرّف العملية';

  @override
  String get terminalScreenDetailExit => 'رمز الخروج';

  @override
  String localTerminalStopNamedTitle(String name) {
    return 'هل تريد إيقاف $name؟';
  }

  @override
  String localTerminalPasteNamed(String name) {
    return 'اللصق في $name';
  }

  @override
  String get localTerminalCopySelection => 'نسخ التحديد';

  @override
  String defaultShellOnlyOne(String name) {
    return '$name · مفسّر الأوامر الوحيد الذي يتيحه هذا الخادم';
  }

  @override
  String defaultShellSaveFailed(String error) {
    return 'تعذّر تغيير مفسّر الأوامر. $error اضغط للمحاولة مجددًا.';
  }

  @override
  String get terminalScreenReadableMode => 'العرض كنص مقروء';

  @override
  String get terminalScreenLiveMode => 'العرض كطرفية مباشرة';

  @override
  String get localTerminalSetUpLinux => 'إعداد Linux على هذا الهاتف';

  @override
  String get messageViewSendAgain => 'إرسال هذه الرسالة مجددًا';

  @override
  String get messageViewContinueReply => 'متابعة هذا الرد';

  @override
  String get reviewRevertSheetTitle => 'هل تريد التراجع بدءًا من هذا الطلب؟';

  @override
  String get reviewRevertSheetBody =>
      'يُخفى هذا الطلب وكل ما يليه أثناء المراجعة. لا يُثبّت شيء نهائيًا حتى تختار.';

  @override
  String get reviewRevertPromptLabel => 'بدءًا من هذا الطلب';

  @override
  String get reviewRevertFilesToggle => 'استعادة الملفات أيضًا';

  @override
  String get reviewRevertFilesToggleHint =>
      'تعود الملفات إلى حالتها قبل هذا الطلب.';

  @override
  String get reviewRevertSheetAction => 'تطبيق التراجع ومراجعته';

  @override
  String get reviewRevertStageFailed => 'تعذّر إعداد التراجع. لم يُخفَ شيء.';

  @override
  String get reviewRevertScreenTitle => 'مراجعة التراجع';

  @override
  String get reviewRevertScreenIntro =>
      'هذا الطلب وكل ما يليه مخفي. لا يُثبّت شيء نهائيًا حتى تختار أدناه.';

  @override
  String get reviewRevertFilesLabel => 'الملفات في هذا التراجع';

  @override
  String get reviewRevertNoFiles => 'لا تتغيّر ملفات بهذا التراجع.';

  @override
  String reviewRevertFileLines(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String reviewRevertFileSupporting(String folder, String lines) {
    return '$folder · $lines';
  }

  @override
  String get reviewRevertRestoreTitle => 'استعادة كل شيء';

  @override
  String get reviewRevertKeepTitle => 'حذف الرسائل المخفية';

  @override
  String reviewRevertKeepConfirmTitle(String title) {
    return 'حذف الرسائل في «$title»؟';
  }

  @override
  String get reviewRevertKeepConfirmBody => 'لا يمكن التراجع عن ذلك.';

  @override
  String get reviewRevertKeepConfirmAction => 'حذف الرسائل المخفية';

  @override
  String get reviewRevertKeepConsequenceMessages =>
      'يُحذف الطلب المخفي وكل رسالة تليه';

  @override
  String get reviewRevertKeepConsequenceFiles =>
      'تبقى الملفات على حالتها الحالية';

  @override
  String reviewRevertRestoreConfirmTitle(String title) {
    return 'استعادة «$title»؟';
  }

  @override
  String get reviewRevertRestoreConfirmBody =>
      'تعود الرسائل المخفية، وتعود الملفات في هذا التراجع إلى حالتها عند إعداده. يمكنك التراجع بدءًا من طلب مجددًا لاحقًا.';

  @override
  String get reviewRevertRestoreConsequenceMessages => 'تعود الرسائل المخفية';

  @override
  String reviewRevertRestoreConsequenceFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'تُستبدل الملفات وعددها $count، بما في ذلك أي تعديلات أُجريت منذ ذلك الحين',
      one: 'يُستبدل ملف واحد، بما في ذلك أي تعديلات أُجريت منذ ذلك الحين',
    );
    return '$_temp0';
  }

  @override
  String get reviewRevertRestoreConsequenceUnknownFiles =>
      'تُستبدل الملفات في هذا التراجع، بما في ذلك أي تعديلات أُجريت منذ ذلك الحين';

  @override
  String get reviewRevertStaleTitle => 'تغيّر التراجع';

  @override
  String get reviewRevertNoneTitle => 'لا يوجد ما يُراجع';

  @override
  String get reviewRevertNoneBody =>
      'لا يوجد تراجع ينتظر المراجعة في هذه المحادثة.';

  @override
  String get reviewRevertBackAction => 'العودة إلى المحادثة';

  @override
  String get reviewRevertKeptTitle => 'ثُبّت التراجع';

  @override
  String get reviewRevertKeptBody =>
      'حُذفت الرسائل المخفية. تبقى الملفات على حالتها الحالية.';

  @override
  String get reviewRevertRestoredTitle => 'استُعيد كل شيء';

  @override
  String get reviewRevertRestoredBody =>
      'عادت الرسائل والملفات إلى حالتها السابقة.';

  @override
  String get reviewRevertFailed =>
      'لم يكتمل ذلك. تحقّق من المحادثة، ثم حاول مجددًا.';

  @override
  String get perfTraceClearTimings => 'مسح التوقيتات';

  @override
  String appDiagnosticsClearTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'هل تريد مسح الأخطاء وعددها $count؟',
      one: 'هل تريد مسح خطأ واحد؟',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'تُزال الأخطاء المحفوظة على هذا الهاتف وعددها $count، ومن التقرير المحفوظ أيضًا. لا يمكن التراجع عن ذلك.',
      one:
          'يُزال الخطأ المحفوظ على هذا الهاتف، ومن التقرير المحفوظ أيضًا. لا يمكن التراجع عن ذلك.',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'مسح الأخطاء وعددها $count',
      one: 'مسح خطأ واحد',
    );
    return '$_temp0';
  }

  @override
  String get capabilityStateHere => 'يعمل هنا';

  @override
  String get capabilityStateNotServer => 'غير متاح على هذا الخادم';

  @override
  String get capabilityStateNotDevice => 'غير متاح على هذا الجهاز';

  @override
  String get capabilityNeedsAndroid => 'يحتاج تطبيق Android';

  @override
  String capabilityAvailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'ميزات تعمل هنا: $count',
      one: 'ميزة واحدة تعمل هنا',
    );
    return '$_temp0';
  }

  @override
  String get capabilityAvailableCountDetail =>
      'عرض ما يمكن لهذا الخادم والجهاز فعله';

  @override
  String get capabilityAddServer => 'إضافة خادم يتيح هذه الميزات';

  @override
  String get capabilityAddServerDetail =>
      'اتصل بحاسوب آخر أو أعدّ خادمًا على هذا الهاتف، ثم بدّل إليه';

  @override
  String get keepRunningAllSetTitle => 'الإعداد مكتمل';

  @override
  String get keepRunningAllSetBody =>
      'يترك Android التطبيق يعمل في الخلفية. لا توجد أذونات أخرى مطلوبة على هذا الهاتف.';

  @override
  String get keepRunningDailyLimit =>
      'على Android 15 والإصدارات الأحدث، يتيح Android المزامنة في الخلفية لنحو 6 ساعات يوميًا، حتى مع السماح بكل ما هنا. بعدها يتوقف التطبيق مؤقتًا في الخلفية حتى تفتحه.';

  @override
  String get aboutTitle => 'حول التطبيق';

  @override
  String get aboutCopyVersion => 'نسخ الإصدار';

  @override
  String get aboutCheckUpdates => 'التحقّق من التحديثات';

  @override
  String get aboutUpdateIdle => 'يبحث عن إصدار أحدث من هذا التطبيق';

  @override
  String get aboutUpdateChecking => 'جارٍ التحقّق…';

  @override
  String get aboutUpdateCurrent => 'لديك أحدث إصدار';

  @override
  String get aboutUpdateDownloading => 'جارٍ تنزيل التحديث…';

  @override
  String get aboutUpdateReady =>
      'التحديث جاهز. أغلق التطبيق وافتحه مجددًا لاستخدامه.';

  @override
  String get aboutUpdateCannot =>
      'لا يمكن لهذا الإصدار تحديث نفسه. ثبّت أحدث إصدار بدلًا من ذلك.';

  @override
  String get aboutUpdateFailed =>
      'تعذّر التحقّق من التحديثات. تحقّق من اتصالك وحاول مجددًا.';

  @override
  String get aboutAllLicences => 'تراخيص كل الحزم';

  @override
  String get aboutAllLicencesDetail =>
      'نص ترخيص كل مكتبة مضمّنة في هذا الإصدار';

  @override
  String get aboutPackageId => 'معرّف الحزمة';

  @override
  String get usageHubUnavailableTitle => 'لا يوجد استخدام لعرضه';

  @override
  String get usageHubUnavailableBody =>
      'اتصل بخادم محفوظ لعرض ما أنفقه وما تبقى في حساباتك لدى المزوّدين.';

  @override
  String get voiceSetupSubtitle =>
      'نزّل نموذجًا للكلام مرة واحدة. بعد ذلك، يعمل الإدخال الصوتي على هذا الهاتف دون إنترنت.';

  @override
  String voiceSetupDownloadPack(String model, String size) {
    return 'تنزيل $model ($size)';
  }

  @override
  String voiceSetupUsePack(String model) {
    return 'استخدام $model';
  }

  @override
  String voiceSetupRedownloadPack(String model) {
    return 'تنزيل نموذج الكلام $model مجددًا';
  }

  @override
  String voiceSetupDeletePack(String model, String size) {
    return 'حذف نموذج الكلام $model ($size)';
  }

  @override
  String voiceSetupKeepPack(String model) {
    return 'إبقاء $model';
  }

  @override
  String voiceSetupDownloadingPack(String model) {
    return 'جارٍ تنزيل $model';
  }

  @override
  String get voiceSetupModelLabel => 'نموذج الكلام';

  @override
  String get voiceNoticesTitle => 'تراخيص الصوت';

  @override
  String get voiceNoticesIntro =>
      'يعتمد الإدخال الصوتي على هذه المكوّنات مفتوحة المصدر. افتح أحدها لقراءة ترخيصه.';

  @override
  String voiceNoticesMadeBy(String maker, String license) {
    return '$maker · $license';
  }

  @override
  String voiceNoticesOpenWebsite(String name) {
    return 'فتح موقع $name';
  }

  @override
  String get voiceNoticesWhisper => 'نماذج الكلام Whisper';

  @override
  String get voiceSetupBusyReason => 'يتوفّر بعد التنزيل';

  @override
  String get shorebirdUpdateReadyTitle => 'تحديث التطبيق جاهز';

  @override
  String get shorebirdUpdateReadyBody =>
      'يسري عند إغلاق التطبيق بالكامل وفتحه مجددًا.';

  @override
  String desktopReleaseAvailable(String tag) {
    return 'التحديث $tag متاح';
  }

  @override
  String get desktopReleaseWhatChanged =>
      'تعرض صفحة الإصدار التغييرات وروابط التنزيل.';

  @override
  String get desktopReleaseOpenPage => 'فتح صفحة الإصدار';

  @override
  String get runningWorkTitle => 'العمل في هذه المحادثة';

  @override
  String get runningWorkFailed => 'فشل';

  @override
  String runningWorkAgentState(String state) {
    return 'الوكيل · $state';
  }

  @override
  String runningWorkCommandState(String state) {
    return 'الأمر · $state';
  }

  @override
  String get runningWorkOffline =>
      'جارٍ إعادة الاتصال. حاول مجددًا عندما يردّ الخادم.';

  @override
  String runningWorkStopAgent(String title) {
    return 'إيقاف «$title»';
  }

  @override
  String runningWorkStopAgentTitle(String title) {
    return 'هل تريد إيقاف «$title»؟';
  }

  @override
  String get runningWorkStopAgentBody =>
      'يتوقف الوكيل عند خطوته الحالية. تبقى محادثته والملفات التي غيّرها محفوظة.';

  @override
  String get runningWorkStopAgentConfirm => 'إيقاف الوكيل';

  @override
  String get runningWorkScopeChangedTitle => 'تغيّر الخادم أو المشروع';

  @override
  String get runningWorkAgentsFailed => 'تعذّر تحميل وكلاء هذه المحادثة.';

  @override
  String get runningWorkCommandsFailed => 'تعذّر تحميل أوامر هذه المحادثة.';

  @override
  String get runningWorkEmptyTitle => 'لا يوجد عمل جارٍ';

  @override
  String get runningWorkEmptyBody =>
      'تظهر هنا الوكلاء والأوامر التي تبدأها هذه المحادثة أثناء عملها وبعد انتهائها.';

  @override
  String get runningWorkBackgroundBody =>
      'يواصل العمل التشغيل على الخادم وتعود نتائجه إلى هنا.';

  @override
  String get runningWorkBackgroundAction => 'متابعة المحادثة أثناء التشغيل';

  @override
  String get shellOutputCopyFirst => 'نسخ المخرجات أولًا';

  @override
  String get shellOutputLimitTitle => 'الإيقاف بعد…';

  @override
  String shellOutputStopsIn(String time) {
    return 'يتوقف خلال $time';
  }

  @override
  String get shellOutputNoLimit => 'بلا مهلة زمنية';

  @override
  String shellOutputAboutToStop(String time) {
    return 'يتوقف خلال $time. غيّر المهلة لمنحه وقتًا أطول.';
  }

  @override
  String get shellOutputReadFailed => 'تعذّرت قراءة المخرجات.';

  @override
  String get shellOutputLimitFailed => 'تعذّر تغيير المهلة الزمنية.';

  @override
  String get shellOutputDetailCommand => 'الأمر كما كُتب';

  @override
  String get shellOutputDetailFolder => 'المجلد';

  @override
  String get shellOutputDetailExit => 'رمز الخروج';

  @override
  String get shellOutputDetailId => 'معرّف الأمر';

  @override
  String get shellOutputReading => 'جارٍ قراءة المخرجات';

  @override
  String get sessionDestinationWarpTitle => 'نقل إلى السحابة';

  @override
  String get sessionDestinationSeparateCopy => 'نسخة منفصلة';

  @override
  String sessionDestinationCloudKind(String state) {
    return 'جهاز سحابي · $state';
  }

  @override
  String get sessionDestinationConnected => 'متصل';

  @override
  String get sessionDestinationNotConnected => 'غير متصل';

  @override
  String get sessionDestinationNotConnectedWhy =>
      'غير متصل. يمكنك اختياره بعد اتصاله.';

  @override
  String sessionDestinationChangesGo(String destination) {
    return 'عند النقل مع التغييرات، تنتقل معه إلى $destination.';
  }

  @override
  String sessionDestinationChangesCopied(String destination) {
    return 'عند النقل مع التغييرات، تنتقل نسخة منها معه إلى $destination.';
  }

  @override
  String sessionDestinationChangesStay(String place) {
    return 'عند النقل دون التغييرات، تبقى في $place.';
  }

  @override
  String sessionDestinationMoveWithout(String destination) {
    return 'نقل إلى $destination دون التغييرات';
  }

  @override
  String get sessionDestinationMoveFailed => 'تعذّر نقل المحادثة.';

  @override
  String get sessionDestinationLoadFailed => 'تعذّر تحميل وجهات النقل';

  @override
  String get sessionDestinationNoneTitle => 'لا توجد وجهة لنقلها';

  @override
  String get sessionDestinationNoneMoveBody =>
      'لا يحتوي هذا المشروع إلا على هذا المجلد. تظهر هنا نسخة منفصلة من المشروع بمجرد إنشائها.';

  @override
  String get sessionDestinationNoneWarpBody =>
      'لا يوجد جهاز سحابي لهذا المشروع بعد.';

  @override
  String get consoleOrganizationWhatChanges =>
      'تتبع النماذج والمزوّدون والفوترة المؤسسة التي تختارها.';

  @override
  String consoleOrganizationSwitchBody(String organization) {
    return 'تصبح $organization المؤسسة الخاصة بالنماذج والمزوّدين والفوترة. يُعاد تحميل النماذج؛ لا يتوقف أي عمل جارٍ.';
  }

  @override
  String consoleOrganizationSwitchConfirm(String organization) {
    return 'التبديل إلى $organization';
  }

  @override
  String get consoleOrganizationLoadFailed => 'تعذّر تحميل مؤسساتك';

  @override
  String get consoleOrganizationNoneTitle => 'لا توجد مؤسسات';

  @override
  String get consoleOrganizationOnlyOne =>
      'هذه مؤسستك الوحيدة، لذا لا توجد مؤسسة أخرى للتبديل إليها.';

  @override
  String get sessionContextLoading => 'جارٍ تحميل السياق';

  @override
  String get sessionContextMovedTitle => 'انتقلت هذه المحادثة';

  @override
  String get sessionContextLoadFailed => 'تعذّر تحميل السياق';

  @override
  String get sessionContextRefreshFailed =>
      'تعذّر التحديث. الأرقام أدناه من آخر قراءة.';

  @override
  String sessionContextVerdictPlenty(String percent) {
    return 'المستخدم: $percent% · تتبقى مساحة كبيرة';
  }

  @override
  String sessionContextVerdictUsed(String percent) {
    return 'المستخدم: $percent%';
  }

  @override
  String sessionContextVerdictNear(String percent) {
    return 'المستخدم: $percent%';
  }

  @override
  String sessionContextVerdictFull(String percent) {
    return 'المستخدم: $percent% · بلغ الحد';
  }

  @override
  String get sessionContextNearLimitTitle => 'اقترب من الحد';

  @override
  String get sessionContextNearLimitBody =>
      'قد تُحذف التفاصيل القديمة مما يراه النموذج. لخّص المحادثة للمتابعة، أو ابدأ محادثة جديدة.';

  @override
  String get sessionContextCompactAction => 'تلخيص هذه المحادثة';

  @override
  String get sessionContextCompactTitle => 'هل تريد تلخيص هذه المحادثة؟';

  @override
  String get sessionContextCompactBody =>
      'يلخّص OpenCode المحادثة حتى الآن ويتابع انطلاقًا من الملخّص، لتستهلك قدرًا أقل من سعة النموذج.';

  @override
  String get sessionContextCompactKept => 'تبقى جميع الرسائل في السجل.';

  @override
  String get sessionContextCompactConfirm => 'تلخيص المحادثة';

  @override
  String get sessionContextCompactStarted =>
      'بدأ التلخيص. تُحدَّث الأرقام بعد انتهائه.';

  @override
  String get sessionContextCompactBusy => 'انتظر حتى ينتهي الرد.';

  @override
  String get sessionContextMakeupTitle => 'مدخلات الطلب الأخير';

  @override
  String sessionContextTokens(String count) {
    return '$count رمزًا';
  }

  @override
  String get sessionContextModelId => 'معرّف النموذج';

  @override
  String get demoScreenTitle => 'التجربة دون اتصال';

  @override
  String get demoScreenSimulated => 'محاكاة · لا يُحفظ شيء';

  @override
  String get demoScreenFinished =>
      'هذه هي الخطوات كاملة: طلب وردّ وتعديل تمت مراجعته.';

  @override
  String sessionContextPercent(String percent) {
    return '$percent %';
  }

  @override
  String sessionDestinationChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يوجد $count من الملفات التي تغيّرت.',
      one: 'يوجد ملف واحد تغيّر.',
    );
    return '$_temp0';
  }

  @override
  String get activeContextLoading => 'جارٍ قراءة السياق النشط…';

  @override
  String activeContextAllCount(int count) {
    return 'كل الرسائل · $count';
  }

  @override
  String get activeContextChangedTitle => 'هذا العرض قديم';

  @override
  String get activeContextFailedTitle => 'تعذّرت قراءة السياق';

  @override
  String get activeContextIntro =>
      'ما يقرؤه النموذج في دوره التالي، بعد أحدث ملخّص.';

  @override
  String get activeContextEmptyDetail =>
      'لم يُحتفظ بشيء للدور التالي بعد. اسحب لأسفل للتحقّق مجددًا.';

  @override
  String get activeContextWhat => 'السياق النشط';

  @override
  String activeContextRowMenu(String type) {
    return 'إجراءات $type';
  }

  @override
  String activeContextOpenMessage(String type) {
    return 'فتح $type';
  }

  @override
  String activeContextCopyMessage(String type) {
    return 'نسخ نص $type';
  }

  @override
  String get activeContextMessageId => 'معرّف الرسالة';

  @override
  String activeContextCopyPart(String part) {
    return 'نسخ $part';
  }

  @override
  String get sessionNoteDeleting => 'جارٍ حذف الملاحظة…';

  @override
  String get sessionNoteSaving => 'جارٍ حفظ الملاحظة…';

  @override
  String get sessionNoteLoading => 'جارٍ قراءة الملاحظة المحفوظة…';

  @override
  String get sessionNoteLoadFailed => 'تعذّر قراءة الملاحظة';

  @override
  String get sessionNoteSaveFailed => 'تعذّر حفظ الملاحظة';

  @override
  String get sessionNoteFieldLabel => 'ملاحظة';

  @override
  String get sessionNoteFieldLocked => 'حدّث الملاحظة المحفوظة قبل تحريرها.';

  @override
  String sessionNoteTooLong(int over, int limit) {
    return 'تتجاوز الحد بمقدار $over بايت. الحد الأقصى للملاحظة $limit بايت.';
  }

  @override
  String get sessionNoteWriteFirst => 'اكتب ملاحظة لحفظها.';

  @override
  String get sessionNoteEmptyUseDelete =>
      'لإزالة الملاحظة، استخدم «حذف الملاحظة المحفوظة».';

  @override
  String get sessionRelationsTitle => 'الوكلاء الفرعيون';

  @override
  String sessionRelationsStopTitle(String title) {
    return 'هل تريد إيقاف $title؟';
  }

  @override
  String get sessionRelationsStopBody =>
      'يتوقف الوكيل الفرعي عن خطوته الحالية. يبقى ما أنجزه بالفعل في محادثته.';

  @override
  String get sessionRelationsStopConfirm => 'إيقاف الوكيل الفرعي';

  @override
  String get sessionRelationsFailedTitle => 'تعذّر تحميل الوكلاء الفرعيين';

  @override
  String get sessionRelationsLoading => 'جارٍ تحميل الوكلاء الفرعيين…';

  @override
  String get sessionRelationsStartedFrom => 'بدأت من';

  @override
  String sessionRelationsSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الوكلاء الفرعيين',
      one: 'وكيل فرعي واحد',
    );
    return '$_temp0';
  }

  @override
  String get sessionRelationsOpenToAnswer => 'افتح للإجابة';

  @override
  String get sessionRelationsIdle => 'خامل';

  @override
  String get sessionRelationsThisConversation => 'هذه المحادثة';

  @override
  String get sessionRelationsOpening => 'جارٍ الفتح…';

  @override
  String sessionRelationsRowMenu(String title) {
    return 'إجراءات $title';
  }

  @override
  String sessionRelationsOpen(String title) {
    return 'فتح $title';
  }

  @override
  String sessionRelationsCopyHandoff(String title) {
    return 'متابعة $title على الكمبيوتر';
  }

  @override
  String sessionRelationsPin(String title) {
    return 'تثبيت $title';
  }

  @override
  String sessionRelationsUnpin(String title) {
    return 'إلغاء تثبيت $title';
  }

  @override
  String sessionRelationsStop(String title) {
    return 'إيقاف $title';
  }

  @override
  String get webSourcesInvalidUrl =>
      'أدخل عنوان HTTP أو HTTPS دون اسم مستخدم أو كلمة مرور.';

  @override
  String get webSearchFailedTitle => 'لم يكتمل البحث';

  @override
  String get webSearchTryAgain => 'البحث مجددًا';

  @override
  String get webSearchBusy => 'انتظر اكتمال البحث.';

  @override
  String get webSearchQueryHint =>
      'مثلًا: اختبارات المقارنة المرئية في flutter';

  @override
  String get webSearchNeedsProvider => 'أعدّ مزوّد بحث على هذا الخادم أولًا.';

  @override
  String get webSearchEmptyDetail => 'جرّب كلمات أخرى أو الصق رابطًا أدناه.';

  @override
  String webSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عدد النتائج: $count',
      one: 'نتيجة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesAdded => 'تمت الإضافة';

  @override
  String webSourcesAddNamed(String title) {
    return 'إضافة $title إلى الطلب';
  }

  @override
  String webSourcesRowMenu(String title) {
    return 'إجراءات $title';
  }

  @override
  String webSourcesOpenHost(String host) {
    return 'فتح $host في المتصفح';
  }

  @override
  String get webSourcesAddLink => 'إضافة رابط إلى الطلب';

  @override
  String get webSourcesPasteDetail => 'عنوان عام، مع مقتطف اختياري';

  @override
  String webSourcesRemoveNamed(String title) {
    return 'إزالة $title من الطلب';
  }

  @override
  String get webSearchSearching => 'جارٍ البحث…';

  @override
  String get webSearchFindingProviders => 'جارٍ البحث عن مزوّدي البحث…';

  @override
  String webSourcesDone(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إضافة المصادر إلى الطلب وعددها $count',
      one: 'إضافة مصدر واحد إلى الطلب',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesScopeChangedTitle => 'تغيّر الخادم';

  @override
  String get sessionExportFormatLabel => 'الصيغة';

  @override
  String get sessionExportJsonUnavailable =>
      'لا يمكن لهذا الخادم إرسال نسخة كاملة. احفظ سجلًا قابلًا للقراءة بدلًا منها.';

  @override
  String get sessionExportPrivacyLabel => 'الخصوصية';

  @override
  String get sessionExportRedactKeeps =>
      'يحتفظ بمن كتب كل رسالة، ويستبدل الكلمات بنصوص بديلة. ليس نسخة احتياطية.';

  @override
  String get sessionExportRedactBusy =>
      'انتظر حتى يُحفظ الملف لتغيير هذا الخيار.';

  @override
  String get sessionExportRedactChanged =>
      'افتح التصدير مجددًا من المحادثة لتغيير هذا الخيار.';

  @override
  String get sessionExportSaveJson => 'حفظ المحادثة كاملة';

  @override
  String get sessionExportSaveMarkdown => 'حفظ سجل قابل للقراءة';

  @override
  String get sessionExportSaveFailed =>
      'تعذّر كتابة الملف على هذا الجهاز. لم يتغيّر شيء على الخادم. حاول مجددًا، أو اختر مجلدًا آخر.';

  @override
  String get capabilitiesToolsMissingTitle => 'الأدوات غير مدرجة';

  @override
  String capabilitiesToolsMissingOnServer(String server) {
    return 'لا يعرض $server قائمة أدواته';
  }

  @override
  String get mcpSetupWhere => 'مكان الإضافة';

  @override
  String get mcpSetupHowItRuns => 'طريقة التشغيل';

  @override
  String get mcpSetupHeaders => 'الترويسات';

  @override
  String get mcpSetupAdvanced => 'خيارات متقدمة';

  @override
  String get mcpSetupAdvancedRemote => 'اكتشاف تسجيل الدخول والمهلة';

  @override
  String get mcpSetupAdvancedLocal => 'مجلد العمل والمهلة';

  @override
  String get mcpSetupNoProject => 'افتح مشروعًا أولًا';

  @override
  String get mcpSetupRuntimeNote =>
      'يتصل الآن ويُزال عند إعادة تشغيل OpenCode. لإعداد دائم، عدّل إعدادات الخادم.';

  @override
  String mcpSetupSaveNamed(String name) {
    return 'حفظ $name';
  }

  @override
  String mcpSetupAddNamed(String name) {
    return 'إضافة $name';
  }

  @override
  String get mcpSetupLocationChangedShort => 'تغيّر الخادم أو المشروع';

  @override
  String get mcpSetupSaveFailed => 'تعذّرت إضافة خادم MCP';

  @override
  String get mcpSetupDiscardTitle => 'هل تريد تجاهل خادم MCP هذا؟';

  @override
  String get mcpSetupDiscardBody => 'لم يُحفظ ما كتبته هنا وسيُفقد.';

  @override
  String get mcpSetupDiscardConfirm => 'تجاهل الخادم';

  @override
  String get externalAgentsEmptyTitle => 'لا يوجد وكلاء خارجيون بعد';

  @override
  String get externalAgentsEmptyBody =>
      'أضف وكيلًا بعنوانه على الويب. سترى ما يقوله عن نفسه قبل حفظ أي شيء.';

  @override
  String get externalAgentsBoundary =>
      'لا يصل إلى الوكيل الخارجي إلا النص الذي ترسله. تبقى مشاريعك وملفاتك ومحادثاتك الأخرى على هذا الهاتف.';

  @override
  String get externalAgentsRemovalIncomplete =>
      'لم تكتمل الإزالة · اضغط للمحاولة مجددًا';

  @override
  String externalAgentsRemoveNamed(String name) {
    return 'إزالة $name من هذا الهاتف';
  }

  @override
  String externalAgentsRemoveTitle(String name) {
    return 'هل تريد إزالة $name؟';
  }

  @override
  String get externalAgentsRemoveBody =>
      'تُزال مهامه المحفوظة ومفتاحه من هذا الهاتف. يستمر العمل الذي بدأه بالفعل ويبقى ما يحتفظ به لديه.';

  @override
  String get externalAgentsBusy => 'انتظر اكتمال الخطوة الحالية';

  @override
  String get externalAgentsAddressHelper =>
      'عنوانه على الويب أو عنوان بطاقة الوكيل الخاصة به.';

  @override
  String get externalAgentsCheck => 'التحقّق من الوكيل';

  @override
  String get externalAgentsCheckNeedsAddress => 'اكتب عنوان الوكيل أولًا';

  @override
  String get externalAgentsStopChecking => 'إيقاف التحقّق';

  @override
  String get externalAgentsCheckFailedTitle => 'تعذّر التحقّق من هذا الوكيل';

  @override
  String externalAgentsSaveNamed(String name) {
    return 'حفظ $name';
  }

  @override
  String get externalAgentsSaveNeedsKey => 'أدخل مفتاح الوكيل أولًا';

  @override
  String get externalAgentsAboutLabel => 'ما يقوله عن نفسه';

  @override
  String get externalAgentsUnverified =>
      'الوكيل يصف نفسه. لم يتحقّق هذا التطبيق من الجهة التي تشغّله أو إمكاناته أو تكلفته.';

  @override
  String get externalAgentsUnsupportedTitle => 'الوكيل غير مدعوم';

  @override
  String get externalAgentsUnsupportedBody =>
      'لا يقبل المهام النصية بالطريقة التي يرسلها هذا التطبيق، أو يطلب تسجيل دخول لا يدعمه التطبيق.';

  @override
  String get externalAgentsKeyLabel => 'مفتاح الوكيل';

  @override
  String get externalAgentsKeyHelper =>
      'المفتاح الذي أعطاك إياه مالكه. يبقى في مساحة التخزين الآمنة لهذا الهاتف ولا يُرسل إلا إلى هذا الوكيل.';

  @override
  String get externalAgentsNoKey =>
      'لا يطلب هذا الوكيل مفتاحًا. لا ترسل معلومات خاصة إلا إذا كنت تثق به.';

  @override
  String get externalAgentsDetailCard => 'بطاقة الوكيل';

  @override
  String get externalAgentsDetailEndpoint => 'نقطة الاتصال';

  @override
  String get externalAgentsDetailVersion => 'الإصدار';

  @override
  String get externalAgentsDetailConnection => 'الاتصال';

  @override
  String externalAgentsNewTaskNamed(String name) {
    return 'مهمة جديدة لـ $name';
  }

  @override
  String externalAgentsReplaceKeyNamed(String name) {
    return 'استبدال مفتاح $name';
  }

  @override
  String externalAgentsReplaceKeyTitle(String name) {
    return 'استبدال مفتاح $name';
  }

  @override
  String get externalAgentsSaveKey => 'حفظ المفتاح';

  @override
  String get externalAgentsTasksLabel => 'المهام';

  @override
  String get externalAgentsNoTasksTitle => 'لا توجد مهام بعد';

  @override
  String get externalAgentsNoTasksBody =>
      'اكتب مهمة وراجعها قبل إرسالها. يتحقّق فتح المهمة المرسلة من حالتها؛ ولا تُرسل مرتين أبدًا.';

  @override
  String get externalAgentsUntitledTask => 'مهمة جديدة';

  @override
  String externalAgentsSendNamed(String name) {
    return 'الإرسال إلى $name';
  }

  @override
  String externalAgentsReplyNamed(String name) {
    return 'الرد على $name';
  }

  @override
  String get externalAgentsSendNeedsText => 'اكتب المهمة أولًا';

  @override
  String get externalAgentsReplyNeedsText => 'اكتب ردّك أولًا';

  @override
  String get externalAgentsSendNote =>
      'يُرسل هذا النص فقط. قد يستخدم الوكيل خدماته الخاصة ويفرض رسومًا عليها؛ راجع شروطه.';

  @override
  String externalAgentsCheckedAt(String age) {
    return 'تم التحقّق مع الوكيل منذ $age';
  }

  @override
  String get externalAgentsPullToCheck =>
      'محفوظة على هذا الهاتف · اسحب لأسفل للتحقّق مع الوكيل';

  @override
  String externalAgentsStopMenu(String name) {
    return 'طلب إيقاف هذه المهمة من $name';
  }

  @override
  String get externalAgentsStopUnavailable =>
      'تحقّق مع الوكيل أولًا؛ اسحب لأسفل للتحديث';

  @override
  String get externalAgentsForgetMenu => 'مسح هذه المهمة من هذا الهاتف';

  @override
  String get externalAgentsForgetTitle => 'هل تريد مسح هذه المهمة؟';

  @override
  String get externalAgentsForgetBody =>
      'تُزال من هذا الهاتف. يستمر العمل الذي بدأه الوكيل بالفعل وتبقى نسخته لديه.';

  @override
  String get externalAgentsForgetConfirm => 'مسح المهمة';

  @override
  String mcpSetupSavedNamed(String name) {
    return 'حُفظ $name على هذا الخادم';
  }

  @override
  String mcpSetupSavedNotConnectedBody(String reason) {
    return 'لم يُعِد التطبيق الاتصال بعد ذلك. $reason';
  }

  @override
  String get mcpSetupSavedElsewhere =>
      'تغيّر الخادم أو المشروع بعد الحفظ، لذا لا يمكن لهذه الصفحة إعادة الاتصال به. أغلقها وتحقّق من خوادم MCP.';

  @override
  String get mcpSetupUnavailableTitle => 'لا يمكن إضافة خوادم MCP';

  @override
  String get mcpSetupUnavailableBody =>
      'لا يقبل خوادم MCP جديدة من التطبيق. أضفها إلى إعداداته على الحاسوب؛ ستظهر بعد ذلك ضمن خوادم MCP.';

  @override
  String get commandAuthSheetWorking => 'جارٍ طلب الاستجابة من الخادم…';

  @override
  String get credentialSheetLoading => 'جارٍ قراءة الحسابات المحفوظة…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return 'سجّل الدخول إلى $provider مجددًا من «مزوّدو الخدمة» لإضافة حساب.';
  }

  @override
  String credentialSheetActions(String label) {
    return 'إجراءات $label';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return 'استخدام $label';
  }

  @override
  String get credentialSheetInUse => 'قيد الاستخدام بالفعل';

  @override
  String credentialSheetRenameNamed(String label) {
    return 'إعادة تسمية $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return 'إزالة $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return 'يُزال $label من هذا الخادم. ستحتاج المشاريع التي تستخدمه إلى حساب آخر لدى $provider.';
  }

  @override
  String credentialSheetRenamed(String label) {
    return 'أُعيدت تسميته إلى $label.';
  }

  @override
  String get credentialSheetLabelEmpty => 'أعطِ الحساب اسمًا.';

  @override
  String get credentialSheetLabelInvalid =>
      'استخدم حتى 128 حرفًا، دون فواصل أسطر أو محارف تحكم.';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return 'هل تريد مسح تسجيل الدخول إلى $integration؟';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      'يتوقف التطبيق عن تتبّعه على هذا الجهاز. لا يُلغى شيء على الخادم؛ وتنتهي صلاحية تسجيل الدخول غير المكتمل هناك تلقائيًا.';

  @override
  String get toolsScreenLoadFailed => 'تعذّر تحميل أدوات هذا النموذج';

  @override
  String get toolsScreenSearchWhat => 'الأدوات';

  @override
  String get toolsScreenRegisteredOnly =>
      'مسجّلة في هذا المشروع · لا يمكن لهذا النموذج استدعاؤها';

  @override
  String get toolsDetailTakes => 'المدخلات';

  @override
  String get toolsDetailTakesNothing => 'لا تحتاج إلى مدخلات.';

  @override
  String get toolsDetailRequired => 'مطلوب';

  @override
  String get toolsDetailOptional => 'اختياري';

  @override
  String get toolsDetailTypeText => 'نص';

  @override
  String get toolsDetailTypeNumber => 'عدد';

  @override
  String get toolsDetailTypeYesNo => 'نعم أو لا';

  @override
  String get toolsDetailTypeList => 'قائمة';

  @override
  String get toolsDetailTypeGroup => 'مجموعة قيم';

  @override
  String get toolsDetailTypeAny => 'أي قيمة';

  @override
  String commandsScreenRunsWith(String agent) {
    return 'يعمل باستخدام $agent';
  }

  @override
  String get commandsScreenMenuLabel => 'إجراءات الأمر';

  @override
  String commandsScreenCopy(String command) {
    return 'نسخ $command';
  }

  @override
  String get referencesScreenLoading => 'جارٍ تحميل المراجع';

  @override
  String get referencesScreenLoadFailed => 'تعذّر تحميل المراجع';

  @override
  String get referencesScreenIntro =>
      'المجلدات التي يوجّه هذا المشروع وكلاءه إليها. أضف أحدها إلى طلب ليتمكن الوكيل من قراءة محتوياته.';

  @override
  String get referencesScreenEmptyBody =>
      'المرجع مجلد يمكن لوكلاء المشروع قراءته. تظهر هنا المراجع المُعدّة لهذا المشروع.';

  @override
  String get referencesScreenMenuLabel => 'إجراءات المرجع';

  @override
  String referencesScreenAdd(String mention) {
    return 'إضافة $mention إلى الطلب';
  }

  @override
  String referencesScreenShowDetails(String name) {
    return 'عرض تفاصيل $name';
  }

  @override
  String referencesScreenCopyMention(String mention) {
    return 'نسخ $mention';
  }

  @override
  String get referencesScreenCopyPath => 'نسخ المسار';

  @override
  String referencesScreenSheetBody(String mention) {
    return 'اكتب $mention في طلب ليقرأ الوكيل هذا المجلد عند الرد عليه.';
  }

  @override
  String get referencesScreenPathLabel => 'المسار';

  @override
  String get skillsScreenLoading => 'جارٍ تحميل المهارات';

  @override
  String get skillsScreenLoadFailed => 'تعذّر تحميل المهارات';

  @override
  String get skillSheetViewLabel => 'طريقة عرض المهارة';

  @override
  String get skillSheetLocation => 'الملف';

  @override
  String skillSheetCopyCommand(String command) {
    return 'نسخ $command';
  }

  @override
  String get skillSheetCheckConversation =>
      'تحقّق من المحادثة قبل المحاولة مجددًا.';

  @override
  String get skillSheetSending => 'جارٍ إضافة المهارة…';

  @override
  String toolCardDelegatedTo(String agent) {
    return 'أُسند العمل إلى $agent';
  }

  @override
  String get toolCardExitPassed => 'نجح · رمز الخروج 0';

  @override
  String toolCardExitFailed(int code) {
    return 'فشل · رمز الخروج $code';
  }

  @override
  String get toolCardRunCommandAgain => 'تشغيل هذا الأمر مجددًا';

  @override
  String get toolCardCopyCommand => 'نسخ الأمر';

  @override
  String toolCardLoadImageAgain(String name) {
    return 'تحميل $name مجددًا';
  }

  @override
  String toolCardChangesIn(String file) {
    return 'التغييرات في $file';
  }

  @override
  String mobileTasksShowAll(int count) {
    return 'عرض كل المهام وعددها $count';
  }

  @override
  String get composerBusyReason => 'جارٍ تجهيز طلبك…';

  @override
  String get composerToolsTextOnly => 'يقبل هذا الخادم النصوص فقط';

  @override
  String composerToolsAgentTextOnly(String agent) {
    return 'يقبل $agent النص فقط هنا';
  }

  @override
  String launcherConversationHint(String project, String agent) {
    return 'محادثة في $project · $agent';
  }

  @override
  String composerToolsAgentPicturesOnly(String agent) {
    return 'يقبل $agent الصور فقط: استخدم مكتبة الصور أو التقاط صورة';
  }

  @override
  String chatsNewAgentNeedsProjectsFolder(String agent) {
    return 'يعمل $agent في مشاريع هذا الهاتف فقط. اختر واحدًا منها.';
  }

  @override
  String get composerToolCommandsTitle => 'الأوامر والوكلاء';

  @override
  String get composerToolSavedSubtitle => 'إعادة طلب محفوظ إلى المسودة';

  @override
  String get composerToolSaveForLater => 'حفظ الطلب لوقت لاحق';

  @override
  String get composerToolNothingToSave => 'اكتب شيئًا أو أرفقه أولًا';

  @override
  String get composerToolsMore => 'المزيد من الأدوات';

  @override
  String get composerReturnedToDraft => 'أُعيد إلى مسودتك';

  @override
  String get promptHistoryIntro => 'اضغط على طلب لإضافته إلى مسودتك.';

  @override
  String get promptEditorDiscardChanges => 'تجاهل التغييرات';

  @override
  String get promptEditorDone => 'استخدام في المسودة';

  @override
  String get promptEditorFieldLabel => 'الطلب';

  @override
  String get promptStashDeleted => 'حُذف الطلب المحفوظ';

  @override
  String get promptStashIntro => 'الأحدث أولًا · محفوظة على هذا الجهاز';

  @override
  String get promptStashEmptyTitle => 'لا توجد طلبات محفوظة بعد';

  @override
  String get promptStashEmptyBody =>
      'اختر «حفظ الطلب لوقت لاحق» من قائمة + للاحتفاظ بطلب هنا.';

  @override
  String get promptStashBusy => 'انتظر حتى تنتهي الخطوة الحالية';

  @override
  String get promptStashRowActions => 'إجراءات الطلب المحفوظ';

  @override
  String get promptStashRestoreToDraft => 'استعادة إلى المسودة';

  @override
  String get promptStashDeleteAction => 'حذف الطلب المحفوظ';

  @override
  String get modelShortcutsNextRecent => 'النموذج التالي من النماذج الأخيرة';

  @override
  String get modelShortcutsPreviousRecent =>
      'النموذج السابق من النماذج الأخيرة';

  @override
  String get modelShortcutsNoRecent =>
      'استخدم نموذجًا آخر أولًا لتتمكن من العودة إليه';

  @override
  String get modelShortcutsNoFavorite =>
      'علّم نموذجًا كمفضّل في قائمة اختيار النموذج أولًا';

  @override
  String get composerDraftBlockedReason =>
      'أجب عن السؤال المتعلق بهذه المسودة أولًا';

  @override
  String get commandLauncherSubtitle =>
      'نفّذ إجراءً في هذه المحادثة، أو أمرًا من هذا الخادم';

  @override
  String get teamChatRefusedTitle => 'لم تُقبل المهمة';

  @override
  String get teamChatRefusedRetry => 'إرسال المهمة مجددًا';

  @override
  String get teamChatGoneTitle => 'لم تعد المهمة مُدرجة';

  @override
  String get teamChatGoneBody =>
      'ربما أُزيلت على حاسوب الفريق. تعرض صفحة AI Team المهام الموجودة لديه الآن.';

  @override
  String get teamChatGoneOpenTeam => 'فتح صفحة AI Team';

  @override
  String connectionReconnectTo(String server) {
    return 'إعادة الاتصال بـ $server';
  }

  @override
  String serverDisconnectFrom(String server) {
    return 'قطع الاتصال بـ $server';
  }

  @override
  String get localAgentStopNamed => 'إيقاف Claude Code';

  @override
  String get localAgentStartNamed => 'تشغيل Claude Code';

  @override
  String monitorSwitchToTitle(String server) {
    return 'هل تريد التبديل إلى $server؟';
  }

  @override
  String monitorSwitchTo(String server) {
    return 'التبديل إلى $server';
  }

  @override
  String servicesStartNamed(String service) {
    return 'تشغيل $service';
  }

  @override
  String servicesStopNamed(String service) {
    return 'إيقاف $service';
  }

  @override
  String serverSettingsChangeSignIn(String server) {
    return 'تغيير بيانات تسجيل الدخول لـ $server';
  }

  @override
  String serverSettingsAuthBasic(String user) {
    return 'المصادقة الأساسية باسم $user';
  }

  @override
  String get serverSettingsUpdateHint =>
      'يستخدم برنامج التثبيت الرسمي لـ OpenCode؛ أعد تشغيل الخادم بعد ذلك.';

  @override
  String get settingsHubModelRow => 'النموذج';

  @override
  String get notifyTurnOnInAndroid => 'تفعيل الإشعارات في Android';

  @override
  String get serversAddOtherWays => 'أو اتصل بطريقة أخرى';

  @override
  String get libraryImportAConversation => 'استيراد محادثة';

  @override
  String get projectHubHealthSubtitle => 'الفرع وخدمات اللغة وأدوات التنسيق';

  @override
  String projectHubChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تغيّر $count من الملفات',
      one: 'تغيّر ملف واحد',
      zero: 'لا توجد تغييرات',
    );
    return '$_temp0';
  }

  @override
  String projectHubTerminalsRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قيد التشغيل',
      one: 'واحدة قيد التشغيل',
    );
    return '$_temp0';
  }

  @override
  String get projectHubCopyFolderPath => 'نسخ مسار المجلد';

  @override
  String get terminalScreenNoTerminalThisServer =>
      'لا يتيح هذا الخادم طرفية مشتركة';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return 'لا يتيح $server طرفية مشتركة';
  }

  @override
  String get terminalScreenNoTerminalWhy =>
      'تُفتح الطرفيات هنا فقط على الخوادم التي تتيح مشاركتها.';

  @override
  String get integrationsSignInWaiting => 'تسجيل الدخول قيد الانتظار';

  @override
  String get integrationsSignInMayNotHaveStarted => 'ربما لم يبدأ تسجيل الدخول';

  @override
  String get integrationsSignInExpired => 'انتهت صلاحية تسجيل الدخول';

  @override
  String get integrationsSignInFailed => 'فشل تسجيل الدخول';

  @override
  String get integrationsSignInComplete => 'تم تسجيل الدخول · اضغط للإكمال';

  @override
  String integrationsFinishSigningIn(String provider) {
    return 'إكمال تسجيل الدخول إلى $provider';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return 'إدخال رمز $provider';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return 'إلغاء تسجيل الدخول إلى $provider';
  }

  @override
  String get integrationsForgetSignInOnPhone =>
      'مسح تسجيل الدخول هذا من هذا الهاتف';

  @override
  String integrationsSignInActions(String provider) {
    return 'إجراءات تسجيل الدخول إلى $provider';
  }

  @override
  String integrationsAccountCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count حسابات',
      one: 'حساب واحد',
    );
    return '$_temp0';
  }

  @override
  String get toolsScreenNoBackgroundSubagents =>
      'لا يوجد وكلاء فرعيون في الخلفية';

  @override
  String get usageRefreshSpending => 'تحديث الإنفاق';

  @override
  String modelPickerUseModel(String model) {
    return 'استخدام $model';
  }

  @override
  String get modelPickerUseChosenModel => 'استخدام النموذج';

  @override
  String get handoffUiComputerCommandLabel => 'أمر الطرفية';

  @override
  String get formRendererDecline => 'رفض هذا الطلب';

  @override
  String get perfTraceActions => 'إجراءات تقرير التوقيت';

  @override
  String voiceSetupNotDownloaded(String size) {
    return 'لم يُنزّل · $size';
  }

  @override
  String get voiceSetupDone => 'إتمام الإعداد';

  @override
  String get voiceAllowMicInSettings => 'السماح بالميكروفون في إعدادات Android';

  @override
  String sessionContextMessagesSplit(String count, String yours, String agent) {
    return '$count ($yours منك، $agent من الوكيل)';
  }

  @override
  String get webSourcesClose => 'إغلاق';

  @override
  String get webSourcesPastedLinks => 'الروابط التي أضفتها';

  @override
  String get thisPhoneHostInApp => 'داخل التطبيق';

  @override
  String get thisPhoneHostTermux => 'في Termux';

  @override
  String get thisPhoneSetUp => 'إعداد OpenCode';

  @override
  String get thisPhoneStart => 'تشغيل الخادم';

  @override
  String get thisPhoneStop => 'إيقاف OpenCode على هذا الهاتف';

  @override
  String get thisPhoneUpdate => 'تحديث OpenCode';

  @override
  String thisPhoneUpdateDetail(String version) {
    return 'يثبّت الإصدار $version';
  }

  @override
  String get thisPhoneAddTools => 'إضافة أدوات';

  @override
  String get thisPhoneInstalled => 'مثبّت';

  @override
  String get thisPhoneTerminal => 'فتح طرفية';

  @override
  String get thisPhoneStorage => 'مساحة التخزين';

  @override
  String get thisPhoneRemove => 'إزالة OpenCode';

  @override
  String get thisPhoneBusy => 'انتظر اكتمال الخطوة الحالية';

  @override
  String get phoneSetupTermuxAllowHow =>
      'في Termux، الصق السطر المنسوخ واضغط Enter.';

  @override
  String get phoneSetupTermuxUpdatingTitle => 'جارٍ تحديث هذا الهاتف';

  @override
  String get phoneSetupTermuxStartingTitle => 'جارٍ تشغيل الخادم';

  @override
  String get phoneSetupTermuxConnecting => 'جارٍ الاتصال';

  @override
  String get phoneSetupTermuxLeaveHint =>
      'يمكنك مغادرة التطبيق. يواصل Termux العمل وتتابع هذه القائمة من الحالة الحالية عند عودتك.';

  @override
  String get phoneSetupTermuxCost =>
      'نحو 10–15 دقيقة في المرة الأولى، في مساحة تخزين Termux';

  @override
  String removeFromPhoneKeepBody(String size) {
    return 'يُزال OpenCode وأدواته، مما يحرر نحو $size.';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured => 'يُزال OpenCode وأدواته.';

  @override
  String get removeFromPhoneKeepConfirm => 'إزالة OpenCode والاحتفاظ بمشاريعي';

  @override
  String get removeFromPhoneDeleteAll => 'حذف كل شيء';

  @override
  String get removeFromPhoneDeleteTitle => 'هل تريد حذف OpenCode والمشاريع؟';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return 'يُحذف OpenCode وأدواته وكل مشروع على هذا الهاتف، مما يحرر نحو $size. لا يمكن التراجع عن ذلك.';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      'يُحذف OpenCode وأدواته وكل مشروع على هذا الهاتف. لا يمكن التراجع عن ذلك.';

  @override
  String get thisPhoneManage => 'إدارة هذا الهاتف';

  @override
  String get chatRequestWho => 'الوكيل';

  @override
  String get chatRequestIfIgnored => 'ينتظر الوكيل حتى تجيب. لن يضيع شيء.';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يوجد $count من الطلبات الأخرى التي تنتظر.',
      one: 'يوجد طلب آخر ينتظر.',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection =>
      'غير متصل بالخادم، لذا لا يمكن الإجابة هنا.';

  @override
  String get chatRequestAlwaysTitle => 'السماح دائمًا بهذه الطلبات';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return 'من الآن، يعمل $patterns دون أن يطلب إذنك، $context. يمكنك التراجع عن ذلك في الإعدادات ضمن «الإجراءات المسموح بها دائمًا».';
  }

  @override
  String chatRequestAlwaysConfirm(String what) {
    return 'السماح دائمًا بـ $what في هذا المشروع؟';
  }

  @override
  String get chatRequestAlwaysInProject => 'في هذا المشروع';

  @override
  String get chatRequestAlwaysOn => 'مسموح دائمًا';

  @override
  String get chatRequestDetailTool => 'الأداة';

  @override
  String get chatRequestDetailPatterns => 'الأنماط المطلوبة';

  @override
  String get chatRequestOtherAnswer => 'إجابة أخرى';

  @override
  String get chatRequestOtherField => 'إجابتك';

  @override
  String get formFlowAnsweredElsewhereBody =>
      'أُجيب عن هذا النموذج على جهاز آخر، لذا لم يُرسل شيء من هذا الهاتف.';

  @override
  String get approvalsUiPausedDetail =>
      'هذا الهاتف غير متصل. تُستأنف الموافقة التلقائية عند إعادة الاتصال.';

  @override
  String get teamUiHomeRunReviewNext => 'يراجعها مُراجع بعد ذلك';

  @override
  String teamUiGateRunStoppedTitle(String title) {
    return 'توقفت $title';
  }

  @override
  String get termuxProcsKindParentGone => 'انتهت العملية الأم';

  @override
  String get termuxProcsKindNoOwner => 'بلا مالك';

  @override
  String termuxProcsStopOrphans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إيقاف العمليات المساعدة اليتيمة وعددها $count',
      one: 'إيقاف عملية مساعدة يتيمة واحدة',
    );
    return '$_temp0';
  }

  @override
  String termuxProcsStopOrphansTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'هل تريد إيقاف العمليات المساعدة اليتيمة وعددها $count؟',
      one: 'هل تريد إيقاف العملية المساعدة اليتيمة؟',
    );
    return '$_temp0';
  }

  @override
  String get teamPhoneStopTeamRow => 'إيقاف الفريق على هذا الهاتف';

  @override
  String get teamPhoneStopTeamRowSupporting =>
      'يتوقف الوكلاء عند موضعهم الحالي؛ لا يُفقد شيء';

  @override
  String teamUiPhoneWorkingOn(String name) {
    return 'يعمل على $name';
  }

  @override
  String get teamUiPhoneVersionsLabel => 'إصدارات المحرك';

  @override
  String get teamUiPhoneProjectLabel => 'مجلد المشروع';

  @override
  String get phoneServerNameInSentence => 'هذا الهاتف';

  @override
  String get teamAgentWorkUnblockedShort => 'لا شيء يعيقها';

  @override
  String get teamAgentWorkBlockedShort => 'متعطّلة';

  @override
  String get teamAgentStepCommand => 'نفّذ أمرًا';

  @override
  String get teamAgentStepTest => 'شغّل الاختبارات';

  @override
  String get teamAgentStepRead => 'قرأ ملفًا';

  @override
  String get teamAgentStepEdit => 'عدّل ملفًا';

  @override
  String get teamAgentStepSearch => 'بحث في الشيفرة';

  @override
  String teamAgentStepTool(String tool) {
    return 'استخدم $tool';
  }

  @override
  String get teamAgentLastCommandLabel => 'آخر أمر';

  @override
  String get termuxStorageOnlyBuildCaches =>
      'يمكن تنظيف ذاكرة البناء المؤقتة فقط هنا';

  @override
  String get termuxStorageWhereItIs => 'موقعها';

  @override
  String termuxStorageCleanBuildCaches(String size) {
    return 'تنظيف ذاكرة البناء المؤقتة ($size)';
  }

  @override
  String get monitorBackgroundChecks => 'الفحوص في الخلفية';

  @override
  String get settingsTryDemo => 'تجربة العرض التجريبي';

  @override
  String get searchArchivedConversations => 'المحادثات المؤرشفة';

  @override
  String get readAloudConsentEngine =>
      'تُعرض فقط الأصوات التي تعمل دون اتصال، لكن محرّك النطق برنامج منفصل له شروط خصوصية خاصة به.';

  @override
  String get readAloudConsentHeard =>
      'قد يسمعها الأشخاص القريبون منك. تتوقف القراءة عندما تغادر هذه المحادثة أو التطبيق.';

  @override
  String get transcriptFindStopSearchingAll => 'إيقاف البحث في الرسائل الأقدم';

  @override
  String nudgeReviewChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'غيّر OpenCode عددًا من الملفات يبلغ $count. راجعها قبل المتابعة.',
      one: 'غيّر OpenCode ملفًا واحدًا. راجعه قبل المتابعة.',
    );
    return '$_temp0';
  }

  @override
  String get voiceComponentTitle => 'الكتابة الصوتية';

  @override
  String get voiceComponentSummary => 'تحدّث بدلًا من الكتابة، حتى دون اتصال';

  @override
  String get voiceComponentRemove => 'إزالة الكتابة الصوتية';

  @override
  String get voiceComponentRemoveTitle => 'هل تريد إزالة الكتابة الصوتية؟';

  @override
  String voiceComponentRemoveBody(String size) {
    return 'يحذف نموذج الكلام ويوفّر $size. تتوقف الكتابة الصوتية حتى تضيفها من هنا مجددًا.';
  }

  @override
  String get setupAppStageDownloading => 'جارٍ التنزيل';

  @override
  String get setupAppStageVerifying => 'جارٍ التحقّق من التنزيل';

  @override
  String kitDiffFilePosition(int index, int count) {
    return '$index من $count';
  }

  @override
  String kitDiffFilePositionSpoken(int index, int count) {
    return 'الملف $index من $count';
  }

  @override
  String get kitDiffViewed => 'تم الاطّلاع عليه';

  @override
  String get kitDiffSelectHunk => 'تحديد هذه الأسطر';

  @override
  String get kitCapFlagTerminalTitle => 'الطرفية';

  @override
  String get kitCapFlagTerminalWhy => 'لا يفتح هذا الخادم طرفية لك.';

  @override
  String get kitCapFlagToolInventoryTitle => 'قائمة الأدوات';

  @override
  String get kitCapFlagToolInventoryWhy =>
      'لا يعرض هذا الخادم قائمة الأدوات التي يمكن لوكيله استخدامها.';

  @override
  String get demoScreenReset => 'إعادة ضبط العرض التجريبي';

  @override
  String get demoScreenLeave => 'مغادرة العرض التجريبي';

  @override
  String get demoScreenDisclosure =>
      'كل شيء هنا محاكاة على هذا الجهاز. لا يجري الوصول إلى أي خادم أو مزوّد أو ملفات.';

  @override
  String capabilityScreenIntroWithGaps(String server) {
    return 'يحدّد $server ما يظهر في هذا التطبيق. تُحذف الميزات التي لا يدعمها من القوائم وعلامات التبويب بدلًا من عرضها بلون باهت. تعمل الميزات المفقودة على خوادم OpenCode أخرى.';
  }

  @override
  String activeContextMessageTitle(String role) {
    String _temp0 = intl.Intl.selectLogic(role, {
      'user': 'رسالة المستخدم',
      'assistant': 'رسالة المساعد',
      'system': 'رسالة النظام',
      'synthetic': 'رسالة مُنشأة',
      'skill': 'رسالة المهارة',
      'shell': 'رسالة الطرفية',
      'compaction': 'رسالة الملخّص',
      'change': 'تغيير المحادثة',
      'other': 'رسالة',
    });
    return '$_temp0';
  }

  @override
  String get chatDraftCopy => 'نسخ المسودة';

  @override
  String get reportProblemDescribeLabel => 'ماذا حدث؟';

  @override
  String get reportProblemDescribeHint =>
      'ما فعلته وما توقعته وما حصلت عليه بدلًا منه';

  @override
  String get reportProblemDescribeFirst => 'صف ما حدث أولًا';

  @override
  String reportProblemErrorsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أخطاء حديثة: $count',
      one: 'خطأ حديث واحد',
    );
    return '$_temp0';
  }

  @override
  String get reportProblemClearFailed =>
      'تعذّر مسح التقرير المحفوظ. حاول مجددًا.';

  @override
  String get reportProblemLinkCopiesDiagnostics =>
      'بيانات التشخيص أطول مما يسمح به الرابط. تُنسخ عند فتح النموذج، فالصقها في حقل التشخيص فيه.';

  @override
  String get reportProblemLinkCopiesWhole =>
      'التقرير أطول مما يسمح به الرابط. يُنسخ عند فتح النموذج، فالصقه فيه.';

  @override
  String get reportProblemCopied => 'نُسخ التقرير';

  @override
  String get reportProblemDiagnosticsCopied =>
      'نُسخت بيانات التشخيص: الصقها في النموذج';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أخطاء محفوظة: $count',
      one: 'خطأ واحد محفوظ',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail =>
      'Python وAI Team والكتابة الصوتية وغيرها';

  @override
  String get phoneSetupTermuxOtherRuntime =>
      'يشغّل Termux نسخة OpenCode الأخرى بالفعل. بدّل النسخة من «هذا الهاتف»، ثم تابع الإعداد.';

  @override
  String get undoFromHereNowAction => 'تطبيق التراجع الآن';

  @override
  String undoFromHereBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'يُزال هذا الطلب والرسائل التي تليه، وعددها $count، وتعود الملفات إلى حالتها قبله. يمكنك استعادتها حتى ترسل طلبًا آخر.',
      one:
          'يُزال هذا الطلب والرسالة التي تليه، وتعود الملفات إلى حالتها قبله. يمكنك استعادتهما حتى ترسل طلبًا آخر.',
      zero:
          'يُزال هذا الطلب، وتعود الملفات إلى حالتها قبله. يمكنك استعادته حتى ترسل طلبًا آخر.',
    );
    return '$_temp0';
  }

  @override
  String get undoFromHereBodyUnknown =>
      'يُزال هذا الطلب وكل ما يليه، وتعود الملفات إلى حالتها قبله. يمكنك استعادتها حتى ترسل طلبًا آخر.';

  @override
  String get undoFromHereFilesLabel => 'الملفات التي عدّلها الوكيل بعده';

  @override
  String get undoFromHereNoEdits =>
      'لم يُبلّغ الوكيل عن تعديلات ملفات بعد هذا الطلب.';

  @override
  String get undoneStatus => 'تم التراجع بدءًا من طلب';

  @override
  String get undonePutBack => 'استعادة المحتوى';

  @override
  String reviewRevertScreenIntroCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'هذا الطلب والرسائل التي تليه، وعددها $count، مخفية. لا يصبح شيء نهائيًا حتى تختار أدناه.',
      one:
          'هذا الطلب والرسالة التي تليه مخفيان. لا يصبح شيء نهائيًا حتى تختار أدناه.',
      zero:
          'هذا الطلب مخفي؛ لم تأتِ بعده أي رسالة. لا يصبح شيء نهائيًا حتى تختار أدناه.',
    );
    return '$_temp0';
  }

  @override
  String reviewRevertKeepConsequenceCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يُحذف الطلب المخفي والرسائل التي تليه، وعددها $count',
      one: 'يُحذف الطلب المخفي والرسالة التي تليه',
      zero: 'يُحذف الطلب المخفي',
    );
    return '$_temp0';
  }

  @override
  String addServerConnectedHost(String host) {
    return 'متصل بـ $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return 'لم يردّ $host بعد. قد يستغرق الأمر بعض الوقت على شبكة بطيئة.';
  }

  @override
  String get addServerCheckCancel => 'إيقاف التحقّق';

  @override
  String get addServerUseTailscale => 'استخدام Tailscale';

  @override
  String get addServerStepsLabel => 'تقدّم إضافة الخادم';

  @override
  String get addServerStepKind => 'ما الذي يعمل هناك';

  @override
  String get addServerStepTailscale => 'Tailscale على هذا الهاتف';

  @override
  String get addServerStepPair => 'الاقتران أو إدخال العنوان';

  @override
  String get addServerStepAddress => 'العنوان وتسجيل الدخول';

  @override
  String get addServerStepCheck => 'جارٍ التحقّق';

  @override
  String get addServerStepReady => 'جاهز';

  @override
  String addServerReadyTitle(String name) {
    return 'تم الاتصال بـ $name';
  }

  @override
  String get addServerReadyBody =>
      'ستُفتح محادثاته الآن. ابدأ محادثة أو تابع محادثة موجودة.';

  @override
  String addServerReadyOpen(String name) {
    return 'فتح $name';
  }

  @override
  String get handoffUiLinkAddTitle => 'هل تريد إضافة هذا الخادم؟';

  @override
  String get handoffUiLinkAddServer => 'إضافة خادم';

  @override
  String get failedJobReport => 'الإبلاغ عن هذا الفشل';

  @override
  String get sessionsOlderLoadFailed => 'تعذّر تحميل المحادثات الأقدم.';

  @override
  String get sessionsLoadFailed => 'تعذّر تحميل محادثاتك.';

  @override
  String get sessionsListChanged =>
      'تغيّرت قائمة المحادثات على الخادم. حدّثها لعرض المحادثات الأقدم.';

  @override
  String get handoffUiComputerUnsupported =>
      'لا يمكن لهذا الخادم تقديم أمر يتيح متابعة محادثة على كمبيوتر.';

  @override
  String get handoffUiComputerChanged =>
      'انتقلت هذه المحادثة أو تغيّر خادمها. ارجع وحاول مجددًا.';

  @override
  String commandAuthSheetIntro(String provider) {
    return 'يُنفّذ تسجيل الدخول هذا على خادمك، لا على هذا الهاتف. ابدأه فقط إذا كنت تثق بالخادم وبـ$provider. قد تحتاج إلى إكمال خطوات على الخادم.';
  }

  @override
  String commandAuthCheckNamed(String provider) {
    return 'التحقّق من تسجيل الدخول إلى $provider الآن';
  }

  @override
  String credentialRemoveAccountTitle(String provider, String name) {
    return 'هل تريد إزالة حساب $provider «$name»؟';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return 'إزالة «$name»';
  }

  @override
  String get discoverServicesAliases =>
      'خدمات تطوير خادم معاينة سجلات تشغيل أوامر عمليات';

  @override
  String get discoverCloudEnvironmentsAliases =>
      'سحابة بيئات مُدارة مساحات عمل بعيدة بيئة معزولة';

  @override
  String promptRestoredWithout(String names) {
    return 'استُعيد دون $names؛ أرفقها مجددًا قبل الإرسال';
  }

  @override
  String get promptStashOlderDraftsWaiting =>
      'لم تنتقل بعض المسودات القديمة إلى هنا بعد. لا تزال محفوظة على هذا الجهاز.';

  @override
  String get promptStashOlderDraftsFull =>
      'تنتظر المسودات القديمة الانتقال إلى هنا. احذف طلبات محفوظة لإفساح المجال.';

  @override
  String get quotaCollectorGuide => 'فتح دليل أداة الجمع';

  @override
  String get usageSpentToday => 'إنفاق اليوم';

  @override
  String get usageSpentThirtyDays => 'إنفاق آخر 30 يومًا';

  @override
  String get usageSpentYear => 'إنفاق هذا العام';

  @override
  String get usageSpentAllTime => 'إجمالي الإنفاق';

  @override
  String usageSpentPeriod(String period) {
    return 'الإنفاق · $period';
  }

  @override
  String get automationTitle => 'ما يعمل تلقائيًا';

  @override
  String get automationSearchAliases =>
      'أتمتة تلقائي إشراف موافقة تلقائية موافقات سماح دائم أذونات خلفية متابعة مراقبة فريق مستوى';

  @override
  String get automationSaveFailed =>
      'لم يُحفظ هذا الاختيار على هذا الهاتف. لا يزال المستوى أعلاه هو المستخدم؛ حاول مجددًا.';

  @override
  String get automationSaving => 'جارٍ الحفظ…';

  @override
  String get automationTeamLabel => 'مدى استقلال AI Team في اتخاذ القرارات';

  @override
  String get automationTeamFootnote =>
      'تبدأ مهام الفريق الجديدة بهذا المستوى. يمكنك اختيار مستوى آخر لمهمة بعينها عند بدئها.';

  @override
  String get automationWithoutAskingLabel => 'دون طلب إذنك';

  @override
  String get automationSavedRulesDetail =>
      'ما يمكن للوكيل تشغيله هنا دون طلب إذنك.';

  @override
  String phoneSetupStartTermuxProgressHeadline(int percent) {
    return 'اكتمل $percent% من الإعداد في Termux';
  }

  @override
  String get teamPhoneReadyChooseTitle => 'اختيار مشروع الفريق';

  @override
  String get teamPhoneReadyTurningOnTitle => 'جارٍ تفعيل AI Team';

  @override
  String get teamPhoneReadyFailedTitle => 'لم يبدأ AI Team';

  @override
  String teamPhoneReadyBody(String project) {
    return 'أعطه مهمة أولى. يخطط للعمل ويوزّعه على وكلائه ويعيد النتيجة إلى $project.';
  }

  @override
  String get teamPhoneReadyFirstTask => 'إعطاء الفريق مهمة أولى';

  @override
  String get teamUiStateNotAnsweringPhone =>
      'يواصل التطبيق المحاولة أثناء بدء الفريق على هذا الهاتف.';

  @override
  String get teamUiStateNotAnsweringComputer =>
      'يواصل التطبيق المحاولة. تحقّق من أن حاسوبك يعمل ومتصل بالشبكة.';

  @override
  String teamUiStateNotAnsweringComputerNamed(String computer) {
    return 'يواصل التطبيق المحاولة. تحقّق من أن $computer يعمل ومتصل بالشبكة.';
  }

  @override
  String get teamHomeChangeAddress => 'تغيير العنوان';

  @override
  String get teamHomeTurnOffFailed =>
      'تعذّر إيقاف الفريق على هذا الهاتف، لذا لا يزال يعمل. حاول مجددًا.';

  @override
  String get teamHomeHostStopped => 'متوقف';

  @override
  String get teamHomeHostCooling => 'جارٍ التبريد';

  @override
  String get teamHomeHostStoppedForHeat => 'متوقف حتى يبرد';

  @override
  String teamHomeHeatPausedLine(String time) {
    return 'ارتفعت حرارة الهاتف عند $time، لذا توقف الفريق مؤقتًا. يتابع تلقائيًا عندما يبرد الهاتف.';
  }

  @override
  String teamHomeHeatStoppedLine(String time) {
    return 'ارتفعت حرارة الهاتف كثيرًا عند $time، لذا توقف الفريق. يبقى عمله محفوظًا، ويبدأ مجددًا عندما يبرد الهاتف.';
  }

  @override
  String get teamHomePhoneControls => 'إبقاؤه قيد التشغيل أو إيقافه أو إزالته';

  @override
  String teamHomeSpentToday(String usage) {
    return 'اليوم · $usage';
  }

  @override
  String get teamHomeSpentHint =>
      'تقدير للفريق كاملًا منذ منتصف الليل في مكان تشغيله. لا يبلّغ الخادم عن تكلفة كل مهمة.';

  @override
  String get teamHomeSpentPartial =>
      'بعض استخدام اليوم ليس له سعر بعد، لذا كانت التكلفة أعلى من هذا المبلغ.';

  @override
  String teamIntroNotFound(String server) {
    return 'لم يُعثر على AI Team على $server';
  }

  @override
  String get chatErrorModelNotFound => 'هذا النموذج غير موجود على الخادم.';

  @override
  String get chatErrorContextOverflow =>
      'هذه المحادثة أطول مما يستطيع النموذج استيعابه.';

  @override
  String get chatErrorProviderAuth =>
      'يحتاج مزوّد النموذج إلى تسجيل دخولك مجددًا.';

  @override
  String get chatErrorOutputLength => 'بلغ الرد حد الطول المسموح به للنموذج.';

  @override
  String get chatErrorContentFilter =>
      'أوقف مرشّح الأمان لدى المزوّد هذا الرد.';

  @override
  String get chatErrorUnknown => 'توقف الوكيل بسبب خطأ.';

  @override
  String modelPickerThinkingChip(String level) {
    return 'التفكير: $level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return 'الوكيل: $agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'تُنقل الطلبات في قائمة الانتظار وعددها $count إلى الطلبات المحفوظة',
      one: 'يُنقل طلب واحد في قائمة الانتظار إلى الطلبات المحفوظة',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'قد يكون $count منها أُرسل بالفعل',
      one: 'قد يكون أحدها أُرسل بالفعل',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إزالة الخادم وحذف الطلبات في قائمة الانتظار وعددها $count',
      one: 'إزالة الخادم وحذف الطلب في قائمة الانتظار',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return 'تغيّرت الطلبات في قائمة الانتظار لـ $name، لذا لم يُحذف شيء. أزل الخادم مجددًا لعرض العدد الجديد.';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return 'تعذّر نقل الطلبات في قائمة الانتظار لـ $name إلى الطلبات المحفوظة، لذا لم يُحذف شيء. احذف بعض الطلبات المحفوظة أو وفّر مساحة تخزين، ثم حاول مجددًا.';
  }

  @override
  String get settingsHubGroupAgent => 'الوكيل';

  @override
  String get settingsHubGroupConversations => 'المحادثات';

  @override
  String get settingsHubGroupThisApp => 'هذا التطبيق';

  @override
  String get settingsHubProvidersRow => 'المزوّدون والحسابات';

  @override
  String get settingsHubToolsRow => 'الأدوات';

  @override
  String get settingsHubShowReasoning => 'إظهار الاستدلال';

  @override
  String get settingsHubShowTimestamps => 'إظهار الوقت والرموز والتكلفة';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إعدادات غير متاحة على هذا الخادم: $count',
      one: 'إعداد واحد غير متاح على هذا الخادم',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => 'معرفة السبب';

  @override
  String get privacyPolicyTitle => 'سياسة الخصوصية';

  @override
  String get aboutHelpSection => 'نصائح واختصارات';

  @override
  String get settingsHubSearchToolsAliases =>
      'أدوات mcp تكاملات أوامر مهارات مراجع شرطة مائلة إمكانات إضافات وكلاء خارجيون a2a';

  @override
  String get aiteamComponentTurnOff => 'إيقاف AI Team';

  @override
  String get aiteamComponentTurnOffTitle => 'هل تريد إيقاف AI Team؟';

  @override
  String get aiteamComponentTurnOffBody =>
      'يتوقف الفريق ويبقى متوقفًا حتى تشغّله مجددًا.';

  @override
  String get aiteamComponentTurnOffKept => 'تبقى مهامه وإعداداته ومشاريعك';

  @override
  String get aiteamComponentTurnOffFailed =>
      'تعذّر إيقاف AI Team. حاول مجددًا أو أعد تشغيل التطبيق.';

  @override
  String thisPhoneRemoveTool(String tool) {
    return 'إزالة $tool';
  }

  @override
  String thisPhoneRemoveToolTitle(String tool) {
    return 'هل تريد إزالة $tool؟';
  }

  @override
  String thisPhoneRemoveToolBody(String size) {
    return 'تتوفر نحو $size من المساحة. يمكنك إضافته مجددًا من «إضافة أدوات».';
  }

  @override
  String get thisPhoneRemoveToolBodyUnmeasured =>
      'يمكنك إضافته مجددًا من «إضافة أدوات».';

  @override
  String thisPhoneRemoveToolNeededBy(String tools) {
    return 'تحتاجه $tools';
  }

  @override
  String get thisPhoneRemoveToolDetail => 'يحذفه من هذا الهاتف. تبقى مشاريعك.';

  @override
  String get thisPhoneRemovePythonDetail =>
      'يحذف pip وvenv. يبقى Python ومشاريعك.';

  @override
  String get thisPhoneRemovePythonLost =>
      'يُحذف pip وvenv والحزم التي لا يستخدمها غيرهما';

  @override
  String get thisPhoneRemovePythonKept => 'يبقى Python نفسه ومشاريعك';

  @override
  String get thisPhoneRemoveTeamDetail =>
      'يحذف برامج الفريق ومهامه وإعداداته. تبقى المشاريع.';

  @override
  String get thisPhoneRemoveTeamLost => 'تُحذف برامج الفريق ومهامه وإعداداته';

  @override
  String get thisPhoneRemoveTeamLostWork =>
      'يضيع عمل الفريق الذي لم يُنقل إلى مشاريعك بعد';

  @override
  String get thisPhoneRemoveTeamKept => 'تبقى ملفات مشاريعك وسجلها في git';

  @override
  String get thisPhoneRemoveVoiceDetail =>
      'يحذف نموذج الكلام. تتوقف الكتابة الصوتية حتى تضيفه مجددًا.';

  @override
  String get thisPhoneRemoveVoiceLost =>
      'تتوقف الكتابة الصوتية حتى تضيفها مجددًا';

  @override
  String get thisPhoneRemoveToolKept => 'تبقى مشاريعك';

  @override
  String get removeFromPhoneKeepLost =>
      'تُحذف المحادثات والإعدادات داخل OpenCode';

  @override
  String get removeFromPhoneKeepKept => 'تبقى مشاريعك وتعود عند الإعداد مجددًا';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return 'تبقى مشاريعك ($size) وتعود عند الإعداد مجددًا';
  }

  @override
  String get removeFromPhoneDeleteLost =>
      'تضيع نهائيًا ملفات المشاريع غير المحفوظة في مكان آخر';

  @override
  String get productErrorTimedOut =>
      'استغرق الخادم وقتًا طويلًا للرد. حاول مجددًا.';

  @override
  String get productErrorCertificate =>
      'شهادة أمان الخادم غير موثوقة، لذا توقف التطبيق. تحقّق من عنوان الخادم.';

  @override
  String get productErrorSignIn =>
      'لم يقبل الخادم تسجيل الدخول. تحقّق من كلمة المرور في إعدادات الخادم.';

  @override
  String get productErrorNotFound =>
      'تعذّر على الخادم العثور عليه. ربما نُقل أو حُذف.';

  @override
  String get productErrorConflict =>
      'تغيّر على الخادم في هذه الأثناء. حدّث الحالة، ثم حاول مجددًا.';

  @override
  String get productErrorBusy => 'الخادم مشغول. انتظر قليلًا، ثم حاول مجددًا.';

  @override
  String get productErrorRejected =>
      'لم يقبل الخادم الطلب. حاول مجددًا، أو أبلغ عن المشكلة.';

  @override
  String get productErrorUnknown =>
      'لم ينجح الإجراء. تعرض التفاصيل ما حدث. حاول مجددًا، أو أبلغ عن المشكلة.';

  @override
  String get productErrorUnexpected =>
      'تعذّر على التطبيق فهم رد الخادم. حاول مجددًا، أو أبلغ عن المشكلة.';

  @override
  String get productErrorDevice => 'تعطّل شيء على هذا الجهاز. حاول مجددًا.';

  @override
  String get productErrorStorage =>
      'تعذّر على التطبيق قراءة ملف على هذا الجهاز أو حفظه.';

  @override
  String get productErrorTermux =>
      'لم يُكمل Termux هذا الإجراء. تحقّق من أن Termux مثبّت ومفتوح، ثم حاول مجددًا.';

  @override
  String get productErrorDetailsLabel => 'تفاصيل الخطأ';

  @override
  String productErrorServer(int code) {
    return 'واجه الخادم مشكلة (الخطأ $code). حاول مجددًا بعد قليل.';
  }

  @override
  String get usageBudgetInvalidUsd => 'أدخل مبلغًا أكبر من 0، مثل 2.50';

  @override
  String get usageBudgetInvalidTokens =>
      'أدخل عددًا صحيحًا من الرموز أكبر من 0';

  @override
  String get usageBudgetSaveUsd => 'حفظ ميزانية الدولار الأمريكي';

  @override
  String get usageBudgetSaveTokens => 'حفظ ميزانية الرموز';

  @override
  String sessionDestinationMoveWithChanges(String destination) {
    return 'نقل إلى $destination مع التغييرات';
  }

  @override
  String sessionDestinationWarpWithChanges(String destination) {
    return 'نقل إلى $destination مع نسخة من التغييرات';
  }

  @override
  String sessionDestinationMoveTo(String destination) {
    return 'نقل إلى $destination';
  }

  @override
  String sessionDestinationNoChanges(String place) {
    return 'لا توجد تغييرات عمل في $place، لذا تنتقل المحادثة وحدها.';
  }

  @override
  String get settingsBackgroundOffFailed => 'لم يوقف Android وضع الخلفية.';

  @override
  String defaultReviewScopeNotice(String scope) {
    return 'يُعرض $scope: فهو العرض الذي يتضمّن تغييرات.';
  }

  @override
  String defaultModelNotice(String model) {
    return 'يُستخدم $model، النموذج الافتراضي لهذا الخادم.';
  }

  @override
  String get defaultModelChange => 'اختيار نموذج آخر';

  @override
  String teamControlReceiptSending(String control) {
    return '$control · جارٍ الإرسال…';
  }

  @override
  String get teamGateCardRunFailedOpen => 'اختيار الإجراء';

  @override
  String get teamGateCardIfIgnored => 'ينتظر الفريق حتى تجيب. لن يضيع شيء.';

  @override
  String get teamGateCardIfIgnoredFailed =>
      'تبقى المهمة متوقفة حتى يتخذ أحد إجراءً بشأنها.';

  @override
  String get teamGateCardIfIgnoredReview =>
      'ينتظر العمل المراجعة. لن يضيع شيء.';

  @override
  String termuxProcsBudget(int count, int limit) {
    return '$count من أصل $limit عملية في الخلفية';
  }

  @override
  String termuxProcsBudgetNote(int limit) {
    return 'قد يوقف Android 12 والإصدارات الأحدث أقدم العمليات عندما يتجاوز مجموع العمليات في كل التطبيقات $limit.';
  }

  @override
  String termuxProcsBudgetOver(int limit) {
    return 'أكثر من $limit: قد يوقف Android أقدم هذه العمليات في أي وقت.';
  }

  @override
  String get termuxProcsLoadFailedBody =>
      'لم يردّ Termux. افتح Termux، ثم حاول مجددًا.';

  @override
  String get termuxProcsRefreshFailed =>
      'تعذّرت قراءة القائمة مجددًا، لذا تُعرض آخر قراءة.';

  @override
  String get termuxProcsStopFailed =>
      'تعذّر إيقافها. حاول مجددًا أو أوقفها من Termux.';

  @override
  String get termuxProcsKindOpenCode => 'خادم OpenCode';

  @override
  String get termuxProcsKindAiTeam => 'AI Team';

  @override
  String get termuxProcsKindClaudeCode => 'Claude Code';

  @override
  String get termuxProcsKindDevService => 'خدمة تطوير';

  @override
  String get termuxProcsKindTerminal => 'طرفية';

  @override
  String get termuxProcsKindHelper => 'عملية مساعدة';

  @override
  String get termuxProcsKindHostApp => 'تطبيق Termux';

  @override
  String get termuxProcsBusy => 'مشغولة';

  @override
  String get termuxProcsIdle => 'خاملة';

  @override
  String termuxProcsRunningFor(String elapsed) {
    return 'قيد التشغيل منذ $elapsed';
  }

  @override
  String termuxProcsStopKindBody(String names) {
    return '$names: تُرسل إشارة إيقاف عادية لكل عملية، ثم تُوقف بالقوة بعد 5 ثوانٍ.';
  }

  @override
  String termuxProcsStopKind(int count, String things) {
    return 'إيقاف كل $things وعددها $count';
  }

  @override
  String termuxProcsStopKindTitle(int count, String things) {
    return 'هل تريد إيقاف كل $things وعددها $count؟';
  }

  @override
  String get termuxProcsKindsAiTeam => 'عمليات AI Team';

  @override
  String get termuxProcsKindsClaudeCode => 'عمليات Claude Code';

  @override
  String get termuxProcsKindsDevServices => 'خدمات التطوير';

  @override
  String get termuxProcsKindsTerminals => 'الطرفيات';

  @override
  String get termuxProcsKindsHelpers => 'العمليات المساعدة';

  @override
  String get termuxProcsStopDevRestart =>
      'تبدأ مجددًا عند حاجة عملية البناء التالية إليها.';

  @override
  String get termuxProcsAboutClaudeCode =>
      'Claude Code، وكيل البرمجة. إيقافه ينهي الرد الذي يكتبه.';

  @override
  String get termuxProcsAboutTerminal =>
      'طرفية. إيقافها يغلقها ويوقف ما يعمل فيها.';

  @override
  String get termuxProcsAboutHostApp => 'تطبيق Termux نفسه. لا يُوقف من هنا.';

  @override
  String get termuxProcsAverageCpu => 'متوسط استخدام المعالج';

  @override
  String get termuxProcsCpuTime => 'وقت المعالج';

  @override
  String get consentBatteryTitle => 'هل تريد إبقاء الخادم قيد التشغيل؟';

  @override
  String get consentBatteryBody =>
      'قد يوقف Android الخادم على هذا الهاتف أثناء إغلاق التطبيق. اسمح بالتشغيل في الخلفية وسيطلب منك Android التأكيد.';

  @override
  String get consentBatteryAllow => 'السماح بالتشغيل في الخلفية';

  @override
  String get consentMakerTitle => 'هل تريد إعادة تشغيل الخادم تلقائيًا؟';

  @override
  String consentMakerBody(String maker) {
    return 'توقف هواتف $maker التطبيقات التي لا يُسمح لها بالبدء تلقائيًا، فيبقى الخادم متوقفًا. فعّل البدء التلقائي لهذا التطبيق في الشاشة التي ستُفتح.';
  }

  @override
  String get consentMakerBodyUnnamed =>
      'توقف بعض الهواتف التطبيقات التي لا يُسمح لها بالبدء تلقائيًا، فيبقى الخادم متوقفًا. فعّل البدء التلقائي لهذا التطبيق في الشاشة التي ستُفتح.';

  @override
  String get consentMakerAllow => 'فتح إعدادات البدء التلقائي';

  @override
  String get consentNotNow => 'ليس الآن';

  @override
  String get consentSaveFailed =>
      'تعذّر حفظ إجابتك على هذا الهاتف، لذا لم يتغيّر شيء. حاول مجددًا.';

  @override
  String get consentStorageFailed =>
      'تعذّرت قراءة إجاباتك السابقة على هذا الخادم، لذا لن يكرّر التطبيق هذه الأسئلة حاليًا. افتح هذه الصفحة مجددًا للمحاولة.';

  @override
  String get consentGroupLabel => 'إجاباتك';

  @override
  String get consentRowBattery => 'التشغيل في الخلفية';

  @override
  String get consentRowMaker => 'البدء مجددًا تلقائيًا';

  @override
  String get consentRowNeedsYou => 'إبلاغي عندما يحتاجني الوكيل';

  @override
  String get consentRowAlwaysAllow => 'اقتراح السماح دائمًا';

  @override
  String get consentWhyBattery =>
      'قد يوقف Android الخادم على هذا الهاتف أثناء إغلاق التطبيق.';

  @override
  String get consentWhyMaker =>
      'قد لا يشغّل هذا الهاتف الخادم مجددًا بعد توقفه.';

  @override
  String get consentWhyNeedsYou => 'لن يصلك إشعار عندما ينتظر الوكيل إجابتك.';

  @override
  String get consentAllowedSystem =>
      'يتحكّم إعداد الهاتف في ذلك. اضغط للتحقّق منه أو لإيقافه.';

  @override
  String get consentAllowedNeedsYou => 'اضغط لتغييره في الإشعارات.';

  @override
  String get consentWhyAlwaysAllow =>
      'لا يزال السؤال يُطرح كل مرة. اضغط لاقتراح السماح دائمًا مجددًا.';

  @override
  String get consentValueAllowed => 'مسموح';

  @override
  String get consentValueDeclined => 'مرفوض';

  @override
  String get consentValueUnanswered => 'بلا إجابة';

  @override
  String consentValueDeclinedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حالات الرفض: $count',
      one: 'رفض واحد',
    );
    return '$_temp0';
  }

  @override
  String get consentAlwaysAgainTitle => 'هل تريد اقتراح السماح دائمًا؟';

  @override
  String get consentAlwaysAgainBody =>
      'بعد 3 طلبات متطابقة أخرى، يقترح التطبيق السماح بها دائمًا مجددًا. لن يُسمح بشيء حتى توافق.';

  @override
  String get consentAlwaysAgainConfirm => 'اقتراح السماح مجددًا';

  @override
  String consentAlwaysAllowQuestion(String what) {
    return 'طُلب 3 مرات. هل تريد السماح دائمًا بـ $what؟';
  }

  @override
  String get consentAlwaysAllowDecline => 'متابعة طرح السؤال';

  @override
  String get consentAlwaysAllowFailed =>
      'لم يحفظ الخادم خيار السماح دائمًا. لا يزال الطلب ينتظر؛ حاول مجددًا أو أجبه لهذه المرة.';

  @override
  String get consentAlwaysAllowTitle => 'هل تريد السماح بهذا الطلب دائمًا؟';

  @override
  String get consentNeedsYouAllow => 'تفعيل الإشعارات';

  @override
  String get bootstrapOpeningTitle => 'جارٍ الفتح…';

  @override
  String get bootstrapOpeningBody => 'جارٍ قراءة الخوادم المحفوظة.';

  @override
  String get bootstrapFailedTitle => 'تعذّرت قراءة الخوادم المحفوظة';

  @override
  String get bootstrapFailedBody =>
      'إذا أُعيد تشغيل هاتفك للتو، افتح قفله ثم حاول مجددًا.';

  @override
  String get shareFailedLine => 'حُفظ النص المشترك · تعذّر فتح محادثة';

  @override
  String get shareFailedAgainLine =>
      'لا يزال فتح محادثة متعذّرًا · حُفظ النص المشترك';

  @override
  String get shareFailedCopy => 'نسخ النص المشترك';

  @override
  String get shareFailedDiscard => 'تجاهل النص المشترك';

  @override
  String get shareDiscarded => 'تم تجاهل النص المشترك';

  @override
  String get shareConnectionChanged =>
      'تغيّر الخادم أو المشروع أثناء الفتح. حاول مجددًا.';

  @override
  String get appNewConversationFailed => 'تعذّر بدء محادثة جديدة';

  @override
  String get rootPhoneServerStartFailed => 'لم يبدأ OpenCode على هذا الهاتف';

  @override
  String get connectionFailureLocalCodexBody =>
      'يُفترض أن تستجيب نقطة اتصال Codex محلية على هذا الهاتف، لكن لم يصل أي رد.';

  @override
  String get connectionFailureRemoteCodexBody =>
      'لم يصل أي رد من نقطة اتصال Codex.';

  @override
  String get connectionFailureLoopbackBody =>
      'بحث التطبيق عن خادم يعمل على هذا الهاتف ولم يتلقّ ردًا. شغّل الخادم، أو أعد اتصال النفق الذي يوصله إلى هنا، ثم حاول مجددًا.';

  @override
  String get connectionFailureTimedOutBody =>
      'توجد جهة على هذا العنوان، لكنها لم تردّ. عادةً تكون المشكلة في الشبكة بينكما، لا في الخادم.';

  @override
  String get connectionFailureNothingAnsweredBody =>
      'لم يصل أي رد. إما أن الخادم لا يعمل، أو أن هذا الهاتف لا يستطيع الوصول إلى عنوانه.';

  @override
  String get connectionFailureUnknownBody =>
      'فشل الاتصال. ستجد سبب المشكلة في التفاصيل.';

  @override
  String get connectionFailureTailnetCheck =>
      'هذا عنوان Tailscale: هل Tailscale مفعّل على هذا الهاتف وعلى الخادم؟';

  @override
  String get teamHomeSpentHistoryMissing =>
      'جزء من سجل اليوم مفقود، لذا كانت التكلفة أعلى من هذا المبلغ.';

  @override
  String get teamHomeSpentNotRecording =>
      'لا يحسب الفريق الاستخدام الجديد حاليًا.';

  @override
  String get teamRunCostUnreported =>
      'لا يُبلّغ عنها لكل مهمة. تعرض صفحة AI Team تقدير اليوم للفريق كاملًا.';

  @override
  String get teamHomeUpkeepTitle => 'صيانة الفريق';

  @override
  String get teamHomeUpkeepPatrol => 'متابعة دورية';

  @override
  String get teamHomeUpkeepChore => 'مهمة صيانة';

  @override
  String teamHomeUpkeepGroup(int count, String kind, String state) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$kind ×$count · $state',
      one: '$kind · $state',
    );
    return '$_temp0';
  }

  @override
  String get teamAgentLooksAfterTeam => 'الفريق كاملًا';

  @override
  String get teamAgentLooksAfterWatchdog => 'المراقب';

  @override
  String get teamAgentLooksAfterWorkers => 'العاملون';

  @override
  String get servicesStopConfirm => 'إيقاف الخدمة';

  @override
  String get servicesRestartConfirm => 'إعادة تشغيل الخدمة';

  @override
  String get managedWorkspacesRemoveConfirm => 'إزالة البيئة';

  @override
  String managedWorkspacesCreateIn(String provider) {
    return 'في $provider';
  }

  @override
  String get voiceAutoSetupTitle => 'الكتابة الصوتية';

  @override
  String get voiceAutoSetupChecking =>
      'جارٍ التحقّق مما يستطيع هذا الهاتف تشغيله';

  @override
  String get voiceAutoSetupOffer =>
      'تحدّث بدلًا من الكتابة. يتحوّل الكلام إلى نص على هذا الهاتف حتى دون اتصال، ولا يغادر الصوت الهاتف أبدًا. يحتاج إلى تنزيل مرة واحدة.';

  @override
  String voiceAutoSetupPicked(String model) {
    return 'نموذج الكلام $model، اختير بحسب ذاكرة هذا الهاتف';
  }

  @override
  String get voiceAutoSetupMobileData =>
      'أنت تستخدم بيانات الهاتف. يُحتسب هذا التنزيل من باقة بياناتك.';

  @override
  String get voiceAutoSetupMaybeMetered =>
      'قد يُحتسب هذا الاتصال من باقة بيانات.';

  @override
  String voiceAutoSetupDownload(String size) {
    return 'تنزيل $size';
  }

  @override
  String voiceAutoSetupDownloadMobile(String size) {
    return 'تنزيل $size عبر بيانات الهاتف';
  }

  @override
  String get voiceAutoSetupOtherModel => 'اختيار نموذج كلام آخر';

  @override
  String get voiceAutoSetupNotified =>
      'يظهر التقدّم أيضًا في إشعاراتك. يبدأ الاستماع عند اكتماله.';

  @override
  String get voiceAutoSetupStartsAfter => 'يبدأ الاستماع عند اكتماله.';

  @override
  String get voiceAutoSetupReady => 'نموذج الكلام موجود على هذا الهاتف.';

  @override
  String get voiceAutoSetupChooseModel => 'اختيار نموذج كلام';

  @override
  String get voiceAutoSetupUnknownMemory =>
      'لم يُبلغ هذا الهاتف عن حجم ذاكرته، لذا لم يُختر نموذج كلام.';

  @override
  String get voiceAutoSetupOffline =>
      'لا يوجد اتصال بالإنترنت. اتصل، ثم حاول مجددًا.';

  @override
  String get voiceAutoSetupBusy => 'جارٍ تنزيل نموذج كلام بالفعل.';

  @override
  String get voiceAutoSetupShowDownload => 'عرض التنزيل';

  @override
  String get voiceAutoSetupNoCapture =>
      'لا يستطيع هذا الهاتف تسجيل الكلام للكتابة الصوتية.';

  @override
  String get voiceAutoSetupDetailFiles => 'الملفات';

  @override
  String get voiceAutoSetupDetailSize => 'الحجم الدقيق';

  @override
  String get voiceAutoSetupDetailMemory => 'الذاكرة';

  @override
  String voiceAutoSetupDetailMemoryValue(int required, int available) {
    return 'يحتاج $required ميغابايت؛ يتوفّر في هذا الهاتف $available ميغابايت';
  }

  @override
  String get terminalScreenEmptyTitle => 'لا توجد طرفيات بعد';

  @override
  String terminalScreenEmptyBody(String project) {
    return 'ابدأ طرفية في $project.';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => 'ابدأ طرفية في هذا المشروع.';

  @override
  String get integrationsProvidersExplanation =>
      'مزوّدو النماذج الذين يمكن لهذا الخادم استخدامهم. اتصل بأحدهم لبدء المحادثة.';

  @override
  String get integrationsResourcesExplanation =>
      'الملفات والبيانات التي تمنحها خوادم MCP المتصلة للوكيل.';

  @override
  String externalAgentsStopTaskTitle(String task) {
    return 'هل تريد إيقاف «$task»؟';
  }

  @override
  String externalAgentsStopTaskConfirm(String agent) {
    return 'طلب الإيقاف من $agent';
  }

  @override
  String get externalAgentsStopTaskKeep => 'متابعة التشغيل';

  @override
  String toolsDetailMenu(String tool) {
    return 'إجراءات $tool';
  }

  @override
  String kitDurationHours(int hours) {
    return '$hours س';
  }

  @override
  String kitDurationHoursMinutes(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String kitDurationDays(int days) {
    return '$days ي';
  }

  @override
  String kitDurationDaysHours(int days, int hours) {
    return '$days ي $hours س';
  }

  @override
  String kitToolFor(String duration) {
    return 'لمدة $duration';
  }

  @override
  String kitSinceWaitingForLong(String duration) {
    return 'ينتظر منذ $duration';
  }

  @override
  String get teamChatLeadRoutedIt => 'أرسلها إلى العاملين';

  @override
  String get teamChatLeadStartingIt => 'بدأ العامل';

  @override
  String get teamChatLeadClaimedWorkerIt => 'تولّى العامل المهمة';

  @override
  String get teamChatLeadPushedIt => 'تغييراته على فرع';

  @override
  String get teamChatLeadReviewIt => 'سلّمها للمراجعة';

  @override
  String get teamChatLeadMergedIt => 'دمجها';

  @override
  String get teamChatLeadStepFailedIt => 'فشلت';

  @override
  String get teamChatLeadStepCancelledIt => 'أُلغيت';

  @override
  String teamChatNowNoProgress(String elapsed) {
    return 'لا تقدّم منذ $elapsed';
  }

  @override
  String teamChatNoProgressBody(String name, String time) {
    return 'لم يحرّك $name هذه المهمة منذ $time. نبّهه للمتابعة أو أعد تشغيله أو أبلغ عن المشكلة.';
  }

  @override
  String teamChatNoProgressBodyNoControls(String name, String time) {
    return 'لم يحرّك $name هذه المهمة منذ $time. لا يمكن لهذا الخادم تنبيهه أو إعادة تشغيله من هنا؛ أبلغ عن المشكلة أو تحقّق من حاسوب الفريق.';
  }

  @override
  String get teamChatNoProgressReport => 'الإبلاغ عن المشكلة';

  @override
  String teamChatNoProgressReportTitle(String elapsed) {
    return 'لا تقدّم منذ $elapsed';
  }

  @override
  String get teamTaskDetailsReported => 'ما أبلغ عنه الخادم';

  @override
  String get kitToolOpenDetails => 'فتح تفاصيله';

  @override
  String get teamStartRunKeepInBacklog => 'إبقاء المهمة في قائمة الأعمال';

  @override
  String workRunawayStopped(String helper) {
    return 'أُوقفت $helper';
  }

  @override
  String workRunawayStopFailed(String helper) {
    return 'تعذّر إيقاف $helper. حاول مجددًا أو أوقفها من Termux.';
  }

  @override
  String get serverSettingsUpdateCommandsDetail =>
      'شغّلها في طرفية على حاسوب الخادم؛ لا يستطيع هذا التطبيق تحديثه.';

  @override
  String get serverSettingsUpdateCommandsCopied =>
      'نُسخت. شغّلها في طرفية على حاسوب الخادم.';

  @override
  String hostServiceTitle(String server) {
    return 'OpenCode على $server';
  }

  @override
  String hostServiceIntro(String server) {
    return 'تُشغّل هذه الأوامر على كمبيوتر $server؛ انسخ كل أمر إلى طرفية هناك.';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return 'مطلوب · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList =>
      'لا يستطيع OpenCode عرض أجهزة شبكتك الخاصة.';

  @override
  String get productErrorStagedRevert =>
      'راجع التراجع المبدئي قبل إرسال هذا الطلب المنتظر.';

  @override
  String teamWatchComposerHint(String name) {
    return 'مراسلة $name…';
  }

  @override
  String teamWatchAboutRole(String role) {
    return 'عن $role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return 'لا يمكن قراءة الطلبات في قائمة الانتظار لـ $name. أُبقي الخادم وطلباته في قائمة الانتظار. حاول إزالته مجددًا بعد أن تصبح القائمة قابلة للقراءة.';
  }

  @override
  String get bootstrapStartFresh => 'البدء من جديد';

  @override
  String get bootstrapStartFreshTitle =>
      'هل تريد إزالة بيانات تسجيل الدخول المحفوظة؟';

  @override
  String get bootstrapStartFreshBody =>
      'يزيل هذا كلمات المرور ورموز الاتصال المحفوظة من هذا الهاتف ويلغي اختيار الخادم الحالي. تبقى خوادمك المحفوظة والطلبات في قائمة الانتظار والمسودات.';

  @override
  String get bootstrapStartFreshConfirm => 'إزالة بيانات تسجيل الدخول المحفوظة';

  @override
  String get bootstrapResettingTitle =>
      'جارٍ إزالة بيانات تسجيل الدخول المحفوظة…';

  @override
  String get bootstrapResettingBody => 'أبقِ التطبيق مفتوحًا حتى يكتمل ذلك.';

  @override
  String get bootstrapResetFailedTitle => 'تعذّرت إعادة ضبط تسجيل الدخول';

  @override
  String get bootstrapResetFailedBody =>
      'تعذّرت إزالة بعض بيانات تسجيل الدخول المحفوظة. حاول مجددًا.';

  @override
  String get workStalled => 'متعثر';

  @override
  String get teamNowActivityPlanning => 'بانتظار خطة';

  @override
  String get teamNowActivityWaitingForWorker => 'بانتظار عامل';

  @override
  String get teamNowActivityStartingWorker => 'جارٍ تشغيل عامل';

  @override
  String get teamNowActivityWorking => 'جارٍ العمل على مهمتك';

  @override
  String get teamNowActivityReviewing => 'جارٍ مراجعة التغييرات';

  @override
  String get teamNowActivityNeedsYou => 'بانتظار إجابتك';

  @override
  String get teamNowActivityDelayed => 'يستغرق وقتًا أطول من المتوقع';

  @override
  String get teamNowActivityUnconfirmed => 'لم يُؤكّد الطلب';

  @override
  String get teamNowActivityRefused => 'لم يُقبل الطلب';

  @override
  String get teamNowActivityUnavailable => 'الفريق لا يردّ';

  @override
  String get teamNowActivityCompleted => 'مكتملة';

  @override
  String get teamNowActivityFailed => 'تعذّر الإكمال';

  @override
  String get teamNowActivityCancelled => 'متوقفة';

  @override
  String get teamNowReasonNoPlanReported =>
      'لم يُبلّغ عن خطة بعد. السبب غير معروف.';

  @override
  String get teamNowReasonNoWorkerReported => 'لم يُبلّغ عن عامل بعد.';

  @override
  String get teamNowReasonWorkerStarting => 'بدأ العامل لكنه لم يبدأ المهمة.';

  @override
  String teamModelRowTitle(String model) {
    return 'يستخدم العاملون $model';
  }

  @override
  String get teamModelDefault => 'مثل OpenCode على هذا الهاتف';

  @override
  String get teamModelDefaultHint =>
      'يستخدم النموذج المحدّد في OpenCode على هذا الهاتف.';

  @override
  String get teamModelChange =>
      'تغيير النموذج. يسري عند بدء عامل في المرة التالية.';

  @override
  String get teamModelSheetTitle => 'نموذج العاملين';

  @override
  String get teamModelSheetNote =>
      'النماذج التي يمكن لـ OpenCode على هذا الهاتف استخدامها فقط. يحتفظ العامل الذي يعمل بالفعل بنموذجه.';

  @override
  String get teamModelNoneLoaded =>
      'لم تُحمّل نماذج هذا الهاتف بعد. أغلق هذا العرض وحاول مجددًا بعد قليل.';

  @override
  String get teamModelFailed =>
      'تعذّر تغيير النموذج. يحتفظ الفريق بنموذجه السابق.';

  @override
  String get teamNowReasonWorkerPreparing =>
      'جارٍ إعداد العامل: يُنشأ مجلده ويبدأ برنامجه.';

  @override
  String get teamNowReasonWorkerRunning =>
      'برنامج العامل قيد التشغيل. لم يبلّغ الفريق عن وصول المهمة إليه بعد.';

  @override
  String get teamNowReasonWorkerTaskDelivered =>
      'وصلت المهمة إلى العامل. يقرأها قبل أن يبدأ.';

  @override
  String teamNowLastStart(String duration) {
    return 'استغرق $duration في المرة السابقة';
  }

  @override
  String get teamUiHostPhraseBusyStartingWorker => 'مشغول بتشغيل عامل';

  @override
  String get teamNowReasonWorkInProgress => 'جارٍ العمل على المهمة.';

  @override
  String get teamNowReasonReviewPending =>
      'لا تزال المراجعة أو الإكمال قيد الانتظار.';

  @override
  String get teamNowReasonAnswerNeeded => 'ينتظر الفريق إجابتك.';

  @override
  String get teamNowReasonWorkerCouldNotStart =>
      'لم يتمكن العامل من مواصلة التشغيل.';

  @override
  String get teamNowReasonProviderLimit =>
      'أبلغت خدمة الذكاء الاصطناعي عن بلوغ حد الاستخدام.';

  @override
  String get teamNowReasonWorkTakingLonger =>
      'يستغرق العمل وقتًا أطول من المتوقع.';

  @override
  String get teamNowReasonConfirmationMissing =>
      'لا يمكننا تأكيد وصول الطلب. تحقّق قبل إرساله مجددًا.';

  @override
  String get teamNowReasonRequestRefused => 'لم يُقبل الطلب.';

  @override
  String get teamNowReasonConnectionUnavailable =>
      'لا يمكن التحقّق من التقدّم أثناء انقطاع الاتصال.';

  @override
  String get teamNowReasonCauseUnknown =>
      'السبب غير معروف. تحقّق مما يفعله الفريق.';

  @override
  String get teamNowWhyPlanning =>
      'يحوّل المخطط مهمتك إلى خطوات. تتابع هذه المحادثة المهمة بمجرد أن يسردها الفريق. إيقاف متابعتها هنا لا يلغيها على حاسوب الفريق.';

  @override
  String get teamNowWhyWaitingForWorker =>
      'يبحث الفريق عن عمل جديد بانتظام ويشغّل عاملًا له عندما يتاح أحدهم.';

  @override
  String get teamNowWhyStartingWorker =>
      'ينشئ العامل الجديد نسخته الخاصة من المشروع ويشغّل برنامجه قبل قراءة المهمة. هذا هو الجزء البطيء على الهاتف، والمرحلة أعلاه هي ما يبلّغ عنه الفريق.';

  @override
  String get teamNowWhyWorking =>
      'يُجري العامل التغييرات في نسخته الخاصة، ثم يسلّمها للمراجعة.';

  @override
  String get teamNowWhyReviewing => 'يفحص مراجع التغييرات قبل دمجها.';

  @override
  String get teamNowWhyUnconfirmed =>
      'أرسل التطبيق المهمة لكنه لم يتلقّ ردًا. قد يؤدي إرسالها مجددًا إلى تشغيلها مرتين، لذا راجع المخطط أولًا.';

  @override
  String get teamNowWhyWorkerCouldNotStart =>
      'توقف العامل أثناء بدء تشغيله. قد توضح محادثته السبب.';

  @override
  String get teamNowWhyProviderLimit =>
      'تحدّد خدمة الذكاء الاصطناعي مقدار الاستخدام خلال فترة معينة. يتابع العمل عندما يُعاد ضبط الحد، أو يمكنك إيقاف المهمة.';

  @override
  String get teamNowWhyWorkTakingLonger =>
      'قد تستغرق المهام الكبيرة بعض الوقت. تبيّن متابعة العامل ما إذا كان لا يزال يتقدّم.';

  @override
  String get teamNowWhyCauseUnknown => 'لا توضح إفادة الفريق سبب انتظاره.';

  @override
  String get teamNowWhyHide => 'إخفاء الشرح';

  @override
  String get teamNowNextPlan => 'التالي: يسرد الفريق الخطوات';

  @override
  String get teamNowNextWorker => 'التالي: يبدأ عامل';

  @override
  String get teamNowNextWork => 'التالي: يبدأ العامل المهمة';

  @override
  String get teamNowNextReview => 'التالي: تُراجع التغييرات';

  @override
  String get teamNowNextFinish => 'التالي: تكتمل المهمة';

  @override
  String get teamNowWatchPlanner => 'متابعة المخطط';

  @override
  String get teamNowDismissRequest => 'إيقاف متابعة هذا الطلب';

  @override
  String teamNowUsuallyWithin(String duration) {
    return 'عادةً خلال $duration';
  }

  @override
  String teamNowWatchAgent(String name) {
    return 'متابعة $name';
  }

  @override
  String get teamNowNotStartingLine => 'لا يشغّل الفريق عاملًا';

  @override
  String get aiSetupTitle => 'إعداد الذكاء الاصطناعي';

  @override
  String get aiSetupEntryDetail => 'النماذج والأدوات والاقتراحات لهذا الخادم';

  @override
  String get aiSetupRefresh => 'قراءة إعدادات هذا الخادم مجددًا';

  @override
  String get aiSetupLoading => 'جارٍ قراءة إعدادات هذا الخادم…';

  @override
  String get aiSetupReviewOnly =>
      'للمراجعة فقط. تُجرى التغييرات على الخادم حاليًا.';

  @override
  String get aiSetupUnsupportedTitle => 'إعداد الذكاء الاصطناعي غير متاح';

  @override
  String get aiSetupUnsupportedBody =>
      'لا يشارك هذا الخادم إعداداته مع التطبيق. اضبط نماذجه وأدواته على الخادم نفسه.';

  @override
  String get aiSetupSignInTitle => 'تسجيل الدخول مطلوب';

  @override
  String aiSetupSignInBody(String server) {
    return 'لم يقبل $server بيانات تسجيل الدخول المحفوظة، لذا لا يمكن قراءة إعداداته.';
  }

  @override
  String get aiSetupErrorTitle => 'تعذّر قراءة الإعدادات';

  @override
  String get aiSetupErrorBody =>
      'لم يرد الخادم كما هو متوقع. حاول مجددًا، أو تحقّق من الخادم في صفحة إعداداته.';

  @override
  String get aiSetupTryAgain => 'إعادة المحاولة';

  @override
  String get aiSetupOfflineTitle => 'أنت غير متصل';

  @override
  String aiSetupOfflineBody(String server) {
    return 'أعد الاتصال بـ $server لقراءة إعداداته.';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return 'غير متصل. هذه إعدادات $server من آخر قراءة؛ تُحدَّث عند إعادة الاتصال.';
  }

  @override
  String get aiSetupEmptyTitle => 'لم يُضبط شيء بعد';

  @override
  String get aiSetupEmptyBody =>
      'يعمل هذا الخادم بإعداداته الافتراضية، دون نموذج مختار أو خوادم أدوات. تُجرى التغييرات على الخادم حاليًا.';

  @override
  String get aiSetupSuggestionsLabel => 'اقتراحات';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return 'تسجيل الدخول إلى $name';
  }

  @override
  String get aiSetupSuggestSignInDetail =>
      'تبقى أدواته معطّلة حتى يسجّل أحد الدخول إليه على الخادم.';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return 'التحقق من إعدادات $name';
  }

  @override
  String get aiSetupSuggestFixDetail =>
      'تعذّر بدء تشغيله. أصلح بند إعداداته على الخادم، ثم أعد تشغيل الخادم.';

  @override
  String get aiSetupSuggestModelTitle => 'اختيار نموذج افتراضي';

  @override
  String get aiSetupSuggestModelDetail =>
      'لم يُحدّد نموذج، لذا تستخدم المحادثات الجديدة اختيار الخادم. اضبط «model» في إعدادات الخادم.';

  @override
  String get aiSetupSuggestToolsTitle => 'إضافة خوادم أدوات';

  @override
  String get aiSetupSuggestToolsDetail =>
      'لم تُضبط خوادم MCP. أضف أحدها في إعدادات الخادم لتزويد الوكيل بالمزيد من الأدوات.';

  @override
  String get aiSetupToolsLabel => 'خوادم الأدوات';

  @override
  String get aiSetupToolsTerm =>
      'توفّر خوادم MCP أدوات إضافية للوكيل. يوضح كل منها ما إذا كان يعمل الآن.';

  @override
  String get aiSetupToolConnected => 'متصل';

  @override
  String get aiSetupToolWaiting => 'بانتظار';

  @override
  String get aiSetupToolOff => 'معطّل';

  @override
  String get aiSetupToolFailed => 'فشل';

  @override
  String get aiSetupToolNeedsSignIn => 'يحتاج إلى تسجيل الدخول';

  @override
  String get aiSetupToolUnknown => 'غير معروف';

  @override
  String get aiSetupEffectiveLabel => 'الإعدادات السارية';

  @override
  String get aiSetupEffectiveTerm =>
      'ما تستخدمه محادثات هذا الخادم بعد دمج ملفات إعداداته.';

  @override
  String get aiSetupModel => 'النموذج';

  @override
  String get aiSetupServerDefault => 'لم يُحدّد: يختار الخادم';

  @override
  String get aiSetupSmallModel => 'النموذج الصغير';

  @override
  String get aiSetupDefaultAgent => 'الوكيل الافتراضي';

  @override
  String get aiSetupProviders => 'مزوّدو الخدمة';

  @override
  String get aiSetupPermissions => 'الأذونات';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من القواعد',
      one: 'قاعدة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => 'كل الإعدادات';

  @override
  String get aiSetupSourcesLabel => 'مصادر الإعدادات';

  @override
  String get aiSetupSourcesTerm =>
      'مرتبة من الأولوية الأدنى إلى الأعلى، كما أبلغ عنها الخادم. لا يدمجها التطبيق.';

  @override
  String get aiSetupNoSources => 'لا توجد ملفات إعدادات';

  @override
  String get aiSetupNoSourcesDetail => 'يعمل هذا الخادم بإعداداته الافتراضية.';

  @override
  String aiSetupSourceUnnamed(String type) {
    return 'مصدر بلا ملف ($type)';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position. يضبط $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position. لا يضبط شيئًا';
  }

  @override
  String get aiSetupAllSources => 'كل المصادر';

  @override
  String get integrationsPageLoadFailed => 'تعذّر تحميل هذه الصفحة';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · جارٍ التحديث';
  }

  @override
  String get kitLastKnownHint =>
      'محفوظة من المرة السابقة. تُفتح عند تحميل القائمة المباشرة.';

  @override
  String get kitTranscriptExcerptHint =>
      'محفوظ من المرة السابقة. تُفتح المحادثة كاملة عند تحميلها.';

  @override
  String get lastKnownUpdatedJustNow => 'حُدّثت الآن';

  @override
  String lastKnownUpdatedAgo(String ago) {
    return 'حُدّثت منذ $ago';
  }

  @override
  String serverRowQueuedWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'طلبات بانتظار الإرسال: $count',
      one: 'طلب واحد بانتظار الإرسال',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'نقل الطلبات المنتظرة وعددها $count إلى $destination',
      one: 'نقل طلب واحد منتظر إلى $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveTitle => 'نقل الطلبات المنتظرة';

  @override
  String queuedMoveSubtitle(String source) {
    return 'من $source';
  }

  @override
  String get queuedMovePromptsLabel => 'الطلبات';

  @override
  String get queuedMoveConversationLabel => 'المحادثة';

  @override
  String get queuedMoveNewConversation => 'محادثة جديدة';

  @override
  String queuedMoveQueuedAt(String time) {
    return 'أُضيف إلى قائمة الانتظار $time';
  }

  @override
  String queuedMoveFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من الملفات',
      one: 'ملف واحد',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveBlockedUncertain(String source) {
    return 'ربما أُرسل بالفعل. تحقّق منه على $source أولًا.';
  }

  @override
  String queuedMoveBlockedFile(String source) {
    return 'يتضمّن ملفًا لا يمكن فتحه إلا على $source';
  }

  @override
  String get queuedMoveBlockedMentions =>
      'سيؤدي إخفاء كلمة مرور فيه إلى تعطيل الإشارات إلى الوكلاء';

  @override
  String queuedMoveHidesSecrets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تبقى كلمات المرور والمفاتيح في $count من الطلبات مخفية',
      one: 'تبقى كلمات المرور والمفاتيح في طلب واحد مخفية',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUsesCurrentModel(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يستخدم $count من الطلبات النموذج المختار على $destination',
      one: 'يستخدم طلب واحد النموذج المختار على $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveAction(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'نقل $count من الطلبات إلى $destination',
      one: 'نقل طلب واحد إلى $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveChooseOne => 'اختر طلبًا واحدًا على الأقل';

  @override
  String queuedMoveNoneLeft(String source) {
    return 'لم يعد هناك شيء ينتظر $source';
  }

  @override
  String queuedMoveFailedDisconnected(String destination) {
    return 'انقطع اتصال $destination، فلم يُنقل شيء. اتصل به وحاول مجددًا.';
  }

  @override
  String queuedMoveFailedConversationGone(String destination) {
    return 'لم تعد تلك المحادثة موجودة على $destination، فلم يُنقل شيء. اختر محادثة أخرى.';
  }

  @override
  String queuedMoveFailedNothing(String source) {
    return 'لم تعد هذه الطلبات تنتظر $source، فلم يُنقل شيء.';
  }

  @override
  String queuedMoveFailedNewConversation(String destination) {
    return 'تعذّر بدء محادثة جديدة على $destination، فلم يُنقل شيء. حاول مجددًا أو اختر محادثة موجودة.';
  }

  @override
  String queuedMoveFailedNotSaved(String source) {
    return 'تعذّر حفظ النقل، فلم يُنقل شيء. لا تزال الطلبات تنتظر $source.';
  }

  @override
  String queuedMoveDone(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'نُقل $count من الطلبات إلى $destination',
      one: 'نُقل طلب واحد إلى $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveDonePartial(int moved, int total, String destination) {
    return 'نُقل $moved من أصل $total من الطلبات إلى $destination. لم تعد الطلبات الباقية في الانتظار.';
  }

  @override
  String queuedMoveUndoNone(String destination) {
    return 'بدأ إرسال الطلبات بالفعل على $destination، لذا تبقى هناك.';
  }

  @override
  String queuedMoveUndoPartial(int count, String destination, String source) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'بدأ إرسال $count من الطلبات بالفعل على $destination وتبقى هناك. تنتظر الطلبات الباقية $source مجددًا.',
      one:
          'بدأ إرسال طلب واحد بالفعل على $destination ويبقى هناك. تنتظر الطلبات الباقية $source مجددًا.',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUndoFailed(String destination) {
    return 'تعذّر إعادة الطلبات. تبقى على $destination.';
  }

  @override
  String get sessionAddressInclude => 'تضمين عنوان هذا الخادم';

  @override
  String get sessionAddressDisclosure =>
      'يعرض الرابط حينها هذا العنوان ومعرّف المحادثة، دون كلمة مرور: لا يزال الهاتف الآخر يحتاج إلى صلاحية وصول خاصة به. قد تحتفظ به لقطات الشاشة والرسائل والحافظة.';

  @override
  String get sessionAddressIntro =>
      'امسح الرمز باستخدام OpenCode Mobile على الهاتف الآخر. يتضمّن الرمز عنوان هذا الخادم ومعرّف المحادثة.';

  @override
  String get sessionAddressUnsupportedHost =>
      'لا يمكن تضمين عنوان في الرابط إلا إذا كان عنوان HTTPS خاصًا ينتهي بـ .ts.net.';

  @override
  String get sessionAddressOpenTitle => 'فتح محادثة مشتركة';

  @override
  String get sessionAddressConsentSaved =>
      'هل تريد الفتح على هذا الخادم المحفوظ؟';

  @override
  String get sessionAddressConsentNew => 'هل تريد إضافة هذا الخادم؟';

  @override
  String get sessionAddressNotSaved => 'غير محفوظ على هذا الهاتف';

  @override
  String get sessionAddressConsentNote =>
      'لا يمنح الرابط صلاحية وصول. يسأل التحقق الخادم عن نسخته فقط؛ لا يحدث تسجيل دخول ولا تُرسل كلمة مرور.';

  @override
  String get sessionAddressCheck => 'فحص الخادم';

  @override
  String sessionAddressChecking(String host) {
    return 'جارٍ فحص $host…';
  }

  @override
  String get sessionAddressAddBody =>
      'هذا الخادم غير محفوظ على هذا الهاتف. أضفه ببيانات تسجيل دخولك؛ لا يتضمّن الرابط تلك البيانات.';

  @override
  String get sessionAddressAddServer => 'إضافة خادم';

  @override
  String get sessionAddressChooseBody =>
      'يستخدم أكثر من خادم محفوظ هذا العنوان. اختر الخادم الذي تريد فتح المحادثة عليه.';

  @override
  String sessionAddressVerifyBody(String name) {
    return 'أكّد أن $name هو الخادم الذي جاء منه هذا الرابط. يتذكر الهاتف ذلك لـ $name؛ ولا يسجّل الدخول أو يشارك كلمة مرور.';
  }

  @override
  String get sessionAddressVerify => 'التحقق من الخادم';

  @override
  String sessionAddressReadyBody(String name) {
    return 'يتطابق $name مع هذا الرابط.';
  }

  @override
  String sessionAddressSignInBody(String name) {
    return 'سجّل الدخول إلى $name بحسابك أولًا، ثم افتح المحادثة.';
  }

  @override
  String get sessionAddressSignIn => 'تسجيل الدخول';

  @override
  String get sessionAddressOpen => 'فتح المحادثة';

  @override
  String get sessionAddressOpening => 'جارٍ فتح المحادثة…';

  @override
  String get sessionAddressReason => 'السبب';

  @override
  String get sessionAddressFailUnavailable =>
      'روابط المحادثات التي تتضمّن عنوان الخادم غير متاحة بعد.';

  @override
  String get sessionAddressFailInvalidLink =>
      'رابط هذه المحادثة غير صالح. امسحه أو انسخه مجددًا.';

  @override
  String get sessionAddressFailTooLarge =>
      'هذا الرابط طويل جدًا. اطلب رابطًا جديدًا من المرسل.';

  @override
  String get sessionAddressFailCredentials =>
      'يتضمّن هذا الرابط معلومات تسجيل دخول خاصة ولا يمكن استخدامه.';

  @override
  String get sessionAddressFailConsentRequired =>
      'اختر أولًا ما إذا كنت تريد تضمين عنوان هذا الخادم.';

  @override
  String get sessionAddressFailPrivateRouteRequired =>
      'لا يمكن الوصول إلى هذا الخادم عبر الاتصال الخاص المطلوب. تحقّق من اتصالك.';

  @override
  String get sessionAddressFailUnreachable =>
      'تعذّر الوصول إلى الخادم. تحقّق من اتصالك وحاول مجددًا.';

  @override
  String get sessionAddressFailTimedOut =>
      'لم يرد الخادم في الوقت المحدد. حاول مجددًا.';

  @override
  String get sessionAddressFailTlsRejected =>
      'تعذّر التحقق من اتصال الخادم الآمن، لذا لم يُفتح الرابط.';

  @override
  String get sessionAddressFailRedirectsRejected =>
      'حاول هذا الخادم إرسال الطلب إلى مكان آخر. لم يُفتح الرابط.';

  @override
  String get sessionAddressFailAccessDenied =>
      'رُفض وصولك إلى هذا الخادم أو المحادثة.';

  @override
  String get sessionAddressFailInvalidDescriptor =>
      'لم يقدّم هذا الخادم المعلومات اللازمة لفتح هذا الرابط.';

  @override
  String get sessionAddressFailInstanceMismatch =>
      'لا يشير هذا الرابط والخادم المحفوظ إلى النسخة المثبّتة نفسها.';

  @override
  String get sessionAddressFailBindingRequired =>
      'تحقّق من هذا الخادم المحفوظ قبل فتح المحادثة.';

  @override
  String get sessionAddressFailAmbiguousProfile =>
      'اختر الخادم المحفوظ الذي تريد استخدامه.';

  @override
  String get sessionAddressFailProfileMissing =>
      'لم يعد هذا الخادم المحفوظ متاحًا.';

  @override
  String get sessionAddressFailStorage =>
      'تعذّر حفظ التحقق من الخادم أو قراءته. حاول مجددًا بعد إعادة تشغيل التطبيق.';

  @override
  String get sessionAddressFailSignInRequired =>
      'سجّل الدخول إلى هذا الخادم بحسابك قبل المتابعة.';

  @override
  String get sessionAddressFailUnsafeLookup =>
      'لم يُتحقّق من هذا الخادم لاستخدام روابط المحادثات الخاصة.';

  @override
  String get sessionAddressFailSessionMissing =>
      'هذه المحادثة غير متاحة على هذا الخادم.';

  @override
  String get sessionAddressFailCancelled => 'أُلغي فتح هذا الرابط.';

  @override
  String get removeFromPhoneDeleteAllChoice => 'حذف كل شيء…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return 'حذف كل شيء، وتحرير نحو $size…';
  }

  @override
  String get phoneServerCardErrorDetail => 'خطأ';

  @override
  String get sessionMenuGoTo => 'الانتقال إلى';

  @override
  String get sessionMenuDo => 'إجراءات';

  @override
  String get sessionMenuFind => 'بحث';

  @override
  String get sessionMenuSubagents => 'الوكلاء الفرعيون';

  @override
  String get sessionMenuDetails => 'التفاصيل';

  @override
  String get sessionMenuShareHint => 'يمكن لأي شخص لديه الرابط قراءتها';

  @override
  String get sessionMenuStopSharingHint => 'يتوقف الرابط العام عن العمل';

  @override
  String get sessionMenuCompactHint => 'يلخّصها لإفساح المجال للوكيل مجددًا';

  @override
  String get sessionMenuForkHint => 'يفتح نسخة يمكنك متابعتها في اتجاه آخر';

  @override
  String get sessionMenuContinueComputerHint => 'يعرض الأمر الذي يستأنفها هناك';

  @override
  String get sessionMenuContinuePhoneHint =>
      'يعرض رمزًا يفتحه التطبيق على ذلك الهاتف';

  @override
  String get sessionMenuNeedsPrompt => 'متاح بعد الطلب الأول';

  @override
  String commandSheetServerGroup(String server) {
    return 'أوامر من $server';
  }

  @override
  String commandSheetAgentMissingTitle(String agent) {
    return 'أوامر $agent غير متاحة';
  }

  @override
  String commandSheetAgentMissingWhy(String agent) {
    return 'لا يشارك $agent أوامره الخاصة مع التطبيق بعد، لذا لا يستطيع التطبيق عرضها أو تشغيلها أو تشغيل أوامر الطرفية التي تبدأ بـ !. لا تزال إجراءات التطبيق نفسه تعمل.';
  }

  @override
  String commandSheetAgentCommandNotSent(String command, String agent) {
    return 'لم يُرسل $command: لا يشارك $agent أوامره مع التطبيق بعد. أزل / لإرساله كرسالة.';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return 'لم يُرسل $command: لا يمكن تشغيل أوامر الطرفية على $agent من التطبيق. أزل ! لإرساله كرسالة.';
  }

  @override
  String get commandSheetShellDescription =>
      'أو ابدأ رسالة بـ ! لتشغيلها من محرّر الرسائل';

  @override
  String get commandSheetRetryDescription => 'يرسل طلبك الأخير مجددًا';

  @override
  String get commandSheetNoteDescription =>
      'ملاحظة يراعيها الوكيل في هذه المحادثة';

  @override
  String get commandSheetApprovalsDescription =>
      'ما يمكن لهذه المحادثة فعله دون سؤال';

  @override
  String get commandSheetReloadDescription =>
      'يقرأ هذه المحادثة من الخادم مجددًا';

  @override
  String get commandSheetLibrarySubtitle =>
      'اختر أمرًا ثم المحادثة التي يعمل فيها';

  @override
  String get commandSheetAgentFallback => 'هذا الوكيل';

  @override
  String get commandSheetPlanDescription => 'يفتح أحدث خطة للوكيل في المحادثة';

  @override
  String get chatUiSessionMenu => 'قائمة المحادثة';

  @override
  String get commandsScreenLoadFailed => 'تعذّر تحميل الأوامر';

  @override
  String get commandSheetSubtitleAppOnly =>
      'تشغيل أحد إجراءات التطبيق في هذه المحادثة';

  @override
  String get voiceModeMicAsk =>
      'تحتاج الكتابة الصوتية إلى الميكروفون. اضغط «السماح بالميكروفون»، ثم اختر «سماح».';

  @override
  String get voiceModeMicAllow => 'السماح بالميكروفون';

  @override
  String get voiceModeMicBlocked =>
      'يحظر Android الميكروفون لهذا التطبيق. فعّله في إعدادات Android، ثم عد إلى هنا.';

  @override
  String get voiceModeNothingHeard =>
      'لم يُسمع شيء. اضغط الميكروفون وحاول مجددًا.';

  @override
  String get teamDispatchCreating => 'جارٍ إنشاء مهمتك…';

  @override
  String get teamDispatchSending => 'أُنشئت المهمة · جارٍ إرسالها إلى الفريق…';

  @override
  String get teamDispatchAwaitingWorker =>
      'أُرسلت المهمة إلى الفريق · بانتظار عامل';

  @override
  String get teamDispatchWorkerStarted => 'بدأ عامل مهمتك';

  @override
  String get teamDispatchCreateRefused =>
      'لم تُنشأ المهمة. عدّلها وأرسلها مجددًا.';

  @override
  String get teamDispatchAssignRefused =>
      'أُنشئت المهمة، لكن تعذّر إرسالها إلى الفريق';

  @override
  String get teamDispatchAssignRefusedHint =>
      'تبقى المهمة على اللوحة دون إسنادها إلى أحد.';

  @override
  String get teamDispatchCreateUnconfirmed => 'تعذّر تأكيد إنشاء المهمة';

  @override
  String get teamDispatchDispatchUnconfirmed =>
      'أُنشئت المهمة · تعذّر تأكيد وصولها إلى الفريق';

  @override
  String get teamDispatchCheckBoard =>
      'تحقّق من اللوحة قبل إرسالها مجددًا. يبقى نصك محفوظًا.';

  @override
  String get teamDispatchUnknown =>
      'أُرسلت المهمة · يتعذّر الوصول إلى الفريق، لذا لا يُعرف ما إذا كان عامل قد بدأ';

  @override
  String get teamDispatchCheckAgain => 'التحقّق من الفريق مجددًا';

  @override
  String get teamDispatchTaskId => 'معرّف المهمة';

  @override
  String get teamDispatchHostWords => 'ردّ الفريق';

  @override
  String get teamUiHostGuideOpen => 'فتح الدليل الكامل';

  @override
  String get mcpAddBrowseTitle => 'تصفّح الدليل';

  @override
  String get mcpAddBrowseDetail => 'خوادم من سجل MCP العام، تُفعّل بمفتاح';

  @override
  String get mcpAddBrowseNone =>
      'لا يتوفّر دليل لهذا الخادم: لا يقبل إضافة خوادم MCP جديدة من التطبيق.';

  @override
  String get mcpAddManualTitle => 'الإدخال يدويًا';

  @override
  String get mcpAddManualDetail => 'اكتب عنوانه أو الأمر الذي يشغّله';

  @override
  String get mcpCatalogTitle => 'دليل MCP';

  @override
  String get mcpCatalogConsentTitle => 'هل تريد تحميل سجل MCP؟';

  @override
  String get mcpCatalogConsentBody =>
      'يطلب التطبيق قائمة خوادم MCP من registry.modelcontextprotocol.io. يرسل فقط ما تبحث عنه، دون أي معلومات عنك أو عن خوادمك.';

  @override
  String get mcpCatalogConsentLoad => 'تحميل القائمة';

  @override
  String get mcpCatalogForget => 'إيقاف استخدام السجل';

  @override
  String get mcpCatalogForgetFailed =>
      'تعذّر مسح قائمة السجل المحفوظة. حاول مجددًا.';

  @override
  String get mcpCatalogSearch => 'البحث في السجل';

  @override
  String get mcpCatalogInventoryFailed =>
      'تعذّرت قراءة خوادم MCP الخاصة بهذا الخادم';

  @override
  String get mcpCatalogInventoryFailedBody =>
      'تحتاج مفاتيح التفعيل إلى معرفة ما هو مفعّل بالفعل. تحقّق من الاتصال، ثم حاول مجددًا.';

  @override
  String get mcpCatalogFailed => 'تعذّر تحميل سجل MCP العام';

  @override
  String get mcpCatalogSearchFailed => 'تعذّر البحث في سجل MCP العام';

  @override
  String get mcpCatalogFailedBody =>
      'تحقّق من اتصال الهاتف بالإنترنت، ثم حاول مجددًا. لا يزال بإمكانك إدخال خادم يدويًا.';

  @override
  String get mcpCatalogEmpty => 'لم يعرض السجل أي خوادم';

  @override
  String mcpCatalogNoMatch(String query) {
    return 'لا يوجد في السجل ما يطابق «$query»';
  }

  @override
  String get mcpCatalogEmptyBody => 'جرّب كلمات أخرى أو أدخل الخادم يدويًا.';

  @override
  String get mcpCatalogStale =>
      'تعذّر تحديث القائمة من السجل. هذه هي العناصر التي حُمّلت سابقًا.';

  @override
  String get mcpCatalogPriceNote =>
      'لا يعرض السجل أسعارًا. قد يفرض مالك الخادم المستضاف رسومًا أو يطلب حسابًا.';

  @override
  String get mcpCatalogAdding => 'جارٍ الإضافة…';

  @override
  String get mcpCatalogRemoving => 'جارٍ الإزالة…';

  @override
  String get mcpCatalogCannotRemove =>
      'مفعّل. يحتفظ هذا الخادم به في إعداداته، ولا يستطيع التطبيق إزالته.';

  @override
  String get mcpCatalogNeedsDocker =>
      'يعمل في Docker. لإضافته رغم ذلك، استخدم «الإدخال يدويًا».';

  @override
  String get mcpCatalogNoEndpoint =>
      'لا يعرض شيئًا يستطيع التطبيق تشغيله. لإضافته رغم ذلك، استخدم «الإدخال يدويًا».';

  @override
  String mcpCatalogHostedBy(String host) {
    return 'يستضيفه $host';
  }

  @override
  String get mcpCatalogNeedsNode => 'يحتاج Node على الخادم';

  @override
  String get mcpCatalogNeedsNodePhone => 'يحتاج Node على هذا الهاتف';

  @override
  String get mcpCatalogNeedsPython => 'يحتاج Python مع uv على الخادم';

  @override
  String get mcpCatalogNeedsKey => 'يحتاج مفتاح API';

  @override
  String get mcpCatalogNeedsSettings => 'يحتاج إعدادات إضافية';

  @override
  String mcpCatalogNodeTitle(String title) {
    return 'يعمل $title باستخدام Node';
  }

  @override
  String get mcpCatalogNodeAdd => 'إضافة Node إلى هذا الهاتف';

  @override
  String get mcpCatalogNodeAddDetail =>
      'يفتح «هذا الهاتف». اختر «إضافة أدوات» ثم Node، وفعّل هذا مجددًا بعد إضافته.';

  @override
  String get mcpCatalogNodeHave => 'Node موجود على هذا الهاتف بالفعل';

  @override
  String get mcpCatalogNodeHaveDetail => 'راجع التفاصيل وأضفه';

  @override
  String mcpSetupFromCatalog(String listing, String server) {
    return 'مُعبّأ من «$listing» في سجل MCP العام. راجعه قبل إضافته: سيشغّل $server ما هو هنا أو يتصل به.';
  }

  @override
  String get mcpSetupThisServer => 'هذا الخادم';

  @override
  String mcpSetupValueRequired(String name) {
    return 'أدخل قيمة لـ $name';
  }

  @override
  String get mcpSetupNameFromCatalog => 'يتطلب عنصر السجل هذا الاسم';

  @override
  String get mcpVariableName => 'اسم المتغيّر';

  @override
  String get mcpVariableValue => 'قيمة المتغيّر';

  @override
  String get mcpAddVariable => 'إضافة متغيّر آخر';

  @override
  String get mcpRemoveVariable => 'إزالة المتغيّر';

  @override
  String get mcpSetupTimeoutSeconds => 'المهلة بالثواني';

  @override
  String get isolatedTaskPromptLabel => 'ما المهمة التي تريد العمل عليها؟';

  @override
  String get isolatedTaskPromptHelper =>
      'يُرسل بعد تجهيز النسخة. اتركه فارغًا لكتابته في المحادثة.';

  @override
  String get isolatedTaskOptions => 'الخيارات';

  @override
  String get isolatedTaskPreparingHint =>
      'إذا توقفت عن الانتظار، تبقى النسخة. ستجدها ضمن المشروع › نسخ العمل.';

  @override
  String get isolatedTaskFailedBody =>
      'أُنشئت النسخة، لكن إعدادها لم يكتمل. ابدأ فيها على أي حال، أو أزلها.';

  @override
  String isolatedTaskSending(String name) {
    return 'جارٍ إرسال مهمتك إلى $name…';
  }

  @override
  String get isolatedTaskSendFailed => 'تعذّر إرسال مهمتك';

  @override
  String get isolatedTaskSendFailedBody =>
      'تنتظر في مربع الرسالة في المحادثة، وجاهزة للإرسال.';

  @override
  String get isolatedTaskSendFailedLost =>
      'انسخ مهمتك أدناه وأرسلها في المحادثة.';

  @override
  String get isolatedTaskOpenConversation => 'فتح المحادثة';

  @override
  String get isolatedTaskStartAnyway => 'البدء على أي حال';

  @override
  String get isolatedTaskRemove => 'إزالة النسخة';

  @override
  String isolatedTaskRemoveTitle(String name) {
    return 'هل تريد إزالة $name؟';
  }

  @override
  String get isolatedTaskRemoveBody =>
      'يُحذف مجلدها وفرعها. لن يتغيّر مشروعك نفسه.';

  @override
  String isolatedTaskRemoved(String name) {
    return 'أُزيلت $name. يمكنك البدء مجددًا.';
  }

  @override
  String get isolatedTaskSetupOutput => 'ما أبلغ عنه الإعداد';

  @override
  String get isolatedTaskCopyFolder => 'مجلد النسخة';

  @override
  String get isolatedTaskBranchLabel => 'الفرع';

  @override
  String get isolatedTaskStageSend => 'جارٍ فتح المحادثة وإرسال مهمتك';

  @override
  String get teamStartRunBlockedTitle => 'لا يستطيع الفريق قبول مهام';

  @override
  String get teamStartRunPlannerOff => 'المخطط متوقف';

  @override
  String get teamStartRunPlannerOffWakeBody =>
      'يحوّل المخطط كل مهمة إلى خطوات للفريق. نشّطه لإعطاء الفريق مهمتك.';

  @override
  String get teamStartRunPlannerOffHostBody =>
      'يحوّل المخطط كل مهمة إلى خطوات للفريق، ولا يستطيع هذا التطبيق تشغيله. شغّله في مكان تشغيل الفريق، ثم حاول مجددًا.';

  @override
  String get teamStartRunNoPlanner => 'ليس لهذا الفريق مخطط';

  @override
  String get teamStartRunNoPlannerBody =>
      'يحوّل المخطط كل مهمة إلى خطوات للفريق. أضف واحدًا في مكان تشغيل الفريق، ثم حاول مجددًا.';

  @override
  String get teamStartRunNoProject => 'ليس لهذا الفريق مشروع بعد';

  @override
  String get teamStartRunNoProjectBody =>
      'تُرسل المهام مباشرة إلى عامل المشروع. أضف مشروعًا إلى الفريق، ثم حاول مجددًا.';

  @override
  String get teamStartRunWake => 'تنشيط المخطط';

  @override
  String get teamStartRunWakeAsked =>
      'جارٍ تنشيط المخطط. يُفتح نموذج المهمة بمجرد تنشيطه.';

  @override
  String get teamStartRunStillOff => 'لا يزال المخطط متوقفًا.';

  @override
  String get teamStartRunStillNoProject => 'لا يزال الفريق بلا مشروع.';

  @override
  String get teamStartRunWakeRefused => 'تعذّر تنشيط المخطط';

  @override
  String get teamStartRunWakeRefusedNext =>
      'حاول مجددًا أو شغّله في مكان تشغيل الفريق.';

  @override
  String get addServerTailscaleNext => 'إدخال العنوان';

  @override
  String get phoneSetupTermuxGetCurrent => 'تنزيل أحدث إصدار من Termux';

  @override
  String get phoneSetupUnsupportedTitle => 'الاتصال بخادم';

  @override
  String get phoneSetupUnsupportedBody =>
      'لا يمكن الإعداد على الجهاز نفسه إلا على هواتف Android. شغّل هذا الأمر على حاسوبك، ثم أضف الخادم هنا بالرمز الذي يعرضه.';

  @override
  String get termuxStorageStageTotal => 'تثبيت Termux كاملًا';

  @override
  String get setupProgressViewFailedStep =>
      'لم تكتمل هذه الخطوة. ستجد سبب المشكلة في التفاصيل.';

  @override
  String setupProgressViewFailedAt(String name) {
    return 'توقف عند $name. ستجد سبب المشكلة في التفاصيل.';
  }

  @override
  String workRunawayHelper(String helper, String duration) {
    return 'ظلت عملية $helper متبقية مشغولة منذ $duration دون عمل';
  }

  @override
  String workRunawayHelperInProject(
    String helper,
    String project,
    String duration,
  ) {
    return 'ظلت عملية $helper متبقية في $project مشغولة منذ $duration دون عمل';
  }

  @override
  String get workRunawaySeeRunning => 'عرض العمليات الجارية';

  @override
  String thisPhoneUpToDate(String version) {
    return 'محدّث · $version';
  }

  @override
  String thisPhoneUpdateTitle(String runtime) {
    return 'هل تريد تحديث $runtime؟';
  }

  @override
  String thisPhoneUpdateBody(String version) {
    return 'يثبّت الإصدار $version ويعيد تشغيل الخادم على هذا الهاتف ثم يتصل مجددًا.';
  }

  @override
  String get thisPhoneUpdateKept =>
      'تبقى محادثاتك محفوظة. يغيب الخادم لنحو دقيقة أثناء إعادة تشغيله.';

  @override
  String get thisPhoneUpdateBusy =>
      'لا يزال ردّ قيد الكتابة. أوقفه أو انتظر حتى يكتمل، ثم حدّث.';

  @override
  String get thisPhoneStartAgain => 'تشغيل الخادم مجددًا';

  @override
  String thisPhoneInstallFailed(String runtime) {
    return 'لم يكتمل تثبيت $runtime. ثبّته مجددًا؛ تبقى محادثاتك محفوظة.';
  }

  @override
  String get thisPhoneInstallAgain => 'إعادة التثبيت';

  @override
  String thisPhoneStopFailed(String runtime) {
    return 'لم يتوقف $runtime. حاول إيقافه مجددًا.';
  }

  @override
  String thisPhoneCheckFailed(String runtime) {
    return 'تعذّر على هذا الهاتف التحقّق من $runtime. حاول مجددًا بعد قليل.';
  }

  @override
  String thisPhoneSwitchStopped(String runtime) {
    return 'لم يبدأ $runtime بعد التبديل. تبقى محادثاتك محفوظة.';
  }

  @override
  String get thisPhoneHistoryMoving => 'جارٍ نقل محادثاتك السابقة…';

  @override
  String thisPhoneHistoryMovingCount(int done, int total) {
    return 'جارٍ نقل محادثاتك السابقة ($done من $total)…';
  }

  @override
  String get thisPhoneHistoryFailed =>
      'تعذّر نقل محادثاتك السابقة. لا تزال بياناتها الأصلية على هذا الهاتف دون تغيير.';

  @override
  String get addServerCheckFailedPlain =>
      'تعذّر التحقّق من الخادم. تحقّق من العنوان واتصال هذا الهاتف، ثم حاول مجددًا.';

  @override
  String serverRowDetailsTitle(String name) {
    return 'تفاصيل $name';
  }

  @override
  String get teamUiHostGuideEnterAddress => 'إدخال العنوان';

  @override
  String get commandAuthStartFailed => 'لم يبدأ تسجيل الدخول.';

  @override
  String get commandAuthCheckFailed =>
      'تعذّر التحقّق من تسجيل الدخول. حاول مجددًا.';

  @override
  String get commandAuthTryAgain => 'حاول مجددًا';

  @override
  String get draftLeaveMessageNoText =>
      'حاول الحفظ مجددًا. إذا غادرت دون الحفظ، فقد تضيع آخر تغييراتك على هذه المسودة.';

  @override
  String get draftLeaveCopyAction => 'نسخ المسودة والمغادرة';

  @override
  String get draftLeaveRetry => 'محاولة الحفظ مجددًا';

  @override
  String get draftLeaveStillFailing => 'لم تُحفظ بعد. انسخ نصك قبل المغادرة.';

  @override
  String get queuedRetry => 'إعادة المحاولة';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إعادة محاولة إرسال الطلبات الـ $count كلها',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return 'استخدام $model وإعادة الإرسال';
  }

  @override
  String get chatUiChooseAnotherModel => 'اختيار نموذج آخر';

  @override
  String get chatUiSendPromptAgain => 'إرسال مجددًا';

  @override
  String get chatUiPromptNotAnswered => 'لم يُجب عنه';

  @override
  String get chatWatchEndedTitle => 'انتهت هذه المحادثة';

  @override
  String get chatWatchEndedBody => 'انتهت قبل أن يكتب العامل شيئًا هنا.';

  @override
  String get chatWatchBackToTask => 'العودة إلى المهمة';

  @override
  String get chatWatchBackToWorker => 'العودة إلى العامل';

  @override
  String get migrationTitle => 'النقل من Termux';

  @override
  String get migrationChecking => 'جارٍ التحقّق من Termux ومساحة تخزين الهاتف…';

  @override
  String get migrationReviewIntro =>
      'تُنسخ مشاريعك إلى OpenCode داخل هذا التطبيق. لا يتغيّر أو يُحذف شيء في Termux.';

  @override
  String get migrationGroupMoves => 'تُنسخ وتصبح جاهزة للاستخدام';

  @override
  String get migrationGroupExports => 'تُحفظ بشكل خاص دون تفعيلها';

  @override
  String get migrationGroupNotMoved => 'لا تُنقل';

  @override
  String get migrationItemProjects => 'المشاريع';

  @override
  String get migrationItemConfig => 'إعدادات MCP والوكلاء';

  @override
  String get migrationItemSessions => 'سجل المحادثات (نسخة احتياطية)';

  @override
  String get migrationItemGitConfig => 'إعدادات Git';

  @override
  String get migrationItemShellFiles => 'إعدادات مفسّر الأوامر';

  @override
  String get migrationItemAiTeam => 'AI Team';

  @override
  String get migrationItemProjectsWhat =>
      'إلى مجلد جديد في «على هذا الهاتف». لا يُستبدل شيء هناك.';

  @override
  String get migrationItemConfigWhat =>
      'للمراجعة قبل الاستخدام: قد لا تعمل الأوامر والمسارات إلا في Termux.';

  @override
  String get migrationItemSessionsWhat =>
      'قد يحتوي على بيانات تسجيل دخولك، ولا يفتحه التطبيق. يحتفظ Termux بسجلك القابل للاستخدام.';

  @override
  String get migrationItemGitConfigWhat =>
      'اسمك وبريدك الإلكتروني وخياراتك في Git، للمراجعة.';

  @override
  String get migrationItemShellFilesWhat =>
      'يحتفظ بـ .bashrc و.zshrc وملفات بدء مفسّر الأوامر الأخرى؛ لا تُشغّل أبدًا.';

  @override
  String get migrationItemAiTeamWhat =>
      'الحالة المحفوظة للفريق. يعيد الإعداد تثبيت أدواته.';

  @override
  String migrationItemSize(String size, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عدد الملفات: $count',
      one: 'ملف واحد',
    );
    return '$size · $_temp0';
  }

  @override
  String get migrationSizeUnknown => 'الحجم غير معروف';

  @override
  String get migrationExportsNote =>
      'تبقى النسخ الخاصة في «على هذا الهاتف». لا يُشغّل أو يُفعّل شيء فيها تلقائيًا.';

  @override
  String get migrationNotMovedSignIn =>
      'تسجيل الدخول إلى مزوّدي الذكاء الاصطناعي: سجّل الدخول مجددًا بعد النقل';

  @override
  String migrationNotMovedSignInNamed(String names) {
    return 'تسجيل الدخول إلى $names: سجّل الدخول مجددًا بعد النقل';
  }

  @override
  String get migrationNotMovedKeys => 'مفاتيح SSH وكلمات مرور Git المحفوظة';

  @override
  String get migrationNotMovedTools =>
      'الأدوات المثبّتة وذاكرتها المؤقتة: يعيد الإعداد تثبيتها';

  @override
  String get migrationTermuxKept =>
      'يبقى Termux كما هو ويواصل خادمه العمل حتى تزيله';

  @override
  String get migrationKeepOpen =>
      'أبقِ التطبيق مفتوحًا أثناء النسخ. يوقفه Android عند مغادرة التطبيق، ويتابع من هنا عند عودتك.';

  @override
  String get migrationStart => 'النسخ إلى «على هذا الهاتف»';

  @override
  String get migrationChooseOne => 'اختر عنصرًا واحدًا على الأقل لنسخه.';

  @override
  String get migrationPacking => 'جارٍ تجهيز ملفاتك في Termux…';

  @override
  String get migrationCopying => 'جارٍ نسخ الملفات إلى هذا التطبيق…';

  @override
  String get migrationUnpacking => 'جارٍ استيراد ملفاتك…';

  @override
  String get migrationVerifying => 'جارٍ التحقّق من الملفات المنسوخة…';

  @override
  String get migrationSwitching => 'جارٍ الاتصال بـ «على هذا الهاتف»…';

  @override
  String get migrationStepConnect => 'الاتصال بـ «على هذا الهاتف»';

  @override
  String get migrationStop => 'إيقاف النسخ';

  @override
  String get migrationStopTitle => 'هل تريد إيقاف النسخ؟';

  @override
  String get migrationStopBody => 'يمكنك الاستئناف لاحقًا من «هذا الهاتف».';

  @override
  String get migrationStopKept => 'يبقى ما نُسخ حتى الآن محفوظًا';

  @override
  String get migrationKeepGoing => 'متابعة النسخ';

  @override
  String get migrationCancelled => 'توقف النسخ. يمكنك الاستئناف لاحقًا.';

  @override
  String get migrationCancelledBody =>
      'يبقى ما نُسخ محفوظًا، ولا يتغيّر Termux.';

  @override
  String get migrationStoppedLeaving =>
      'توقف لأن التطبيق غادر الشاشة: لا يسمح Android بتشغيله في الخلفية. يبقى ما نُسخ محفوظًا.';

  @override
  String get migrationResume => 'استئناف النسخ';

  @override
  String get migrationNeedsSpace => 'تحتاج إلى مساحة فارغة إضافية قبل النسخ.';

  @override
  String migrationNeedsSpaceBody(String needed, String free) {
    return 'يحتاج نحو $needed، والمتاح $free. وفّر مساحة على هذا الهاتف أو انسخ عناصر أقل.';
  }

  @override
  String migrationNeedsSpaceBodyUnknown(String needed) {
    return 'يحتاج نحو $needed، وتعذّرت قراءة المساحة المتاحة. وفّر مساحة على هذا الهاتف أو انسخ عناصر أقل.';
  }

  @override
  String get migrationChooseFewer => 'اختيار عناصر أقل';

  @override
  String get migrationNeedsBuiltin => 'أعدّ «على هذا الهاتف» أولًا.';

  @override
  String migrationNeedsBuiltinBody(String runtime) {
    return 'تُنقل مشاريعك إلى OpenCode داخل هذا التطبيق، لذا يحتاج إلى إعداد. يثبّت الإعداد Linux و$runtime، ثم تُستأنف العملية هنا.';
  }

  @override
  String get migrationSetUpBuiltin => 'إعداد «على هذا الهاتف»';

  @override
  String get migrationSetupFailed =>
      'تعذّر بدء الإعداد. حاول مجددًا أو أعدّه من «هذا الهاتف».';

  @override
  String get migrationTermuxNotAnswering => 'Termux لا يردّ';

  @override
  String get migrationTermuxUnavailable => 'افتح Termux، ثم حاول مجددًا.';

  @override
  String get migrationOpenTermux => 'فتح Termux';

  @override
  String get migrationFailedTitle => 'توقف النقل';

  @override
  String get migrationSourceChanged =>
      'تغيّرت الملفات أثناء النسخ. حاول مجددًا عندما يكون Termux خاملًا.';

  @override
  String get migrationSourceBusy =>
      'ينهي Termux الخطوة السابقة. حاول مجددًا بعد قليل.';

  @override
  String get migrationUnsupportedFiles =>
      'يحتوي هذا العنصر على ملفات لا يمكن نسخها بأمان.';

  @override
  String get migrationUnsupportedFilesFix =>
      'لا يمكن نسخ الروابط والمقابس وأشجار العمل في Git. أزلها في Termux وحاول مجددًا، أو انسخ ذلك المشروع يدويًا.';

  @override
  String get migrationLinkedFileUnreadable =>
      'تعذّرت قراءة جزء من سجل Git لهذا المشروع في Termux، لذا لا يمكن نقله بأمان.';

  @override
  String get migrationLinkedFileUnreadableFix =>
      'افتح Termux ونفّذ git fsck داخل ذلك المشروع لترى ما ينقص، ثم حاول مجددًا. لم يتغير شيء على هاتفك.';

  @override
  String get migrationTooLarge =>
      'يتجاوز هذا العنصر حد الحجم أو عدد الملفات المسموح به للنقل.';

  @override
  String get migrationTooLargeFix =>
      'يمكن أن يحتوي كل عنصر على ما يصل إلى 512 ميغابايت و20,000 ملف. احذف مجلدات البناء مثل node_modules في Termux، ثم حاول مجددًا.';

  @override
  String get migrationVerificationFailed =>
      'تعذّر التحقّق من النسخة. لم تتغيّر ملفاتك في Termux.';

  @override
  String get migrationDestinationChanged =>
      'تغيّرت الملفات المستوردة. لن تُستبدل.';

  @override
  String get migrationDestinationChangedFix =>
      'تبقى الملفات في «على هذا الهاتف» كما تركتها.';

  @override
  String get migrationStorageFailed =>
      'تعذّر حفظ النسخة. تحقّق من مساحة تخزين الهاتف وحاول مجددًا.';

  @override
  String get migrationTimedOut =>
      'استغرقت هذه الخطوة وقتًا طويلًا. أبقِ التطبيق مفتوحًا واستأنف.';

  @override
  String get migrationSelectionChanged =>
      'استخدم العناصر المحفوظة المختارة للنقل لاستئناف العملية.';

  @override
  String get migrationConnectionFailed =>
      'نُسخت الملفات، لكن تعذّر الاتصال بـ «على هذا الهاتف».';

  @override
  String get migrationFailureCode => 'السبب';

  @override
  String get migrationFailureItem => 'العنصر';

  @override
  String get migrationOpenThisPhone => 'فتح هذا الهاتف';

  @override
  String get migrationDoneTitle => 'تم النقل من Termux';

  @override
  String get migrationDone => 'نُسخت الملفات. لا يزال خادم Termux متاحًا.';

  @override
  String get migrationSignInAgain =>
      'تسجيل الدخول إلى مزوّدي الذكاء الاصطناعي مجددًا';

  @override
  String migrationSignInAgainNamed(String names) {
    return 'تظهر $names في إعدادات Termux. سجّل الدخول هنا لاستخدامها.';
  }

  @override
  String get migrationSignInAgainAny =>
      'لا تُنقل بيانات تسجيل الدخول من Termux أبدًا. إلى أن تسجّل الدخول هنا، تستخدم الردود نموذج OpenCode المجاني، وهو أبطأ.';

  @override
  String get migrationProjectsWhere => 'مشاريعك';

  @override
  String migrationProjectsWhereBody(String folder) {
    return 'في المجلد $folder في «على هذا الهاتف»';
  }

  @override
  String get migrationExportsWhere => 'النسخ الخاصة';

  @override
  String migrationExportsWhereBody(String items) {
    return '$items: محفوظة في «على هذا الهاتف» دون تفعيلها';
  }

  @override
  String get migrationRemoveTermux => 'إزالة خادم Termux عندما تكون جاهزًا';

  @override
  String get migrationRemoveTermuxBody =>
      'لا يُحذف شيء نيابةً عنك. حتى ذلك الحين، يواصل العمل ويمكنك العودة إليه من الخوادم.';

  @override
  String get migrationOpenBuiltin => 'فتح «على هذا الهاتف»';

  @override
  String get migrationDetailProjects => 'مجلد المشاريع';

  @override
  String get migrationDetailExports => 'مجلد النسخ الخاصة';

  @override
  String get migrationUnfinishedTitle => 'لم يكتمل النقل';

  @override
  String get migrationUnfinishedBody =>
      'استأنف للمتابعة من حيث توقف. يبقى ما نُسخ محفوظًا ولا يتغيّر Termux.';

  @override
  String get migrationUnavailableTitle => 'لا يمكن بدء النقل';

  @override
  String get migrationUnavailableBody =>
      'تعذّر على التطبيق تجهيز مساحة تخزينه الخاصة للنسخ. حاول مجددًا، وإذا تكررت المشكلة، فأعد تشغيل التطبيق.';

  @override
  String get migrationRowBody =>
      'انسخ مشاريعك إلى «على هذا الهاتف». يبقى Termux كما هو.';

  @override
  String get migrationRowResume => 'استئناف النقل إلى «على هذا الهاتف»';

  @override
  String get migrationRowResumeBody =>
      'توقف قبل اكتماله. يبقى ما نُسخ محفوظًا.';

  @override
  String get migrationRowRunning => 'جارٍ النقل إلى «على هذا الهاتف»';

  @override
  String get migrationRowDoneBody => 'أزل خادم Termux عندما تكون جاهزًا.';

  @override
  String get migrationOffer =>
      'هل تريد نقل مشاريعك من Termux إلى هذا التطبيق؟ يبقى Termux كما هو.';

  @override
  String get migrationOfferAction => 'مراجعة العناصر التي تُنقل';

  @override
  String get integrationsSignInUncertainNext =>
      'تحقّق من الخادم قبل البدء مجددًا';

  @override
  String teamHomeLastKnownTasks(String time) {
    return 'المهام حتى $time';
  }

  @override
  String teamHomeLastKnownAgents(String time) {
    return 'الوكلاء حتى $time';
  }

  @override
  String get teamHomeStoppedStartFirst =>
      'شغّل الفريق مجددًا لإعطائه مهمة أو فتح مهمة.';

  @override
  String get inAppServerStartExitedBody =>
      'أُغلق OpenCode تلقائيًا أثناء بدء تشغيله. افتح الإعداد لعرض سجله أو شغّله مجددًا.';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'لم يردّ OpenCode خلال $seconds ثانية. قد يكون الهاتف مشغولًا أو ذاكرته قليلة؛ أغلق التطبيقات الأخرى، ثم شغّله مجددًا.';
  }

  @override
  String get inAppServerStartInterruptedBody =>
      'توقف البدء لأن التطبيق غادر الشاشة. شغّله مجددًا للمتابعة.';

  @override
  String get inAppServerStartPasswordBody =>
      'تعذّر على التطبيق إعداد تسجيل دخول OpenCode على هذا الهاتف. شغّله مجددًا؛ وإذا تكررت المشكلة، فافتح الإعداد.';

  @override
  String get inAppServerStartRefusedBody =>
      'لم يسمح الهاتف للتطبيق بتشغيل OpenCode الآن. شغّله مجددًا؛ وإذا تكررت المشكلة، فأعد تشغيل الهاتف.';

  @override
  String get integrationsConnectWithKey => 'إضافة مفتاح API';

  @override
  String get integrationsConnectOnServer => 'الإعداد على الخادم';

  @override
  String integrationsProviderDetails(String name) {
    return 'تفاصيل $name';
  }

  @override
  String get integrationsEnvironmentVariable => 'متغيّر بيئة الخادم';

  @override
  String integrationsEnvironmentNote(String name) {
    return 'للاتصال بـ $name دون التطبيق، اضبط هذا في مكان تشغيل الخادم، ثم أعد تشغيل الخادم.';
  }

  @override
  String get termuxProblemAccessHeard =>
      'يعمل OpenCode في Termux، لكن هذا التطبيق لا يستطيع الوصول إلى Termux بعد. اسمح بالوصول وسيتصل.';

  @override
  String get termuxProblemAccessNeeded =>
      'لا يستطيع هذا التطبيق الوصول إلى Termux بعد. اسمح بالوصول ليتمكن من العثور على OpenCode هناك والاتصال به.';

  @override
  String get termuxProblemAccessBlocked =>
      'حظر Android وصول هذا التطبيق إلى Termux. في أذونات هذا التطبيق، فعّل «Run commands in Termux environment».';

  @override
  String get termuxProblemOtherAppsOff =>
      'لا يقبل Termux أوامر من تطبيقات أخرى بعد. يتيح سطر واحد في Termux ذلك.';

  @override
  String get termuxProblemAsleep =>
      'لم يردّ Termux. ربما وضعه Android في حالة سكون. افتح Termux لتنشيطه.';

  @override
  String termuxProblemNotAnswering(String runtime) {
    return 'أُعدّ $runtime في Termux، لكنه لا يردّ. عادةً تعيده إعادة التشغيل إلى العمل.';
  }

  @override
  String get termuxProblemNotInstalled =>
      'Termux غير موجود على هذا الهاتف. ثبّته مجددًا أو أعدّ «على هذا الهاتف» بدلًا منه.';

  @override
  String get termuxProblemOutdated =>
      'إصدار Termux هذا قديم جدًا ولا يمكن للتطبيق استخدامه. ثبّت أحدث إصدار من Termux من F-Droid.';

  @override
  String termuxProblemUnknown(String runtime) {
    return 'تعذّر على هذا الهاتف التحقّق من $runtime في Termux. حاول مجددًا بعد قليل.';
  }

  @override
  String get termuxFixAllowAccess => 'السماح بالوصول إلى Termux';

  @override
  String get termuxFixOpenPermissions => 'فتح أذونات هذا التطبيق';

  @override
  String get termuxFixAllowOtherApps => 'السماح لتطبيقات أخرى في Termux';

  @override
  String get termuxFixOpenTermux => 'فتح Termux';

  @override
  String termuxFixRestart(String runtime) {
    return 'إعادة تشغيل $runtime في Termux';
  }

  @override
  String get termuxFixGetTermux => 'تنزيل Termux';

  @override
  String get termuxFixGetCurrentTermux => 'تنزيل أحدث إصدار من Termux';

  @override
  String get termuxOtherAppsTitle => 'السماح للتطبيقات الأخرى';

  @override
  String get termuxOtherAppsBody =>
      'الصق هذا السطر في Termux واضغط Enter، ثم عد إلى هنا. يُنسخ السطر لك عند فتح Termux.';

  @override
  String get termuxLeadRunning => 'يعمل OpenCode في Termux';

  @override
  String get termuxLeadAccessLine =>
      'لا يستطيع هذا التطبيق الوصول إلى Termux بعد. اسمح بالوصول وسيتصل بمحادثاتك.';

  @override
  String get termuxLeadSetUp => 'أُعدّ OpenCode في Termux';

  @override
  String get termuxLeadTermuxOnly => 'Termux موجود على هذا الهاتف';

  @override
  String get termuxLeadRunningBody => 'اتصل لمتابعة محادثاتك.';

  @override
  String get termuxLeadStoppedBody => 'الخادم متوقف. شغّله لمتابعة محادثاتك.';

  @override
  String get termuxLeadConnect => 'الاتصال بالخادم في Termux';

  @override
  String get termuxLeadStart => 'تشغيل الخادم في Termux';

  @override
  String get termuxInAppInstead => 'إعداد «على هذا الهاتف» بدلًا منه';

  @override
  String get termuxInAppInsteadDetail =>
      'بداية جديدة تعمل داخل هذا التطبيق. لا حاجة إلى Termux.';

  @override
  String get termuxInAppInsteadBlocked =>
      'بداية جديدة داخل هذا التطبيق. لنقل مشاريعك من Termux، أصلح الوصول إلى Termux أولًا.';

  @override
  String get aboutBundledComponents => 'المكوّنات المضمّنة';

  @override
  String get aboutBundledComponentsDetail =>
      'أيقونات وخطوط ومكوّنات أخرى مضمّنة في هذا التطبيق';

  @override
  String get manageSpaceTitle => 'مسح مساحة تخزين هذا التطبيق';

  @override
  String get manageSpaceMeasuring => 'جارٍ قياس البيانات المخزّنة…';

  @override
  String get manageSpaceIntro =>
      'يحذف المسح كل ما يحتفظ به OpenCode Mobile على هذا الهاتف، ولا يمكن التراجع عنه. صدّر مشاريعك أولًا إذا أردت الاحتفاظ بها.';

  @override
  String get manageSpaceExportFirst => 'تصدير المشاريع أولًا';

  @override
  String get manageSpaceClearCache => 'مسح ذاكرة التطبيق المؤقتة فقط';

  @override
  String manageSpaceClearCacheDetail(String size) {
    return 'يوفّر $size. تبقى المشاريع والخوادم والإعدادات.';
  }

  @override
  String get manageSpaceClearCacheKeeps => 'تبقى المشاريع والخوادم والإعدادات.';

  @override
  String manageSpaceCacheCleared(String size) {
    return 'مُسحت الذاكرة المؤقتة. توفّر $size.';
  }

  @override
  String get manageSpaceCacheFailed =>
      'تعذّر مسح الذاكرة المؤقتة. حاول مجددًا.';

  @override
  String get manageSpaceTryAgain => 'حاول مجددًا';

  @override
  String get manageSpaceDeleteAll => 'حذف كل البيانات';

  @override
  String get manageSpaceDeleteAllDetail =>
      'يحذف كل العناصر في القائمة أدناه ويغلق التطبيق';

  @override
  String get manageSpaceDeleteTitle => 'هل تريد حذف كل البيانات؟';

  @override
  String get manageSpaceDeleteBody =>
      'يبدأ OpenCode Mobile مجددًا كأنه جديد. لا يمكن التراجع عن ذلك.';

  @override
  String get manageSpaceLostServer => '«على هذا الهاتف» ومحادثاته';

  @override
  String manageSpaceLostProjects(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عدد المشاريع: $count ($size)',
      one: 'مشروع واحد ($size)',
    );
    return '$_temp0';
  }

  @override
  String manageSpaceLostSettings(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'الخوادم المحفوظة وعددها $count وكل الإعدادات',
      one: 'خادم محفوظ واحد وكل الإعدادات',
      zero: 'كل الإعدادات',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptAll => 'يبقى Termux وحواسيبك وكل ما رفعته إلى git';

  @override
  String get manageSpaceWaitForExport => 'انتظر اكتمال التصدير';

  @override
  String get manageSpaceDeletedLabel => 'يحذف المسح';

  @override
  String get manageSpaceServer => 'على هذا الهاتف';

  @override
  String get manageSpaceServerDetail =>
      'Ubuntu وOpenCode وبيانات تسجيل دخوله ومحادثاته';

  @override
  String get manageSpaceSettings => 'الخوادم المحفوظة والإعدادات';

  @override
  String manageSpaceSavedServers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عدد الخوادم المحفوظة: $count',
      one: 'خادم محفوظ واحد',
      zero: 'لا توجد خوادم محفوظة',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptLabel => 'يبقى';

  @override
  String get manageSpaceKeptTermux => 'Termux والمشاريع فيه';

  @override
  String get manageSpaceKeptComputers => 'حواسيبك وخوادمها';

  @override
  String get manageSpaceKeptGit => 'كل ما رفعته إلى git';

  @override
  String projectExportDetail(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مشاريع، $size، في ملف zip واحد في المكان الذي تختاره',
      one: 'مشروع واحد، $size، في ملف zip واحد في المكان الذي تختاره',
    );
    return '$_temp0';
  }

  @override
  String get projectExportNoProjects =>
      'لا توجد مشاريع في «على هذا الهاتف» بعد';

  @override
  String get projectExportSave => 'حفظ ملف zip';

  @override
  String get projectExportRunning => 'جارٍ تصدير المشاريع';

  @override
  String get projectExportPreparing => 'جارٍ إعداد قائمة الملفات…';

  @override
  String projectExportProgress(String done, String total) {
    return '$done من $total';
  }

  @override
  String get projectExportStop => 'إيقاف التصدير';

  @override
  String get projectExportStopDetail => 'يُحذف الملف غير المكتمل';

  @override
  String get projectExportPrivate => 'تضمين بيانات تسجيل الدخول والمحادثات';

  @override
  String get projectExportPrivateDetail =>
      'خاص: يمكن لأي شخص يملك الملف استخدام حساباتك';

  @override
  String projectExportDone(String size, int files) {
    return 'صُدّرت المشاريع: $size في $files ملفات.';
  }

  @override
  String get projectExportDonePrivate =>
      'يحتوي هذا الملف على بيانات تسجيل الدخول. احتفظ به في مكان خاص.';

  @override
  String projectExportDoneLeftOut(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'استُبعدت $count ملفات تحتوي على بيانات تسجيل دخول أو مفاتيح.',
      one: 'استُبعد ملف واحد يحتوي على بيانات تسجيل دخول أو مفاتيح.',
    );
    return '$_temp0';
  }

  @override
  String get projectExportStopped => 'توقف التصدير. لم يُحفظ شيء.';

  @override
  String get projectExportFailedDestination =>
      'تعذّرت الكتابة في المكان الذي اخترته. حاول مجددًا أو اختر مكانًا آخر.';

  @override
  String get projectExportFailedSpace =>
      'المكان الذي اخترته ممتلئ. أخلِ بعض المساحة فيه أو اختر مكانًا آخر.';

  @override
  String get projectExportFailedSource =>
      'تعذّرت قراءة أحد ملفات المشروع. حاول مجددًا.';

  @override
  String get projectExportFailed => 'توقف التصدير قبل اكتماله. حاول مجددًا.';

  @override
  String get projectExportProjectsLabel => 'المشاريع';

  @override
  String get thisPhoneExportProjects => 'تصدير المشاريع';

  @override
  String get thisPhoneExportProjectsDetail =>
      'احفظها كملف zip للاحتفاظ بها أو نقلها';

  @override
  String get demoNoCommands =>
      'لا يتضمّن العرض التجريبي أوامر — أرسل الطلب النموذجي لعرض مراجعة تغيير.';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'سجّلت الدخول إلى $providers، لكن هذا الخادم تعذّر عليه تحميل بيانات تسجيل الدخول هذه حتى بعد إعادة التحميل، لذا لا يمكن لنماذجهم الإجابة. لا تُحمَّل على هذا الخادم بيانات تسجيل الدخول عبر المتصفح لبعض المزوّدين، مثل Anthropic وGoogle. أضف مفتاح API ضمن «مزوّدو الخدمة» بدلًا منها، أو اختر نموذجًا آخر.',
      one:
          'سجّلت الدخول إلى $providers، لكن هذا الخادم تعذّر عليه تحميل بيانات تسجيل الدخول حتى بعد إعادة التحميل، لذا لا يمكن لنماذجه الإجابة. لا تُحمَّل على هذا الخادم بيانات تسجيل الدخول عبر المتصفح لبعض المزوّدين، مثل Anthropic وGoogle. أضف مفتاح API ضمن «مزوّدو الخدمة» بدلًا منها، أو اختر نموذجًا آخر.',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'تنتظر إعادة التحميل انتهاء الردود الجارية، وعددها $count، لأنها ستوقفها.',
      one: 'تنتظر إعادة التحميل انتهاء رد واحد جارٍ، لأنها ستوقفه.',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      'يُستخدم نموذج OpenCode المجاني — وهو أبطأ. أضف مفتاح API من مزوّدك لاستخدام نموذجك الخاص.';

  @override
  String get chatFreeModelNote => 'نموذج مجاني، أبطأ';

  @override
  String get chatFreeModelAddKey => 'أضف مفتاحك';

  @override
  String get freeModelSignIn => 'إضافة مفتاح API';

  @override
  String get replySpeedTitle => 'سرعة الرد';

  @override
  String replySpeedLast(String first, String total) {
    return 'الرد الأخير: ظهرت أول كلمات بعد $first، واكتمل بعد $total';
  }

  @override
  String replySpeedNoWords(String total) {
    return 'الرد الأخير: انتهى بعد $total قبل ظهور أي كلمات';
  }

  @override
  String replySpeedSeconds(String seconds) {
    return '$seconds ث';
  }

  @override
  String get perfDetailLinuxMode => 'وضع سرعة Linux';

  @override
  String get perfLinuxModeFast => 'سريع: proot مع seccomp';

  @override
  String get perfLinuxModeSlow => 'بطيء: proot دون seccomp';

  @override
  String get perfLinuxModeUnknown => 'غير معروف أثناء توقف OpenCode';

  @override
  String get perfDetailAwake => 'إبقاء الهاتف مستيقظًا';

  @override
  String get perfAwakeNow => 'الآن، أثناء توليد رد';

  @override
  String get perfAwakeWhenWorking => 'أثناء توليد رد فقط';

  @override
  String get perfDetailFirstWords => 'الكلمات الأولى، آخر رد';

  @override
  String perfFirstWordsSplit(String app, String server) {
    return '$app في التطبيق · $server على الخادم';
  }

  @override
  String get perfDetailModel => 'النموذج، آخر رد';

  @override
  String get manageSpaceIntroNothingToExport =>
      'يحذف المسح كل ما يحتفظ به OpenCode Mobile على هذا الهاتف، ولا يمكن التراجع عنه.';

  @override
  String integrationsKeyOnlyHelper(String name) {
    return 'لا يتيح $name تسجيل الدخول عبر المتصفح من التطبيقات الأخرى، لذا استخدم مفتاح API. تُحتسب تكلفته منفصلة عن أي اشتراك. يُحفظ المفتاح على هذا الخادم ولا يُعرض مجددًا.';
  }

  @override
  String integrationsGetKey(String name) {
    return 'الحصول على مفتاح من $name';
  }

  @override
  String integrationsKeySavedReady(String name) {
    return 'حُفظ مفتاح $name. اختر أحد نماذجه من قائمة اختيار النموذج.';
  }

  @override
  String integrationsKeySavedWaiting(String name) {
    return 'حُفظ مفتاح $name. يُحمّل بعد انتهاء الردود الجارية.';
  }

  @override
  String integrationsKeySavedUnusable(String name) {
    return 'حُفظ مفتاح $name، لكن تعذّر على هذا الخادم تحميله بعد التحديث. تحقّق من المفتاح أو جرّب «إعادة تحميل المزوّدين» في قائمة اختيار النموذج.';
  }

  @override
  String integrationsKeySavedPending(String name) {
    return 'حُفظ مفتاح $name. لم يحمّله الخادم بعد.';
  }

  @override
  String migrationReviewSpace(String needed, String free) {
    return 'يحتاج نحو $needed · المتاح $free';
  }

  @override
  String migrationReviewSpaceUnknown(String needed) {
    return 'يحتاج نحو $needed · المساحة المتاحة غير معروفة';
  }

  @override
  String get migrationReviewSpaceShort =>
      'لا تتوفّر مساحة كافية لهذا. اختر عناصر أقل أو وفّر مساحة على هذا الهاتف.';

  @override
  String get migrationDiscard => 'حذف النسخة المحفوظة';

  @override
  String get migrationDiscardTitle => 'هل تريد حذف هذه النسخة المحفوظة؟';

  @override
  String get migrationDiscardBody =>
      'ستُحذف ملفات النسخة المؤقتة. تبقى الملفات المستوردة وكل ما في Termux.';

  @override
  String get migrationDiscardFailed =>
      'تعذّر حذف النسخة المحفوظة. حاول مجددًا بعد قليل.';

  @override
  String get migrationStopping => 'جارٍ الإيقاف…';

  @override
  String get pickerConnectProvider => 'توصيل مزوّد خدمة';

  @override
  String get pickerConnectProviderHint =>
      'أضف مفتاح API أو سجّل الدخول لاستخدام نماذجه';

  @override
  String pickerAddKeyFor(String name) {
    return 'إضافة مفتاح API لـ$name';
  }

  @override
  String get pickerAddKeyNotConnectedHint =>
      'لا تظهر نماذجه في هذه القائمة بعد';

  @override
  String pickerSignInTo(String name) {
    return 'تسجيل الدخول إلى $name';
  }

  @override
  String get pickerSignInHint => 'يفتح خيارات تسجيل الدخول لهذا الخادم';

  @override
  String get pickerFreeOnlyNote =>
      'تتوفر نماذج OpenCode المجانية فقط، وهي أبطأ.';

  @override
  String pickerProviderReady(String name) {
    return '$name جاهز. تظهر نماذجه في القائمة.';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return 'حُفظ $name، لكن الخادم لم يحمّله بعد.';
  }

  @override
  String get integrationsSignedInUnusable =>
      'تم تسجيل الدخول، لكن هذا الخادم لا يستطيع استخدامه';

  @override
  String get chatUiCompactConfirmTitle => 'هل تريد تلخيص هذه المحادثة؟';

  @override
  String get chatUiCompactConfirmBody =>
      'يستبدل التلخيص الرسائل السابقة بملخص قصير لتوفير المساحة. لا يمكن التراجع عنه.';

  @override
  String get chatUiCompactConfirmAction => 'تلخيص المحادثة';

  @override
  String get kitTurnReconnecting =>
      'انقطع الاتصال. جارٍ إعادة الاتصال للحصول على بقية هذا الرد.';

  @override
  String get kitTurnLiveSending => 'جارٍ الإرسال';

  @override
  String get kitTurnLiveWaitingForServer => 'بانتظار الخادم';

  @override
  String get kitTurnLiveServerQuiet => 'لم يرد الخادم بعد';

  @override
  String get kitTurnLiveThinking => 'جارٍ التفكير';

  @override
  String get kitTurnLiveFirstWordSlow => 'بانتظار الكلمة الأولى من النموذج';

  @override
  String get kitTurnLiveFirstWordSlowTeam =>
      'يعمل AI Team أيضًا على هذا الهاتف، لذا قد تكون الردود أبطأ';

  @override
  String get kitTurnLiveWriting => 'جارٍ الكتابة';

  @override
  String get kitTurnLiveWorking => 'قيد العمل';

  @override
  String get kitTurnLiveWaitingForYou => 'بانتظارك';

  @override
  String get kitTurnLiveStop => 'إيقاف الرد';

  @override
  String kitTurnLiveNow(String status) {
    return '$status…';
  }

  @override
  String kitTurnLiveFor(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String kitTurnLiveSeconds(int seconds) {
    return '$seconds ث';
  }

  @override
  String kitTurnLiveMinutes(int minutes, int seconds) {
    return '$minutes د $seconds ث';
  }

  @override
  String get chatNoReplyCameBack => 'لم يصل أي رد';

  @override
  String get composerFieldLabel => 'رسالة إلى الوكيل';

  @override
  String get e7WorkspaceYesterday => 'أمس';

  @override
  String teamProgressOne(String title) {
    return 'فريق الذكاء الاصطناعي: $title';
  }

  @override
  String teamProgressStep(String title, int done, int total) {
    return 'فريق الذكاء الاصطناعي: $title · الخطوة $done من $total';
  }

  @override
  String teamProgressMany(int count) {
    return 'فريق الذكاء الاصطناعي: $count مهام قيد العمل';
  }

  @override
  String get teamSettingsTitle => 'إعدادات الفريق';

  @override
  String get teamSettingsOpenTooltip => 'إعدادات الفريق';

  @override
  String get teamSettingsTurnOff => 'إيقاف فريق الذكاء الاصطناعي';

  @override
  String teamChatWorkerNumbered(String role, int n) {
    return '$role $n';
  }

  @override
  String get teamRoleNameGeneral => 'عام';

  @override
  String get teamRoleNameProduct => 'المنتج';

  @override
  String get teamRoleNameFrontend => 'الواجهة الأمامية';

  @override
  String get teamRoleNameBackend => 'الواجهة الخلفية';

  @override
  String get teamRoleNameTester => 'مختبِر';

  @override
  String get teamRolePurposeGeneral => 'أي مهمة، بأسلوب بسيط';

  @override
  String get teamRolePurposeProduct => 'يحوّل الفكرة إلى متطلبات واضحة وخطة';

  @override
  String get teamRolePurposeFrontend => 'الشاشات والتخطيط وسهولة الاستخدام';

  @override
  String get teamRolePurposeBackend =>
      'الخوادم والبيانات والبرمجيات وراء الشاشات';

  @override
  String get teamRolePurposeTester => 'يكتشف ما يتعطل ويتحقق من أنه يعمل';

  @override
  String get teamRolesTitle => 'الوكلاء';

  @override
  String get teamRolesNew => 'دور جديد';

  @override
  String teamRoleWorkingOn(String task, String age) {
    return 'يعمل على «$task» · $age';
  }

  @override
  String teamRoleUses(String model) {
    return 'يستخدم $model';
  }

  @override
  String get teamRoleUsesTeamModel => 'يستخدم نموذج الفريق';

  @override
  String get teamRoleUsesComputerModel => 'يستخدم نموذج الكمبيوتر';

  @override
  String teamRoleTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count من المهام',
      one: 'مهمة واحدة',
      zero: 'لا توجد مهام بعد',
    );
    return '$_temp0';
  }

  @override
  String teamSettingsAgentsRow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'الوكلاء · $count من الأدوار',
      one: 'الوكلاء · دور واحد',
    );
    return '$_temp0';
  }

  @override
  String get teamSettingsAgentsHint => 'من ينجز العمل وكيف يعمل كل منهم';

  @override
  String get teamRoleNewTitle => 'دور جديد';

  @override
  String get teamRoleFieldName => 'الاسم';

  @override
  String get teamRoleFieldPurpose => 'الغرض منه';

  @override
  String get teamRoleFieldPurposeHint => 'سطر واحد، مثل: يكتب الأدلة';

  @override
  String get teamRoleFieldInstructions => 'التعليمات';

  @override
  String get teamRoleFieldInstructionsHint =>
      'كيف ينبغي لهذا الدور أن يعمل، بكلماتك';

  @override
  String get teamRoleNameRequired => 'أعطِ الدور اسمًا';

  @override
  String get teamRoleModelRow => 'النموذج';

  @override
  String get teamRoleTeamModel => 'نموذج الفريق';

  @override
  String get teamRoleComputerModel => 'نموذج الكمبيوتر';

  @override
  String get teamRoleModelSheetDefault => 'نموذج الفريق';

  @override
  String get teamRoleModelSheetDefaultHint =>
      'يستخدم النموذج الذي يستخدمه الفريق كله';

  @override
  String teamRoleWorkingNow(String task) {
    return 'يعمل على «$task»';
  }

  @override
  String get teamRoleOpenConversation => 'فتح محادثته';

  @override
  String get teamRoleRecentTasks => 'المهام الأخيرة';

  @override
  String teamRoleNoTasks(String role) {
    return 'لم تُسند أي مهمة إلى $role بعد';
  }

  @override
  String teamRoleGiveTask(String role) {
    return 'إسناد مهمة إلى $role';
  }

  @override
  String get teamRoleSave => 'حفظ';

  @override
  String get teamRoleCreate => 'إنشاء الدور';

  @override
  String teamRoleReset(String role) {
    return 'إعادة ضبط $role';
  }

  @override
  String teamRoleResetTitle(String role) {
    return 'هل تريد إعادة ضبط $role؟';
  }

  @override
  String get teamRoleResetBody =>
      'يعود اسمه وغرضه وتعليماته ونموذجه إلى الإعدادات الأصلية.';

  @override
  String teamRoleDelete(String role) {
    return 'حذف $role';
  }

  @override
  String teamRoleDeleteTitle(String role) {
    return 'هل تريد حذف $role؟';
  }

  @override
  String teamRoleDeleteBody(String role) {
    return 'يُزال $role من هذا الفريق. تحتفظ المهام التي أنجزها باسمه.';
  }

  @override
  String get teamRoleWorkerName => 'اسم العامل';

  @override
  String get teamRoleExamplesLabel => 'البدء من مثال';

  @override
  String get teamRoleExampleDocs => 'كاتب توثيق';

  @override
  String get teamRoleExampleDocsPurpose => 'يكتب الأدلة ويحدّثها';

  @override
  String get teamRoleExampleDocsInstructions =>
      'أنت تكتب التوثيق وتحدّثه. اجعله موجزًا ودقيقًا وبكلمات بسيطة. تحقّق من كل أمر ومسار تذكره قبل كتابته.';

  @override
  String get teamRoleExampleSecurity => 'مراجع أمني';

  @override
  String get teamRoleExampleSecurityPurpose =>
      'يبحث عن طرق إساءة استخدام البرمجيات';

  @override
  String get teamRoleExampleSecurityInstructions =>
      'أنت تراجع البرمجيات بحثًا عن مشكلات أمنية: أسرار في الشيفرة أو السجلات، ومدخلات لم يُتحقّق منها، وروابط غير آمنة، وفحوص أذونات مفقودة. أبلغ عمّا تجده مع الملف والسطر، وأصلح ما تطلبه المهمة فقط.';

  @override
  String get teamRoleExampleDesigner => 'مصمم';

  @override
  String get teamRoleExampleDesignerPurpose => 'يجعله واضحًا ومتسقًا ومريحًا';

  @override
  String get teamRoleExampleDesignerInstructions =>
      'أنت تحسّن مظهر المنتج وصياغته. أعد استخدام الأجزاء والكلمات الموجودة في التطبيق، وحافظ على أسلوب تصميم واحد، وتحقّق من الشاشات الصغيرة والنص الكبير.';

  @override
  String get teamRoleStarterInstructions =>
      'أنت ___ في هذا الفريق.\nركّز على: ___\nدائمًا: ___\nلا تفعل أبدًا: ___';

  @override
  String get teamStartRunWho => 'من';

  @override
  String get teamStartRunWhoSuggested => 'مقترح بناءً على كلماتك';

  @override
  String get teamStartRunWhoChange => 'تغيير';

  @override
  String get teamStartRunWhoTitle => 'من ينبغي أن يتولى هذه المهمة؟';

  @override
  String teamChatLeadStartingRole(String role, String title) {
    return 'بدأ $role العمل على «$title»';
  }

  @override
  String teamChatLeadClaimedRole(String role, String title) {
    return 'تولّى $role «$title»';
  }

  @override
  String teamChatLeadStartingItRole(String role) {
    return 'بدأ $role العمل';
  }

  @override
  String teamChatLeadClaimedItRole(String role) {
    return 'تولّى $role المهمة';
  }

  @override
  String get teamRolesSearchAliases =>
      'أدوار شخصيات وكلاء فريق واجهة أمامية واجهة خلفية مختبر منتج مصمم تعليمات';

  @override
  String get teamUiStatePhoneStoppedTitle => 'توقف AI Team';

  @override
  String get teamUiStatePhoneStoppedBody =>
      'لا يعمل AI Team على هذا الهاتف. شغّله لمتابعة مهامك.';

  @override
  String get teamStartStepService => 'تشغيل خدمة الفريق';

  @override
  String get teamStartStepAnswering => 'انتظار رد الفريق';

  @override
  String get teamStartStepStore => 'فتح مخزن المهام';

  @override
  String get teamStartStepAgents => 'تجهيز الوكلاء';

  @override
  String get teamStartSlow => 'يستغرق وقتًا أطول من المعتاد، الهاتف مشغول';

  @override
  String get teamStartAgain => 'ابدأ من جديد';

  @override
  String get teamUiHostPhraseStarting => 'قيد البدء';

  @override
  String get teamUiStartOnPhone => 'تشغيل AI Team على هذا الهاتف';

  @override
  String get chatCollapseAllSteps => 'طي كل الخطوات';

  @override
  String chatWatchTeamInstructions(int count, String time) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تعليمات من الفريق · $countString كلمة · $time',
      one: 'تعليمات من الفريق · كلمة واحدة · $time',
    );
    return '$_temp0';
  }

  @override
  String get chatWatchEmptyStartingTitle => 'جارٍ البدء';

  @override
  String get chatWatchEmptyStartingBody => 'تظهر خطواته هنا أثناء عمله.';

  @override
  String chatWatchEmptyWorkingOn(String task) {
    return 'يعمل على «$task». تظهر خطواته هنا أثناء عمله.';
  }

  @override
  String chatWatchEmptyReviewing(String task) {
    return 'يراجع تغييرات «$task». تظهر خطواته هنا أثناء عمله.';
  }

  @override
  String get chatWatchEmptyIdleTitle => 'في الانتظار';

  @override
  String get chatWatchEmptyIdleBody =>
      'ينتظر مهمته التالية. راسله بالأسفل ليطلب شيئًا.';

  @override
  String get teamUiAgentLabelSessionTitle => 'عنوان الجلسة';

  @override
  String get kitComposerPillNoAnswer => 'لا توجد إجابة بعد';

  @override
  String get kitComposerRailRetry => 'إعادة المحاولة';

  @override
  String get teamProjectHome => 'AI Team';

  @override
  String get teamProjectDemo => 'عرض تجريبي';

  @override
  String get teamProjectNew => 'مشروع جديد';

  @override
  String get teamProjectQuick => 'إسناد مهمة سريعة';

  @override
  String get teamProjectSettings => 'إعدادات المشروع';

  @override
  String get teamProjectRoles => 'الأدوار والوكلاء';

  @override
  String get teamProjectEmpty => 'أعطِ فريقك هدفًا لبدء مشروع.';

  @override
  String get teamProjectSelect => 'اختيار مشروع';

  @override
  String get teamProjectSelectTask => 'اختر مهمة لمتابعة محادثتها.';

  @override
  String get teamProjectLoad => 'جارٍ تحميل المشاريع';

  @override
  String get teamProjectRetry => 'إعادة المحاولة';

  @override
  String get teamProjectError =>
      'تعذّر تحديث المشروع. لا يزال عملك المحفوظ متاحًا.';

  @override
  String get teamProjectSpec => 'فتح المواصفات';

  @override
  String get teamProjectPlan => 'مراجعة الخطة';

  @override
  String get teamProjectBoard => 'اللوحة';

  @override
  String get teamProjectGraph => 'الاعتماديات';

  @override
  String get teamProjectTimeline => 'المخطط الزمني';

  @override
  String get teamProjectServers => 'الخوادم';

  @override
  String get teamProjectMilestones => 'المراحل الرئيسية';

  @override
  String get teamProjectMerge => 'قائمة انتظار الدمج';

  @override
  String get teamProjectCost => 'التكلفة';

  @override
  String get teamProjectDecisions => 'القرارات الأخيرة';

  @override
  String get teamProjectPause => 'إيقاف المشروع مؤقتًا';

  @override
  String get teamProjectResume => 'استئناف المشروع';

  @override
  String get teamProjectStopConfirmTitle => 'هل تريد إيقاف هذا المشروع؟';

  @override
  String get teamProjectStop => 'إيقاف المشروع';

  @override
  String get teamProjectStopBody =>
      'ستتوقف المهام الجارية. سيُحتفظ بعملها وسجل المشروع.';

  @override
  String get teamProjectAdvance => 'تقديم العرض التجريبي';

  @override
  String get teamProjectDigest => 'منذ غيابك';

  @override
  String get teamProjectDigestRead => 'وضع علامة مقروء';

  @override
  String get teamProjectAnswer => 'إجابة';

  @override
  String get teamProjectAnswerLabel => 'إجابتك';

  @override
  String get teamProjectAll => 'الكل';

  @override
  String get teamProjectMerges => 'عمليات الدمج';

  @override
  String get teamProjectProblems => 'المشكلات';

  @override
  String get teamProjectMilestoneFilter => 'المرحلة الرئيسية';

  @override
  String get teamProjectRepoFilter => 'المستودع';

  @override
  String get teamProjectServerFilter => 'الخادم';

  @override
  String get teamProjectBacklog => 'المهام المؤجلة';

  @override
  String get teamProjectReady => 'جاهزة';

  @override
  String get teamProjectWorking => 'قيد العمل';

  @override
  String get teamProjectReview => 'المراجعة';

  @override
  String get teamProjectDone => 'مكتملة';

  @override
  String get teamProjectNoTasks => 'لا توجد مهام في هذا العرض.';

  @override
  String get teamProjectMove => 'نقل المهمة';

  @override
  String get teamProjectMoveTo => 'نقل إلى خادم';

  @override
  String get teamProjectHandoff => 'ملاحظة تسليم العمل';

  @override
  String get teamProjectPaused => 'متوقف مؤقتًا';

  @override
  String get teamProjectStopped => 'متوقف';

  @override
  String get teamProjectFailed => 'توقف بشكل غير متوقع';

  @override
  String get teamProjectStalled => 'لا يوجد تقدم حديث';

  @override
  String get teamProjectPlanning => 'صياغة المواصفات';

  @override
  String get teamProjectPlanWaiting => 'الخطة جاهزة للمراجعة';

  @override
  String get teamProjectNeedsYou => 'يحتاج إلى قرارك';

  @override
  String get teamProjectWaiting => 'بانتظار الاعتماديات';

  @override
  String get teamProjectOnline => 'يمكن الوصول إليه';

  @override
  String get teamProjectOffline => 'يتعذّر الوصول إليه · آخر المهام المعروفة';

  @override
  String get teamProjectNoLimit => 'بلا حد';

  @override
  String get teamProjectUnknown => 'لم يُبلَّغ عنه';

  @override
  String get teamProjectAcceptMilestoneConfirmTitle =>
      'هل تريد قبول هذه المرحلة الرئيسية؟';

  @override
  String get teamProjectAccept => 'قبول المرحلة الرئيسية';

  @override
  String get teamProjectMergeConfirmTitle => 'هل تريد الدمج في dev؟';

  @override
  String get teamProjectMergeNext => 'دمج العمل المتحقق منه في dev';

  @override
  String get teamProjectCostDemo =>
      'أرقام العرض التجريبي محاكاة؛ لم تُقَس ذاكرة الجهاز أو البطارية أو الحرارة أو سرعة المحادثة.';

  @override
  String teamProjectPlanningElapsed(int minutes) {
    return 'نصوغ المواصفات · $minutes د';
  }

  @override
  String teamProjectPlanningSlow(int minutes) {
    return 'ما زلنا نصوغ المواصفات · $minutes د';
  }

  @override
  String teamProjectAllTasksDone(int total) {
    return 'اكتملت كل المهام ($total)';
  }

  @override
  String teamProjectProgress(int done, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working قيد العمل',
      zero: '',
    );
    return 'اكتملت $done من أصل $total من المهام$_temp0';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy من أصل $total من المسارات مشغولة';
  }

  @override
  String get teamProjectEditorNewProject => 'مشروع جديد';

  @override
  String get teamProjectEditorQuickTask => 'مهمة سريعة';

  @override
  String get teamProjectEditorSpec => 'مواصفات متجددة';

  @override
  String get teamProjectEditorPlan => 'مراجعة الخطة';

  @override
  String get teamProjectEditorSettings => 'إعدادات المشروع';

  @override
  String get teamProjectEditorRoles => 'الأدوار والوكلاء';

  @override
  String get teamProjectEditorStartPlanning => 'بدء التخطيط';

  @override
  String get teamProjectEditorStartTask => 'بدء المهمة';

  @override
  String get teamProjectEditorApproveSpec => 'اعتماد المواصفات';

  @override
  String get teamProjectEditorApprovePlan => 'اعتماد وبدء العمل';

  @override
  String get teamProjectEditorSave => 'حفظ التغييرات';

  @override
  String get teamProjectEditorSaveDraft => 'حفظ المسودة';

  @override
  String get teamProjectEditorName => 'اسم المشروع';

  @override
  String get teamProjectEditorGoal => 'الهدف';

  @override
  String get teamProjectEditorRepos => 'المستودعات';

  @override
  String get teamProjectEditorRepoName => 'اسم المستودع';

  @override
  String get teamProjectEditorRepoPath => 'مجلد المستودع';

  @override
  String get teamProjectEditorServer => 'الخادم';

  @override
  String get teamProjectEditorRemove => 'إزالة';

  @override
  String get teamProjectEditorAddRepo => 'إضافة مستودع';

  @override
  String get teamProjectEditorRole => 'الدور';

  @override
  String get teamProjectEditorPlanFirst => 'التخطيط أولًا';

  @override
  String get teamProjectEditorMode => 'وضع التنفيذ';

  @override
  String get teamProjectEditorSingle => 'مسار واحد';

  @override
  String get teamProjectEditorParallel => 'وكلاء يعملون بالتوازي';

  @override
  String get teamProjectEditorMaxLanes => 'الحد الأقصى للمسارات';

  @override
  String teamProjectEditorCostMeasured(String host, String memory) {
    return 'على $host: نحو $memory ميغابايت من الذاكرة لكل مسار، مقيسة. البطارية وسرعة المحادثة لم تُقَس بعد.';
  }

  @override
  String teamProjectEditorCostNotMeasured(String host) {
    return 'لم تُقَس على $host بعد. محادثتك تبقى أولًا.';
  }

  @override
  String get teamProjectEditorCostNoHost =>
      'اختر مكان تشغيل العمل لترى كلفة المسار هناك.';

  @override
  String get teamProjectEditorThisPhone => 'هذا الهاتف';

  @override
  String get teamProjectEditorGoalRequired => 'أضف هدفًا.';

  @override
  String get teamProjectEditorRepoMissing => 'أضف مستودعًا واحدًا على الأقل.';

  @override
  String get teamProjectEditorRepoIncomplete =>
      'أكمل المستودع: اسم ومجلد ومكان التشغيل.';

  @override
  String get teamProjectEditorNoFallback => 'بلا نموذج احتياطي';

  @override
  String get teamProjectEditorNoFallbackHint =>
      'ينتظر العمل النموذج الأساسي بدل التبديل.';

  @override
  String get teamProjectEditorReadOnlyRole =>
      'للقراءة فقط: يقرأ هذا الوكيل المشروع ولا يغيّره.';

  @override
  String get teamProjectEditorReadOnlyShort => 'للقراءة فقط';

  @override
  String get teamProjectEditorCharging => 'أثناء الشحن فقط';

  @override
  String get teamProjectEditorReview => 'مستوى المراجعة';

  @override
  String get teamProjectEditorMilestonesRisk =>
      'المراحل الرئيسية والنقاط الخطرة';

  @override
  String get teamProjectEditorEveryStep => 'كل خطوة';

  @override
  String get teamProjectEditorBudget => 'الميزانية';

  @override
  String get teamProjectEditorSetLimits => 'تعيين الحدود';

  @override
  String get teamProjectEditorNoLimit => 'بلا حد';

  @override
  String get teamProjectEditorDailyBudget => 'لكل يوم (USD)';

  @override
  String get teamProjectEditorTotalBudget => 'الإجمالي (USD)';

  @override
  String get teamProjectEditorTaskTokens => 'حد الرموز لكل مهمة (اختياري)';

  @override
  String get teamProjectEditorAutoFix => 'إصلاح المشكلات المكتشفة تلقائيًا';

  @override
  String get teamProjectEditorMaxRounds => 'الحد الأقصى لجولات الإصلاح';

  @override
  String get teamProjectEditorConstraints => 'القيود';

  @override
  String get teamProjectEditorDecisions => 'القرارات';

  @override
  String get teamProjectEditorOutOfScope => 'خارج النطاق';

  @override
  String get teamProjectEditorMilestones => 'المراحل الرئيسية';

  @override
  String get teamProjectEditorMilestoneTitle => 'عنوان المرحلة الرئيسية';

  @override
  String get teamProjectEditorCriteria => 'معايير القبول (معيار في كل سطر)';

  @override
  String get teamProjectEditorMoveUp => 'نقل لأعلى';

  @override
  String get teamProjectEditorMoveDown => 'نقل لأسفل';

  @override
  String get teamProjectEditorAddMilestone => 'إضافة مرحلة رئيسية';

  @override
  String get teamProjectEditorHistory => 'سجل الإصدارات';

  @override
  String get teamProjectEditorVersion => 'الإصدار';

  @override
  String get teamProjectEditorPlanHelp =>
      'راجع المهام ومعايير قبولها. تُضمّن تغييراتك هنا عند اعتماد الخطة.';

  @override
  String get teamProjectEditorRisky => 'نقطة مراجعة · محفوفة بالمخاطر';

  @override
  String get teamProjectEditorTaskTitle => 'عنوان المهمة';

  @override
  String get teamProjectEditorRepo => 'المستودع';

  @override
  String get teamProjectEditorDependencies => 'تعتمد على';

  @override
  String get teamProjectEditorRemoveTask => 'إزالة المهمة';

  @override
  String get teamProjectEditorAddRole => 'إضافة دور';

  @override
  String get teamProjectEditorRoleName => 'اسم الدور';

  @override
  String get teamProjectEditorInstructions => 'التعليمات';

  @override
  String get teamProjectEditorModel => 'النموذج';

  @override
  String get teamProjectEditorFallback => 'النموذج البديل';

  @override
  String get teamProjectEditorAllRoles => 'كل الأدوار';

  @override
  String get teamProjectEditorChooseMode =>
      'اختر «مسار واحد» أو «وكلاء يعملون بالتوازي».';

  @override
  String get teamProjectEditorPositiveLanes => 'أدخل حدًا للمسارات بين 1 و32.';

  @override
  String get teamProjectEditorChooseBudget => 'عيّن ميزانية أو اختر «بلا حد».';

  @override
  String get teamProjectEditorPositiveBudget =>
      'أدخل حدًا يوميًا وحدًا إجماليًا، كلاهما أكبر من صفر.';

  @override
  String get teamProjectEditorSaveFailed =>
      'تعذّر حفظ التغييرات. لا تزال تعديلاتك هنا؛ حاول الحفظ مجددًا.';

  @override
  String get teamProjectEditorRequired =>
      'أضف هدفًا ومستودعًا واحدًا على الأقل.';

  @override
  String get teamProjectEditorChooseRoleServer =>
      'اختر دورًا وخادمًا لهذه المهمة.';

  @override
  String get teamProjectEditorRepoRequired =>
      'اختر خادمًا وأدخل اسم المستودع ومجلده.';

  @override
  String get teamProjectEditorSpecRequired =>
      'أضف هدفًا ومرحلة رئيسية واحدة على الأقل بعنوان ومعايير قبول.';

  @override
  String get teamProjectEditorDraftFailed =>
      'تعذّر الاحتفاظ بالمسودة على هذا الجهاز. أبقِ هذه الشاشة مفتوحة وحاول الحفظ مجددًا.';

  @override
  String get teamProjectEditorChangedElsewhere =>
      'تغيّر هذا المشروع أثناء تحريرك له. أغلق هذه اللوحة وراجع أحدث حالة للمشروع قبل اعتماد التغييرات.';

  @override
  String get teamProjectTaskMissing => 'لم تعد هذه المهمة متاحة';

  @override
  String get teamProjectRefreshTask => 'تحديث المهمة';

  @override
  String get teamProjectTaskSaveFailed =>
      'لم يُحفظ التغيير. حدّث الحالة وحاول مجددًا؛ لا تزال رسالتك هنا.';

  @override
  String get teamProjectTaskMessage => 'أرسل رسالة إلى الفريق…';

  @override
  String get teamProjectTaskInstructions => 'تعليمات الفريق';

  @override
  String get teamProjectTaskApprovePlan => 'اعتماد وبدء العمل';

  @override
  String get teamProjectTaskReview => 'المراجعة مطلوبة';

  @override
  String get teamProjectTaskAccepted => 'مقبولة';

  @override
  String get teamProjectTaskAcceptPhase => 'قبول المرحلة';

  @override
  String get teamProjectTaskFindings => 'نتائج التحقق';

  @override
  String get teamProjectTaskFix => 'إصلاح المحدد';

  @override
  String get teamProjectTaskRecheck => 'إعادة التحقق من المهمة';

  @override
  String get teamProjectTaskIgnore => 'تجاهل النتيجة المحددة';

  @override
  String get teamProjectTaskIgnoreReason =>
      'لماذا يمكن تجاهل هذه النتيجة بأمان؟';

  @override
  String get teamProjectTaskReasonRequired =>
      'أدخل سببًا للاحتفاظ به مع هذا القرار.';

  @override
  String get teamProjectTaskCritical => 'حرجة';

  @override
  String get teamProjectTaskMajor => 'كبيرة';

  @override
  String get teamProjectTaskMinor => 'طفيفة';

  @override
  String get teamProjectTaskMerge => 'قائمة انتظار الدمج في dev';

  @override
  String get teamProjectTaskMergeRun => 'التحقق والدمج في dev';

  @override
  String get teamProjectTaskPromoteConfirmTitle =>
      'هل تريد ترقية dev إلى main؟';

  @override
  String get teamProjectTaskPromote => 'ترقية dev إلى main';

  @override
  String get teamProjectTaskPromoteBody =>
      'يُحدّث هذا الإجراء فرع main المحمي إلى نسخة dev التي راجعتها. سيتحقق المحرّك من النسختين مجددًا قبل تغيير main.';

  @override
  String get teamProjectTaskDiff => 'عرض التغييرات';

  @override
  String get teamProjectTaskPause => 'إيقاف المهمة مؤقتًا';

  @override
  String get teamProjectTaskResume => 'استئناف المهمة';

  @override
  String get teamProjectTaskStopConfirmTitle => 'هل تريد إيقاف هذه المهمة؟';

  @override
  String get teamProjectTaskStop => 'إيقاف المهمة';

  @override
  String get teamProjectTaskStopBody =>
      'أوقف هذه المهمة واحتفظ بمحادثتها وتغييراتها للمراجعة.';

  @override
  String get teamProjectTaskRestart => 'بدء المهمة مجددًا';

  @override
  String get teamProjectTaskAnswer => 'إرسال الإجابة';

  @override
  String get teamProjectTaskAnswerLabel => 'إجابتك';

  @override
  String get teamProjectTaskRunning => 'قيد العمل';

  @override
  String get teamProjectTaskWaiting => 'بانتظار';

  @override
  String get teamProjectTaskDone => 'مكتملة';

  @override
  String get teamProjectTaskFailed => 'توقفت المهمة قبل اكتمالها';

  @override
  String get teamProjectTaskStale => 'آخر حالة معروفة';

  @override
  String get teamProjectTaskCollapse => 'طي الكل';

  @override
  String get teamProjectTaskWork => 'العمل المنجز';

  @override
  String get teamProjectTaskEmpty =>
      'المهمة في قائمة الانتظار. ستظهر ردودها وفحوصها هنا.';

  @override
  String get teamProjectTaskReceipt => 'إيصال الترقية';

  @override
  String get teamProjectTaskVerify => 'التحقّق من المهمة';

  @override
  String get teamProjectTryDemo => 'تجربة العرض التوضيحي لـAI Team';

  @override
  String get teamProjectLoadFailure =>
      'تعذّر فتح العرض التوضيحي. احتُفظ ببيانات مشروعك المحفوظة.';

  @override
  String get teamProjectDemoDisclosure =>
      'مشاريع محاكاة. لا يعمل أي وكلاء ولا تتغيّر أي مستودعات.';

  @override
  String get teamProjectOff => 'مغادرة العرض التوضيحي';

  @override
  String get teamProjectEditorFixRoundsRange =>
      'أدخل حدًا لجولات الإصلاح من 0 إلى 3.';

  @override
  String get teamProjectEditorPositiveTokens =>
      'أدخل حدًا موجبًا للرموز أو اتركه فارغًا.';

  @override
  String get teamProjectEditorReloadConfirmTitle =>
      'هل تريد تحديث هذا المشروع؟';

  @override
  String get teamProjectEditorReload => 'تحديث المشروع إلى أحدث حالة';

  @override
  String get teamProjectEditorDiscardDraft =>
      'يستبدل هذا تعديلاتك غير المحفوظة بأحدث حالة للمشروع. يبقى مشروعك المحفوظ كما هو.';

  @override
  String get teamProjectEditorRoleRequired => 'أدخل اسمًا لهذا الدور.';

  @override
  String get teamProjectEditorDefaults => 'الإعدادات الافتراضية للمشروع الجديد';

  @override
  String get teamProjectEditorApplyPlan => 'تطبيق الخطة المحدّثة';

  @override
  String get teamProjectEditorContextFiles => 'الملفات المطلوب قراءتها أولًا';

  @override
  String get teamProjectEditorContextFilesHelp =>
      'اختياري. مسار واحد في كل سطر. يقرأ الفريق هذه الملفات قبل التخطيط. لا يقرأ العرض التوضيحي الملفات ولا يرفعها.';

  @override
  String get teamProjectEditorScreenOff => 'مواصلة العمل والشاشة مغلقة';

  @override
  String get teamProjectEditorScreenOffHelp =>
      'يُحفظ هذا التفضيل للمشروع. يبقى العمل في الخلفية خاضعًا لحدود الخادم والنظام.';

  @override
  String get teamProjectEditorDraftApproval =>
      'تحتاج تغييرات المسودة إلى موافقتك قبل اعتمادها كمواصفات المشروع.';

  @override
  String get teamProjectEditorChangeRequest => 'ما الذي تريد أن يغيّره المخطط؟';

  @override
  String get teamProjectEditorAskChange => 'طلب التغيير';

  @override
  String get teamProjectEditorChangeRequired =>
      'أضف هدفًا وصف التغيير الذي تريده.';

  @override
  String get teamProjectTaskCriteria => 'معايير القبول';

  @override
  String get teamProjectTaskOpenFindings => 'الملاحظات غير المعالجة';

  @override
  String get teamProjectTaskFindingsAddressed => 'الملاحظات المعالجة';

  @override
  String get teamProjectMergeConfirmBody =>
      'ستُدمج فروع المهام التي جرى التحقّق منها في dev، ثم تُجرى الفحوص المشتركة. يبقى Main كما هو.';

  @override
  String get teamProjectEditorDraftClearFailed =>
      'حُفظت التغييرات، لكن تعذّر مسح المسودة المحلية. أغلق هذه اللوحة وراجع المشروع قبل المحاولة مجددًا.';

  @override
  String get teamProjectRestartElsewhereConfirmTitle =>
      'هل تريد البدء من جديد في مكان آخر؟';

  @override
  String get teamProjectRestartElsewhere => 'البدء من جديد في مكان آخر';

  @override
  String get teamProjectRestartElsewhereBody =>
      'ابدأ محاولة جديدة على هذا الخادم. يبقى الفرع السابق على خادمه الأصلي.';

  @override
  String get teamProjectWaitForServer => 'انتظار الخادم الأصلي';

  @override
  String get teamProjectBudgetNear =>
      'تقترب من ميزانيتك. يتوقف العمل الجديد مؤقتًا عند الحد الذي اخترته.';

  @override
  String get teamProjectDemoPlanFailure => 'العرض التوضيحي: خطة غير مقروءة';

  @override
  String get teamProjectTaskReviewFindings => 'اختيار الملاحظات غير المعالجة';

  @override
  String get teamProjectTaskResolveAgent => 'المعالجة بواسطة وكيل';

  @override
  String get teamProjectTaskResolveManually => 'سأتولى المعالجة';

  @override
  String get teamProjectTaskRecheckResolution => 'إعادة التحقّق من المعالجة';

  @override
  String get teamProjectTaskVerificationResults => 'نتائج التحقّق';

  @override
  String get teamProjectTaskCriterionMet => 'مستوفى';

  @override
  String get teamProjectTaskCriterionUnmet => 'غير مستوفى';

  @override
  String get teamProjectTaskCriterionNotApplicable => 'لا ينطبق';

  @override
  String get teamProjectTaskDemoConflict => 'العرض التوضيحي: إنشاء تعارض';

  @override
  String get teamProjectTaskDemoCommit => 'العرض التوضيحي: إضافة تثبيت يدوي';

  @override
  String get teamProjectEditorNoOptions =>
      'لا توجد خيارات متاحة بعد. عد إلى AI Team لإضافة خادم أو دور.';

  @override
  String get teamProjectEditorUnknownDate => 'التاريخ غير متاح';

  @override
  String get teamProjectEditorYou => 'أنت';

  @override
  String get teamProjectEditorApprovedBy => 'وافق عليه';

  @override
  String get teamProjectEditorPlanFailed =>
      'لم يُرجع المخطط خطة قابلة للاستخدام. احتفظ بالهدف كمهمة واحدة، أو اطلب خطة جديدة.';

  @override
  String get teamProjectEditorUseAsTask => 'استخدام الهدف كمهمة واحدة';

  @override
  String get teamProjectEditorAskAgain => 'الطلب مجددًا';

  @override
  String get teamProjectDemoChip => 'عرض توضيحي';

  @override
  String teamProjectHeadlineMilestone(int current, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working قيد العمل',
      zero: '',
    );
    return 'المرحلة الرئيسية $current من $total$_temp0';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return 'اكتملت جميع المراحل الرئيسية، وعددها $total';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    return 'اعتُمدت المواصفات منذ $age · عدد المراحل الرئيسية: $milestones · عدد المستودعات: $repos';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    return 'مسودة المواصفات · عدد المراحل الرئيسية: $milestones · عدد المستودعات: $repos';
  }

  @override
  String get teamProjectOpenSpec => 'فتح المواصفات';

  @override
  String teamProjectRequestWhere(String role, String server) {
    return '$role على $server';
  }

  @override
  String teamProjectRequestBlocks(String role, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'وتنتظر مهام عددها $count بعده أيضًا',
      one: 'وتنتظر مهمة واحدة بعده أيضًا',
    );
    return 'ينتظر $role؛ $_temp0';
  }

  @override
  String teamProjectMilestoneTasks(int done, int total) {
    return 'المهام: $done من $total';
  }

  @override
  String teamProjectMilestoneWaits(int number) {
    return 'تنتظر $number';
  }

  @override
  String get teamProjectMilestoneNoTasks => 'لا توجد مهام بعد';

  @override
  String teamProjectLanesTitle(int busy, int total) {
    return 'مسارات العمل: $busy/$total مشغولة';
  }

  @override
  String get teamProjectLanesChange => 'تغيير المسارات';

  @override
  String teamProjectLaneRunning(String server, String elapsed) {
    return '$server · $elapsed';
  }

  @override
  String teamProjectLaneWaiting(String server) {
    return '$server · بانتظار مسار عمل متاح';
  }

  @override
  String teamProjectLaneTitle(String role, String task) {
    return '$role · $task';
  }

  @override
  String teamProjectLaneNoteParallel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'مسارات عددها $count',
      one: 'مسار واحد',
    );
    return 'بالتوازي · $_temp0';
  }

  @override
  String get teamProjectLaneNoteSingle => 'مسار واحد';

  @override
  String teamProjectLaneNoteDemo(String note) {
    return '$note · الأرقام ناتجة عن محاكاة';
  }

  @override
  String teamProjectElapsedSeconds(int count) {
    return '$count ث';
  }

  @override
  String teamProjectElapsedMinutes(int count) {
    return '$count د';
  }

  @override
  String teamProjectElapsedHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String teamProjectCostToday(String amount) {
    return '$amount اليوم';
  }

  @override
  String teamProjectCostTodayOf(String amount, String limit) {
    return '$amount من $limit اليوم';
  }

  @override
  String teamProjectCostTotal(String amount) {
    return '$amount إجمالًا';
  }

  @override
  String teamProjectCostTotalOf(String amount, String limit) {
    return '$amount من $limit إجمالًا';
  }

  @override
  String get teamProjectCostNotReported => 'لم يُبلّغ عنه بعد';

  @override
  String teamProjectBoardSummary(int tasks, int milestones) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: 'مهام عددها $tasks',
      one: 'مهمة واحدة',
    );
    String _temp1 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: 'مراحل رئيسية عددها $milestones',
      one: 'مرحلة رئيسية واحدة',
    );
    return '$_temp0 موزّعة على $_temp1';
  }

  @override
  String get teamProjectBoardEmpty => 'لا توجد مهام بعد';

  @override
  String teamProjectTimelineSummary(int count, String age) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أحداث عددها $count',
      one: 'حدث واحد',
    );
    return '$_temp0 · الأحدث منذ $age';
  }

  @override
  String get teamProjectTimelineEmpty => 'لم يحدث شيء بعد';

  @override
  String teamProjectServersSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'خوادم عددها $count',
      one: 'خادم واحد',
    );
    return '$_temp0';
  }

  @override
  String teamProjectSettingsSummaryParallel(int count) {
    return 'بالتوازي · الحد الأقصى للمسارات: $count';
  }

  @override
  String get teamProjectSettingsSummarySingle => 'مسار واحد';

  @override
  String get teamProjectMenu => 'قائمة المشروع';

  @override
  String get teamProjectTaskMenu => 'قائمة المهمة';

  @override
  String teamProjectDecisionBy(String who, String age) {
    return '$who · $age';
  }

  @override
  String get teamProjectYou => 'أنت';

  @override
  String teamProjectPlanFor(int number) {
    return 'خطة المرحلة الرئيسية $number · بانتظارك';
  }

  @override
  String get teamProjectPlanForProject => 'الخطة · بانتظارك';

  @override
  String teamProjectPlanSummary(int phases, int tasks, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      phases,
      locale: localeName,
      other: 'مراحل عددها $phases',
      one: 'مرحلة واحدة',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: 'مهام عددها $tasks',
      one: 'مهمة واحدة',
    );
    String _temp2 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: 'مستودعات عددها $repos',
      one: 'مستودع واحد',
    );
    return '$_temp0 · $_temp1 · $_temp2';
  }

  @override
  String teamProjectPlanPhase(int number, String title) {
    return 'المرحلة $number · $title';
  }

  @override
  String get teamProjectPlanReviewPoint => 'نقطة مراجعة · محفوفة بالمخاطر';

  @override
  String teamProjectPlanRepo(String name) {
    return 'مستودع $name';
  }

  @override
  String teamProjectPlanAfter(int number) {
    return 'بعد $number';
  }

  @override
  String teamProjectPlanCriteria(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'معايير عددها $count',
      one: 'معيار واحد',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPlanMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '… مهام إضافية عددها $count',
      one: '… مهمة إضافية واحدة',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPlanEdit => 'تعديل الخطة';

  @override
  String get teamProjectPlanAsk => 'طلب التغيير';

  @override
  String get teamProjectPlanNotYet => 'ليس الآن';

  @override
  String teamProjectPlanServerTitle(String task) {
    return 'تشغيل «$task» على';
  }

  @override
  String get teamProjectPlanServerFixed =>
      'كل مهمة تعمل على الجهاز الذي خُطط لها عليه. هذا الفريق لا يستطيع نقل المهام إلى جهاز آخر بعد.';

  @override
  String teamProjectMergeEffectDev(String repo, int tasks) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks مهام مفحوصة',
      one: 'مهمة مفحوصة واحدة',
    );
    return '$_temp0 من $repo ستُدمج في dev.';
  }

  @override
  String get teamProjectMergeEffectMain => 'لن يتغير main.';

  @override
  String teamProjectReceiptMerged(String repo) {
    return 'دُمج في dev · $repo';
  }

  @override
  String teamProjectReceiptPromoted(String repo) {
    return 'رُقّي إلى main · $repo';
  }

  @override
  String teamProjectTimelineRepeated(String text, int count) {
    return '$text · $count مرات';
  }

  @override
  String get teamProjectPromoteTitle => 'نقل dev → main';

  @override
  String teamProjectPromoteStatus(String repo) {
    return 'مستودع $repo · main محمي. أنت وحدك تستطيع نقل التغييرات إليه.';
  }

  @override
  String teamProjectPromoteMilestone(int number, String title) {
    return 'المرحلة الرئيسية $number · $title';
  }

  @override
  String teamProjectPromoteMerged(int done, int total) {
    return 'المهام المدمجة: $done من $total';
  }

  @override
  String teamProjectPromoteDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أيام عددها $count',
      one: 'يوم واحد',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteChecks => 'الفحوص بعد الدمج';

  @override
  String get teamProjectPromoteChecksPassed => 'نجحت جميع الفحوص بعد آخر دمج';

  @override
  String get teamProjectPromoteReview => 'مراجعة العمل';

  @override
  String teamProjectPromoteAccepted(int number) {
    return 'وافقت على المرحلة الرئيسية $number';
  }

  @override
  String get teamProjectPromoteNoReview => 'لم تلزم مراجعة لهذا العمل';

  @override
  String teamProjectPromoteChanges(int commits) {
    String _temp0 = intl.Intl.pluralLogic(
      commits,
      locale: localeName,
      other: 'تثبيتات عددها $commits',
      one: 'تثبيت واحد',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPromoteFiles(int files) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: 'ملفات عددها $files',
      one: 'ملف واحد',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteSeeChanges => 'عرض التغييرات';

  @override
  String get teamProjectPromoteNotYet => 'ليس بعد';

  @override
  String teamProjectFindingsTitle(String role, String summary) {
    return 'تحقّق منه $role · $summary';
  }

  @override
  String get teamProjectEditorGoalLabel => 'ما الذي تريد أن يحققه الفريق؟';

  @override
  String get teamProjectEditorWhereRuns => 'مكان التشغيل';

  @override
  String get teamProjectEditorMoreOptions => 'تفاصيل اختيارية';

  @override
  String get teamProjectEditorNameHelp =>
      'اختياري. اتركه فارغًا لاستخدام بداية الهدف.';

  @override
  String get teamProjectEditorBudgetHelp =>
      'يتوقف الفريق مؤقتًا عندما يبلغ الإنفاق اليومي أو إنفاق المشروع كله حده.';

  @override
  String teamProjectFindingsCritical(int count) {
    return 'الملاحظات الحرجة: $count';
  }

  @override
  String teamProjectFindingsMajor(int count) {
    return 'الملاحظات الكبيرة: $count';
  }

  @override
  String teamProjectFindingsMinor(int count) {
    return 'الملاحظات الصغيرة: $count';
  }

  @override
  String get phoneTeamSetupTitle => 'تشغيل فريق الذكاء الاصطناعي';

  @override
  String get phoneTeamStepReply => 'انتظر انتهاء ردّك الحالي';

  @override
  String get phoneTeamReplyWaiting => 'ينهي ردّك الحالي…';

  @override
  String get phoneTeamStepStop => 'إيقاف OpenCode وإغلاق الطرفيات';

  @override
  String get phoneTeamStepCheck => 'التحقق من أن الهاتف آمن للفريق';

  @override
  String get phoneTeamStepServer => 'تشغيل OpenCode من جديد بحماية';

  @override
  String get phoneTeamNotNeeded => 'غير مطلوب';

  @override
  String get phoneTeamStopTitle => 'إيقاف OpenCode لحظة؟';

  @override
  String get phoneTeamStopBody =>
      'يوقف خادم OpenCode على هذا الهاتف لنحو دقيقة ثم يشغّله من جديد بحماية. تُغلق الطرفيات المفتوحة.';

  @override
  String get phoneTeamStopConfirm => 'أوقف وتابع';

  @override
  String get phoneTeamStopCancel => 'ليس الآن';

  @override
  String get phoneTeamStopWaiting => 'بانتظار ردّك';

  @override
  String get phoneTeamDoneTitle => 'فريق الذكاء الاصطناعي جاهز';

  @override
  String get phoneTeamDoneBody => 'أعطه هدفًا فيخطط الفريق وينفذ ويراجع العمل.';

  @override
  String get phoneTeamFailUnsafeTitle => 'يبقى الفريق متوقفًا';

  @override
  String get phoneTeamFailUnsafeBody =>
      'لا يستطيع هذا الهاتف فصل نسخة الفريق من شيفرتك عن الوكلاء، لذلك يبقى الفريق متوقفًا.';

  @override
  String get phoneTeamFailEngineTitle => 'لم يبدأ فريق الذكاء الاصطناعي';

  @override
  String get phoneTeamFailEngineBody => 'لم يبدأ محرك الفريق على هذا الهاتف.';

  @override
  String get phoneTeamFailStopTitle => 'لم يتوقف الخادم';

  @override
  String get phoneTeamFailStopBody =>
      'لم يُغلق OpenCode أو إحدى الطرفيات، فلا يمكن إجراء الفحص بأمان بعد.';

  @override
  String get phoneTeamFailServerTitle => 'لم يعد الخادم';

  @override
  String get phoneTeamFailServerBody =>
      'اجتاز هذا الهاتف الفحص لكن OpenCode لم يبدأ من جديد. ابدأ من جديد للإنهاء.';

  @override
  String get phoneTeamFailNotReadyTitle => 'ليس جاهزًا بعد';

  @override
  String get phoneTeamFailNotReadyBody =>
      'عاد OpenCode لكن الفريق لا يستطيع بدء العمل بعد.';

  @override
  String get phoneTeamFailDeclinedTitle => 'لم يتغير شيء';

  @override
  String get phoneTeamFailDeclinedBody =>
      'يبقى OpenCode كما هو، لذلك يبقى الفريق متوقفًا. ابدأ من جديد حين تكون مستعدًا لإعادة تشغيله.';

  @override
  String get phoneTeamFailNoServerTitle => 'أضف خادمًا أولًا';

  @override
  String get phoneTeamFailNoServerBody =>
      'يعمل الفريق مع OpenCode على هذا الهاتف ولا يوجد واحد بعد.';

  @override
  String get phoneTeamReasonNotPackaged =>
      'هذه النسخة من التطبيق لا تتضمن أدوات أمان الفريق.';

  @override
  String get phoneTeamStateBackOn => 'عاد OpenCode للعمل.';

  @override
  String get phoneTeamStateStillOff =>
      'ما زال OpenCode متوقفًا. ابدأ مجددًا أو أعد تشغيله من هذا الهاتف.';

  @override
  String get phoneTeamStateNotStopped => 'لم يتم إيقاف OpenCode.';

  @override
  String get phoneTeamStateTerminalsClosed => 'أُغلقت الطرفيات المفتوحة.';

  @override
  String get phoneTeamWhyUnsafe =>
      'لم ينجح الفحص الذي يفصل نسخة الفريق من شيفرتك عن الوكلاء.';

  @override
  String get phoneTeamWhyEngine => 'توقف محرك الفريق أو لم يستجب أثناء بدئه.';

  @override
  String get phoneTeamWhyStop =>
      'لم يُغلق OpenCode أو إحدى الطرفيات عند الطلب.';

  @override
  String get phoneTeamWhyServer => 'لم يستجب OpenCode بعد تشغيله مجددًا.';

  @override
  String get phoneTeamWhyNotReady =>
      'استجاب محرك الفريق لكنه قال إنه لا يستطيع تشغيل العمل بعد.';

  @override
  String get phoneTeamOffTitle => 'فريق الذكاء الاصطناعي متوقف';

  @override
  String get phoneTeamOffBody =>
      'يحتاج إلى فحص أمان سريع، مثلًا بعد تحديث التطبيق.';

  @override
  String get phoneTeamBlocked =>
      'لا يستطيع الفريق بدء العمل قبل فحص هذا الهاتف.';

  @override
  String get phoneTeamStripChecking =>
      'فحص فريق الذكاء الاصطناعي على هذا الهاتف';

  @override
  String get phoneTeamStripWaiting => 'يحتاج الفريق إلى إعادة تشغيل OpenCode';

  @override
  String get phoneTeamStripReview => 'افتح الفحص لتختار الوقت';

  @override
  String phoneTeamStripStep(int step, int total) {
    return 'الخطوة $step من $total';
  }

  @override
  String get phoneTeamBlockedTitle => 'افحص الهاتف أولًا؟';

  @override
  String get teamMigrationTitle => 'تغيّر فريق الذكاء الاصطناعي';

  @override
  String get teamMigrationBody =>
      'الفريق الجديد يخطط لمشاريع كاملة وينفذها على هذا الهاتف. فريقك القديم يواصل المهام السريعة فقط ولا يخطط للمشاريع، والتبديل يسألك قبل إيقاف أي شيء.';

  @override
  String get teamMigrationSwitch =>
      'التبديل إلى فريق الذكاء الاصطناعي الجديد على هذا الهاتف';

  @override
  String get teamMigrationKeep => 'إبقاء الفريق القديم حاليًا';

  @override
  String get teamMigrationMenu => 'ما الجديد في فريق الذكاء الاصطناعي';

  @override
  String get teamProjectPages => 'صفحات المشروع';

  @override
  String get teamProjectMergeReady => 'جاهز للدمج';

  @override
  String get teamProjectMergeChecking => 'بانتظار فحوصاته';

  @override
  String get teamRefusalUnsupportedCommand =>
      'لا يستطيع هذا الإصدار من الفريق تنفيذ ذلك بعد.';

  @override
  String get teamRefusalUnsupportedCommandNext =>
      'حدّث التطبيق، ثم حاول مجددًا.';

  @override
  String get teamRefusalBoundaryUnverified =>
      'لا يستطيع الفريق العمل حتى يُتحقّق من حماية هذا الهاتف.';

  @override
  String get teamRefusalBoundaryUnverifiedNext => 'افتح AI Team وشغّله مجددًا.';

  @override
  String get teamRefusalProtocolUnverified =>
      'ينتظر الفريق إعادة تشغيل OpenCode.';

  @override
  String get teamRefusalProtocolUnverifiedNext => 'حاول مجددًا بعد دقيقة.';

  @override
  String get teamRefusalEngineUnavailable => 'لا يستجيب الفريق حاليًا.';

  @override
  String get teamRefusalEngineUnavailableNext => 'حاول مجددًا بعد قليل.';

  @override
  String get teamRefusalTransportUncertain => 'ربما لم يتلقّ الفريق ذلك.';

  @override
  String get teamRefusalTransportUncertainNext =>
      'تحقّق من قائمة المشاريع قبل المحاولة مجددًا.';

  @override
  String get teamRefusalBusy => 'لا يزال حفظ تغيير آخر جاريًا.';

  @override
  String get teamRefusalBusyNext => 'حاول مجددًا بعد قليل.';

  @override
  String get teamRefusalSaveFailed => 'تعذّر حفظ التغيير على هذا الهاتف.';

  @override
  String get teamRefusalSaveFailedNext => 'لا تزال تعديلاتك هنا. حاول مجددًا.';

  @override
  String get teamRefusalReadOnly => 'الفريق متاح للقراءة فقط حاليًا.';

  @override
  String get teamRefusalReadOnlyNext =>
      'شغّل AI Team على هذا الهاتف لإجراء تغييرات.';

  @override
  String get teamRefusalClosed => 'أُغلق الفريق.';

  @override
  String get teamRefusalClosedNext => 'افتح AI Team مجددًا للمتابعة.';

  @override
  String get teamRefusalCommandRefused => 'رفض الفريق هذا الطلب.';

  @override
  String get teamRefusalCommandRefusedNext =>
      'تحقّق من الهدف ومجلد المستودع، ثم حاول مجددًا.';

  @override
  String get teamRefusalImportFailed =>
      'تعذّرت قراءة مجلد المستودع على الفريق.';

  @override
  String get teamRefusalImportFailedNext =>
      'تحقّق من اسم المجلد، ثم حاول مجددًا.';

  @override
  String get teamRefusalPayloadInvalid =>
      'أجاب الفريق بصيغة لا يفهمها هذا التطبيق.';

  @override
  String get teamRefusalPayloadInvalidNext => 'حدّث التطبيق، ثم حاول مجددًا.';

  @override
  String get teamRefusalSchemaUnsupported =>
      'يستخدم الفريق وهذا التطبيق إصدارين مختلفين.';

  @override
  String get teamRefusalSchemaUnsupportedNext =>
      'حدّث التطبيق، ثم حاول مجددًا.';

  @override
  String get teamRefusalEngineClosed => 'جارٍ إيقاف الفريق.';

  @override
  String get teamRefusalEngineClosedNext => 'شغّل AI Team مجددًا للمتابعة.';

  @override
  String get teamRefusalSessionFailed => 'توقف المخطط قبل أن يجيب.';

  @override
  String get teamRefusalSessionFailedNext =>
      'افتح اختيار نموذج المخطِّط وتحقق منه ثم اطلب مرة أخرى.';

  @override
  String get teamRefusalModelNotConfigured => 'لم يقبل نموذج هذه المهمة الطلب.';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      'قد يحتاج إلى تسجيل دخول أو قد لا يكون مجانياً هنا. افتح اختيار نموذج المخطِّط وجرّب نموذجاً آخر ثم اطلب مرة أخرى.';

  @override
  String get teamRefusalModelUnavailable => 'النموذج المختار غير متاح.';

  @override
  String get teamRefusalModelUnavailableNext =>
      'اختر نموذجًا آخر في إعدادات الفريق › النموذج.';

  @override
  String get teamRefusalAuthFailed => 'لم يقبل مزوّد النموذج تسجيل الدخول.';

  @override
  String get teamRefusalAuthFailedNext =>
      'تحقّق من مفتاح مزوّد الخدمة، ثم وافق على المواصفات مجددًا.';

  @override
  String get teamRefusalCloneFailed =>
      'تعذّر على الفريق نسخ المشروع للعمل عليه.';

  @override
  String get teamRefusalCloneFailedNext =>
      'تحقّق من مستودع المشروع، ثم وافق على المواصفات مجددًا.';

  @override
  String get teamRefusalSessionUncertain =>
      'لا يعرف الفريق إلى أين وصل تشغيله الأخير.';

  @override
  String get teamRefusalSessionUncertainNext =>
      'استأنف إن أمكن، أو وافق على المواصفات مجددًا.';

  @override
  String get teamRefusalPlanInvalid => 'تعذّر استخدام الخطة التي أُرجعت.';

  @override
  String get teamRefusalPlanInvalidNext =>
      'وافق على المواصفات مجددًا ليعيد الفريق التخطيط.';

  @override
  String get teamRefusalNeedsAnswer => 'لدى المخطط سؤال لك.';

  @override
  String get teamRefusalNeedsAnswerNext => 'افتح المواصفات وأجب عنه.';

  @override
  String get teamRefusalRecoveryReview =>
      'توقف العمل قبل اكتماله ويحتاج إلى مراجعة.';

  @override
  String get teamRefusalRecoveryReviewNext =>
      'تحقّق من المشروع، ثم وافق على المواصفات مجددًا.';

  @override
  String get teamRefusalAppStopped => 'أُغلق التطبيق قبل انتهاء الفريق.';

  @override
  String get teamRefusalAppStoppedNext => 'استأنف للتحقّق من موضع توقفه.';

  @override
  String get teamRefusalChatBusy => 'ينتظر الفريق اكتمال الرد في محادثتك.';

  @override
  String get teamRefusalChatBusyNext => 'يواصل العمل تلقائيًا بعد ذلك.';

  @override
  String get teamRefusalBudgetReached => 'بلغ المشروع حد الإنفاق.';

  @override
  String get teamRefusalBudgetReachedNext =>
      'ارفع الحد في إعدادات المشروع للمتابعة.';

  @override
  String get teamRefusalModelNotConfiguredAction => 'اختيار نموذج المخطِّط';

  @override
  String get teamProjectPlanFailedTitle => 'لم تُنشأ الخطة';

  @override
  String get teamProjectApproveAgain => 'الموافقة على المواصفات مجددًا';

  @override
  String get teamProjectApproveAgainNote =>
      'وافق على المواصفات مجددًا لإعادة المحاولة.';

  @override
  String get teamProjectTaskWorkLive => 'العمل حتى الآن';

  @override
  String get teamProjectTaskWorkLog => 'سجل العمل';

  @override
  String get teamRefusalDidPlan => 'بدء التخطيط';

  @override
  String get teamRefusalDidQuick => 'بدء تلك المهمة';

  @override
  String get teamRefusalDidApprove => 'بدء العمل';

  @override
  String get teamRefusalDidSpec => 'اعتماد المواصفات';

  @override
  String get teamRefusalDidPromote => 'نقل العمل';

  @override
  String get teamRefusalDidStop => 'إيقاف المشروع';

  @override
  String get teamRefusalDidPause => 'إيقاف المشروع مؤقتًا';

  @override
  String get teamRefusalDidResume => 'استئناف المشروع';

  @override
  String get teamRefusalDidSave => 'حفظ تغييراتك';

  @override
  String teamRefusalUnknown(String action) {
    return 'تعذّر على الفريق $action.';
  }

  @override
  String get teamRefusalUnknownNext =>
      'حاول مجددًا. إذا تكرر ذلك، افتح التفاصيل لرؤية الرمز.';

  @override
  String get teamRefusalCode => 'الرمز';

  @override
  String get phoneTeamProtectedProot =>
      'محمي ببيئة Linux المعزولة على هذا الهاتف';

  @override
  String get phoneTeamProtectedLandlock => 'محمي بحماية الملفات في Android';

  @override
  String get teamRefusalRepositoryEmpty =>
      'لا يحتوي هذا المستودع على أي تثبيتات بعد.';

  @override
  String get teamRefusalRepositoryEmptyNext =>
      'أنشئ أول تثبيت فيه، ثم ابدأ التخطيط مجددًا.';

  @override
  String get teamRefusalRepositoryLink =>
      'تعذّر على الفريق نسخ هذا المستودع بأمان.';

  @override
  String get teamRefusalRepositoryLinkNext =>
      'حاول مجددًا. إذا تكرر ذلك، أبلغ عن المشكلة.';

  @override
  String get teamRefusalRepositoryDamaged => 'لم تطابق نسخة المستودع الأصل.';

  @override
  String get teamRefusalRepositoryDamagedNext =>
      'حاول مجددًا. إذا تكرر ذلك، أبلغ عن المشكلة.';

  @override
  String get teamRefusalPlanTaskName => 'إحدى المهام في الخطة بلا اسم.';

  @override
  String get teamRefusalPlanTaskNameNext => 'سمِّ كل مهمة، ثم وافق مجددًا.';

  @override
  String get teamRefusalPlanPhase => 'إحدى مراحل الخطة غير مكتملة.';

  @override
  String get teamRefusalPlanPhaseNext =>
      'تحقّق من أن لكل مرحلة مهام ومعايير، ثم وافق مجددًا.';

  @override
  String get teamServerPhoneFailed =>
      'لا يستجيب AI Team على هذا الهاتف، لذا يتعذّر الوصول إلى عمله.';

  @override
  String get teamServerPhoneNotReady =>
      'AI Team على هذا الهاتف غير جاهز: لم ينجح فحص السلامة الخاص به.';

  @override
  String get teamServerPhoneNoAnswer =>
      'لم يستجب OpenCode على هذا الهاتف للفحص الأخير.';

  @override
  String get phoneTeamOffReview => 'راجع واختر الوقت';

  @override
  String teamProjectInterruptedRow(String name) {
    return '$name: توقف العمل، اضغط للاستئناف';
  }

  @override
  String get teamProjectResumeUnavailable =>
      'هذا الإصدار من فريق الذكاء الاصطناعي لا يستطيع استئناف العمل المتوقف بعد. يمكنك إيقاف المشروع وبدءه من جديد.';

  @override
  String get teamProjectInterrupted => 'توقف العمل، جاهز للاستئناف';

  @override
  String get teamProjectEditorContextFilesHelpReal =>
      'اختياري. مسار واحد في كل سطر. يقرأ الفريق هذه الملفات قبل أن يخطط.';

  @override
  String get addServerCleartextWarning =>
      'يستخدم عنوان الشبكة الخاصة أو Tailscale هذا HTTP غير المشفّر. تنتقل كلمة المرور والشيفرة دون تشفير في طبقة HTTP. استخدمه فقط على شبكة خاصة تثق بها أو عبر Tailscale؛ فهو خطر على شبكات Wi-Fi العامة.';

  @override
  String get addServerCleartextConfirm => 'استخدمه على أي حال';

  @override
  String get addServerCleartextConfirmed =>
      'تم تفعيل HTTP العادي لهذا العنوان. يمكن لأي شخص على هذه الشبكة قراءة ما ترسله.';

  @override
  String get storageAccessTitle => 'السماح بالوصول إلى الملفات؟';

  @override
  String get storageAccessBody =>
      'هذا المجلد في التخزين المشترك بهاتفك. يخفي أندرويد ملفاته عن التطبيقات ما لم تسمح بالوصول إلى كل الملفات.';

  @override
  String get storageAccessWhyScope =>
      'يقرأ التطبيق الملفات ويعدلها فقط في المجلدات التي تفتحها كمشاريع.';

  @override
  String get storageAccessWhyAgent =>
      'يحتاجه الوكيل ليعمل في مجلدك مباشرة. بدونه سترى العناصر المخفية فقط مثل .git.';

  @override
  String get storageAccessWhyOff =>
      'يمكنك إيقافه في أي وقت من إعدادات أندرويد، ضمن الوصول إلى كل الملفات.';

  @override
  String get storageAccessAllow => 'السماح بالوصول إلى الملفات';

  @override
  String get storageAccessNotNow => 'ليس الآن';

  @override
  String get storageAccessUseAppSpace => 'استخدام مساحة مشاريع التطبيق';

  @override
  String get storageAccessRefusedTitle => 'لم يُفتح المجلد';

  @override
  String get storageAccessRefusedBody =>
      'بدون الوصول إلى الملفات لا يمكن عرض ملفات هذا المجلد، لذلك لم يُفتح. اسمح بالوصول أو اختر مجلدًا في مساحة مشاريع التطبيق.';

  @override
  String get storageTermuxTitle => 'السماح بتخزين Termux؟';

  @override
  String get storageTermuxBody =>
      'خادمك يعمل في Termux، ولا يستطيع Termux قراءة التخزين المشترك بهاتفك بعد. بدون ذلك يعرض هذا المجلد العناصر المخفية فقط مثل .git.';

  @override
  String get storageTermuxAllow => 'فتح Termux';

  @override
  String get storageTermuxStillTitle => 'اسمح بالتخزين في Termux';

  @override
  String get storageTermuxStillBody =>
      'في Termux، اسمح بالتخزين عندما يسأل أندرويد (الأمر هو termux-setup-storage)، ثم افتح المجلد مرة أخرى.';

  @override
  String get filesAccessNeededTitle => 'الملفات مخفية';

  @override
  String get filesAccessNeededBody =>
      'هذا المجلد في التخزين المشترك بهاتفك ولا يستطيع التطبيق قراءة معظم ملفاته بعد. تظهر العناصر المخفية فقط.';

  @override
  String get filesAccessNeededTermuxBody =>
      'هذا المجلد في التخزين المشترك بهاتفك ولا يستطيع Termux قراءة معظم ملفاته بعد. تظهر العناصر المخفية فقط.';

  @override
  String get storageRestartTitle => 'إعادة التشغيل لفتح المجلد؟';

  @override
  String get storageRestartBody =>
      'يجب إعادة تشغيل OpenCode على هذا الهاتف قبل أن يرى هذا المجلد. يستغرق ذلك نحو 30 ثانية.';

  @override
  String get storageRestartPause =>
      'الردود الجارية وعمل فريق الذكاء الاصطناعي يتوقفان مؤقتًا ثم يتابعان.';

  @override
  String get storageRestartBusy =>
      'هناك رد أو مهمة لفريق الذكاء الاصطناعي قيد التنفيذ الآن. ستتوقف مؤقتًا أثناء إعادة التشغيل.';

  @override
  String get storageRestartConfirm => 'أعد التشغيل وافتح';

  @override
  String get storageRestartFailedTitle => 'لم تكتمل إعادة التشغيل';

  @override
  String get storageRestartFailedBody =>
      'لم يُفتح المجلد. حاول مرة أخرى، أو شغّل OpenCode من هذا الهاتف.';

  @override
  String get kitQueuedChecking => 'جارٍ التحقق من وصول رسالتك…';

  @override
  String get kitQueuedUncertain => 'لم نتمكن من التأكد من وصول رسالتك.';

  @override
  String kitQueuedLastChecked(String time) {
    return 'آخر تحقق $time.';
  }

  @override
  String get kitQueuedStorageFull =>
      'لا يستطيع هذا الهاتف تسجيل الإرسال بأمان الآن، لذا لم تُرسل رسالتك. وهي محفوظة هنا.';

  @override
  String get queuedReceiptConfirmed => 'وصلت الرسالة.';

  @override
  String get queuedReceiptStillUncertain =>
      'ما زلنا لا نستطيع التأكد من وصولها. لم يُرسل شيء مرة أخرى.';

  @override
  String get queuedReceiptStorageProblem =>
      'لا يستطيع هذا الهاتف تسجيل الإرسال أو التحقق منه بأمان الآن. حرّر بعض مساحة التخزين وحاول مرة أخرى.';

  @override
  String get queuedReceiptDetailSentAt => 'وقت الإرسال';

  @override
  String get queuedReceiptDetailCommand => 'معرّف الأمر';

  @override
  String get queuedReceiptDetailReceipt => 'معرّف الإيصال';

  @override
  String get queuedReceiptDetailNote =>
      'انقطع الاتصال قبل أن نتمكن من تأكيد الاستلام.';

  @override
  String get kitToolRetry => 'أعد المحاولة';

  @override
  String get folderBrowserPlaceLabel => 'المكان';

  @override
  String get folderBrowserPlaceProjects => 'مساحة المشاريع';

  @override
  String get folderBrowserPlacePhone => 'هذا الهاتف';

  @override
  String get folderBrowserInternalStorage => 'التخزين الداخلي';

  @override
  String get folderBrowserOpenedBefore => 'فُتحت سابقًا';

  @override
  String get folderBrowserShowHidden => 'إظهار المجلدات المخفية';

  @override
  String get folderBrowserHintEmpty => 'فارغ';

  @override
  String folderBrowserHintMore(String names, int count) {
    return '$names، و$count أخرى';
  }

  @override
  String get folderBrowserEmptyPhoneBody => 'افتحه، أو اصعد مستوى واحدًا.';

  @override
  String folderBrowserNewProjectCreates(String path) {
    return 'ينشئ $path';
  }

  @override
  String get folderBrowserCreateAndOpen => 'أنشئ وافتح';

  @override
  String get folderBrowserFoldersLabel => 'المجلدات';

  @override
  String get folderBrowserPhoneRefused =>
      'تخزين الهاتف متوقف. اسمح بالوصول لتصفحه هنا.';

  @override
  String get phoneScanTitle => 'المشاريع على هذا الهاتف';

  @override
  String phoneScanStopped(int seconds, int count) {
    return 'توقف بعد $seconds ثانية · وُجد $count';
  }

  @override
  String phoneScanFirstShown(int count) {
    return 'أول $count معروضة';
  }

  @override
  String get phoneScanLookDeeper => 'ابحث أعمق';

  @override
  String get phoneScanEmptyTitle => 'لم يُعثر على مشاريع';

  @override
  String get phoneScanEmptyBody => 'تصفح المجلدات أو ابدأ مشروعًا جديدًا.';

  @override
  String get phoneScanGit => 'Git';

  @override
  String get phoneScanKindDart => 'Dart';

  @override
  String get phoneScanKindNode => 'Node';

  @override
  String get phoneScanKindPython => 'Python';

  @override
  String get phoneScanKindRust => 'Rust';

  @override
  String get phoneScanKindGo => 'Go';

  @override
  String get phoneScanKindJava => 'Java';

  @override
  String get phoneScanKindRuby => 'Ruby';

  @override
  String get phoneScanKindPhp => 'PHP';

  @override
  String get phoneScanKindDotnet => '.NET';

  @override
  String get phoneScanKindCpp => 'C/C++';

  @override
  String get phoneScanKindGit => 'مجلد';

  @override
  String get openProjectTitle => 'افتح مشروعًا';

  @override
  String get openProjectSearchPhone => 'ابحث في هذا الهاتف';

  @override
  String get openProjectChooseFolder => 'اختر مجلدًا';

  @override
  String get openProjectChangeFolder => 'غيّر المجلد';

  @override
  String openProjectUseFolder(String name) {
    return 'استخدم $name';
  }

  @override
  String get phoneScanSearching => 'جارٍ البحث في هذا الهاتف';

  @override
  String phoneScanChecked(int folders, int found) {
    String _temp0 = intl.Intl.pluralLogic(
      folders,
      locale: localeName,
      other: '$folders مجلد',
      many: '$folders مجلدًا',
      few: '$folders مجلدات',
      two: 'مجلدان',
      one: 'مجلد واحد',
    );
    return 'فُحص $_temp0 · وُجد $found';
  }

  @override
  String phoneScanDoneIn(int count, String time) {
    return 'وُجد $count خلال $time';
  }

  @override
  String get phoneScanStop => 'إيقاف';

  @override
  String get openProjectChange => 'غيّر';

  @override
  String openProjectIn(String folder) {
    return 'في $folder';
  }

  @override
  String get effectsGlowStyle => 'النمط';

  @override
  String get effectsGlowStyleClassic => 'كلاسيكي';

  @override
  String get effectsGlowStyleSoft => 'حلقة ناعمة';

  @override
  String get effectsGlowColours => 'الألوان';

  @override
  String get effectsGlowSpeed => 'السرعة';

  @override
  String get approvalModeAskDetail => 'أنت تجيب عن كل طلب.';

  @override
  String get approvalModeConfirmEverythingBody =>
      'سيشغّل الوكلاء على هذا الخادم الأوامر ويغيّرون الملفات دون سؤالك، في كل محادثة.';

  @override
  String get approvalsSheetSubagents => 'الوكلاء الفرعيون يتبعون هذا';

  @override
  String approvalsSheetSetByParent(String name) {
    return 'محدد من $name';
  }

  @override
  String get approvalsSheetSetByServer => 'محدد من هذا الخادم';

  @override
  String get approvalsSheetFootnote =>
      'تتوقف عند انقطاع اتصال التطبيق. وتبقى قواعد الرفض في الخادم سارية.';

  @override
  String get approvalsSheetHistory => 'تمت الموافقة تلقائيًا';

  @override
  String get chatsHomeTitle => 'المحادثات';

  @override
  String get chatsHomeAllProjects => 'كل المشاريع';

  @override
  String get chatsHomeNeedsYou => 'بانتظارك';

  @override
  String get chatsHomeRunning => 'قيد العمل';

  @override
  String get chatsHomeRowWorking => 'قيد العمل';

  @override
  String get chatsHomeStopped => 'أُوقف';

  @override
  String get chatsHomeDone => 'تم';

  @override
  String get chatsHomeFailed => 'فشلت';

  @override
  String get chatsHomeToday => 'اليوم';

  @override
  String get chatsHomeEarlier => 'سابقًا';

  @override
  String chatsHomeOnlyProject(String project) {
    return 'تُعرض المحادثات في $project فقط. لا يستطيع هذا الخادم سرد كل المشاريع.';
  }

  @override
  String get chatsHomeIncomplete => 'تعذر تحميل بعض المحادثات';

  @override
  String chatsHomeIncompleteNamed(String servers) {
    return 'تعذر تحميل محادثات $servers';
  }

  @override
  String shellServerPlusOthers(String name, int count) {
    return '$name +$count';
  }

  @override
  String get chatSubagentReadOnly =>
      'هذا الوكيل الفرعي يجيب محادثته الرئيسية فقط. اكتب هناك.';

  @override
  String get chatsHomeOpening => 'جارٍ الفتح…';

  @override
  String chatsHomeUnreachable(String servers) {
    return '$servers لا يستجيب. محادثاته غير معروضة.';
  }

  @override
  String get chatsSourcesUnreachable => 'لا يستجيب';

  @override
  String get chatsSourcesAll => 'كل الاتصالات';

  @override
  String chatsSourcesSome(int shown, int total) {
    return '$shown من $total اتصالات';
  }

  @override
  String get chatsSourcesSheetTitle => 'الاتصالات في هذه القائمة';

  @override
  String get chatsSourcesMain => 'المحادثات الجديدة تبدأ هنا';

  @override
  String get chatsSourcesOnPhone => 'على هذا الهاتف';

  @override
  String get chatsSourcesElsewhere => 'على جهاز كمبيوتر آخر';

  @override
  String get chatsSourcesAgents => 'الوكلاء على هذا الهاتف';

  @override
  String get chatsSourcesAlways => 'معروض دائمًا';

  @override
  String chatsHomeStillLoading(String agents) {
    return 'جارٍ تحميل محادثات $agents…';
  }

  @override
  String get chatsHomeEmptyTitle => 'لا توجد محادثات بعد';

  @override
  String get chatsHomeNoMatchTitle => 'لا توجد محادثات مطابقة';

  @override
  String get chatsHomeNoMatchBody => 'لا شيء يطابق المرشحات التي اخترتها.';

  @override
  String get chatsHomeClearFilters => 'مسح المرشحات';

  @override
  String chatsHomeProjectEmptyTitle(String project) {
    return 'لا توجد محادثات في $project';
  }

  @override
  String chatsHomeStartChatIn(String project) {
    return 'ابدأ محادثة في $project';
  }

  @override
  String get chatsHomeNewChat => 'محادثة جديدة';

  @override
  String get chatsHomeOpenFailed =>
      'تعذر فتح هذه المحادثة. اسحب للأسفل لتحديث القائمة.';

  @override
  String get chatsFilterSheetTitle => 'عرض محادثات من';

  @override
  String chatsFilterChatCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count محادثات',
      one: 'محادثة واحدة',
    );
    return '$_temp0';
  }

  @override
  String chatsFilterRunningCount(int running) {
    return '$running قيد العمل';
  }

  @override
  String get chatsFilterNeedsYouWord => 'بانتظارك';

  @override
  String get chatsFilterOpenProject => 'فتح مشروع…';

  @override
  String get chatsNewSeparateCopy => 'في نسخة منفصلة';

  @override
  String get agentPersonaClaudeCodeStrength => 'حذر مع التغييرات الكبيرة';

  @override
  String get agentPersonaClaudeCodeAbout =>
      'يخطط قبل أن يعدّل؛ مناسب للتغييرات الأكبر عبر الملفات.';

  @override
  String get agentPersonaClaudeCodeNeeds => 'خطة Claude أو مفتاح Anthropic';

  @override
  String get agentPersonaCodexStrength => 'تعديلات ومراجعات سريعة';

  @override
  String get agentPersonaCodexAbout =>
      'سريع في الإصلاحات المحددة ومراجعة التغييرات.';

  @override
  String get agentPersonaCodexNeeds => 'خطة ChatGPT أو مفتاح OpenAI';

  @override
  String get agentPersonaGeminiStrength => 'يقرأ الكثير دفعة واحدة';

  @override
  String get agentPersonaGeminiAbout =>
      'يتعامل جيدًا مع الملفات الطويلة والأسئلة الواسعة.';

  @override
  String get agentPersonaGeminiNeeds => 'حساب Google أو مفتاح Gemini';

  @override
  String get agentPersonaKiloStrength => 'تختار المزوّد بنفسك';

  @override
  String get agentPersonaKiloAbout => 'وكيل واحد يعمل مع مزوّدي نماذج كثيرين.';

  @override
  String get agentPersonaKiloNeeds => 'مفتاح من أي مزوّد مدعوم';

  @override
  String get agentPersonaClineStrength => 'خطوة بخطوة ويسأل أولًا';

  @override
  String get agentPersonaClineAbout => 'يعمل بخطوات صغيرة ويراجعك أثناء العمل.';

  @override
  String get agentPersonaClineNeeds => 'مفتاح من أي مزوّد مدعوم';

  @override
  String get agentPersonaOpenCodeStrength => 'أي نموذج، وبعضها مجاني';

  @override
  String get agentPersonaOpenCodeAbout =>
      'يعمل مع أي مزوّد، وفيه نماذج مجانية للبدء.';

  @override
  String get agentPersonaOpenCodeNeeds =>
      'لا شيء للبدء؛ أضف مفتاحًا لنماذج أكثر';

  @override
  String get agentPersonaOtherName => 'وكيل آخر';

  @override
  String get agentMakerAnthropic => 'من Anthropic';

  @override
  String get agentMakerOpenAI => 'من OpenAI';

  @override
  String get agentMakerGoogle => 'من Google';

  @override
  String get agentMakerOpenSource => 'مفتوح المصدر';

  @override
  String chatsNewStartWith(String agent) {
    return 'ابدأ مع $agent';
  }

  @override
  String get chatsNewSetUp => 'إعداد';

  @override
  String get chatsNewPrompt => 'على ماذا نعمل؟';

  @override
  String get chatsNewChooseProject => 'اختر مشروعًا';

  @override
  String get chatsNewNeedProject => 'اختر مشروعًا لبدء محادثة.';

  @override
  String get chatsNewFailed => 'لم تبدأ المحادثة. رسالتك ما زالت هنا.';

  @override
  String get chatsNewFailedTitle => 'لم تبدأ المحادثة';

  @override
  String get chatsNewFailedReportHint =>
      'يتضمّن «الإبلاغ عن مشكلة» هذه التفاصيل.';

  @override
  String get shellTabChats => 'المحادثات';

  @override
  String get shellTabFiles => 'الملفات';

  @override
  String get discoverChatsAliases =>
      'chats conversations sessions work home recent pinned inbox activity needs you approvals permissions questions forms المحادثات العمل الرئيسية الأخيرة المثبتة محادثة جديدة الوارد النشاط بانتظارك موافقات أذونات أسئلة نماذج قيد التشغيل منتهية';

  @override
  String chatProjectChipSemantics(String name) {
    return 'المشروع $name. يفتح قائمة بملفاته وطرفيته وتغييراته.';
  }

  @override
  String chatProjectServerChipSemantics(String name, String server) {
    return 'المشروع $name على $server. يفتح قائمة بملفاته وطرفيته وتغييراته.';
  }

  @override
  String get chatProjectMenuLabel => 'أدوات المشروع';

  @override
  String get agentsChooseTitle => 'اختر وكيلًا';

  @override
  String get agentsStateReady => 'جاهز';

  @override
  String get agentsStateCantReopen => 'لا يمكنه إعادة فتح المحادثات القديمة';

  @override
  String agentsStateNotInstalled(String size) {
    return 'غير مثبّت · $size';
  }

  @override
  String get agentsStateNotInstalledNoSize => 'غير مثبّت';

  @override
  String get agentsStateSignInNeeded => 'يلزم تسجيل الدخول';

  @override
  String get agentsStateChecking => 'جارٍ التحقق من تسجيل الدخول…';

  @override
  String get agentsStatePhoneCheck => 'يلزم فحص الهاتف';

  @override
  String get agentsStateStopped => 'توقف في الخلفية';

  @override
  String get agentsStateLimit => 'بلغت حد الخطة';

  @override
  String get agentsStateUnavailable => 'غير متاح على هذا الهاتف بعد';

  @override
  String agentsSetupTitle(String agent, String size) {
    return 'إعداد $agent · $size';
  }

  @override
  String agentsSetupTitleNoSize(String agent) {
    return 'إعداد $agent';
  }

  @override
  String agentsSetupBody(String agent) {
    return 'يعمل $agent على هذا الهاتف. يحمّل التثبيت الوكيل وما يحتاجه.';
  }

  @override
  String agentsSetupSizeNote(String size) {
    return '$size هو حجم الوكيل نفسه. قد تضيف الأجزاء المشتركة المزيد.';
  }

  @override
  String agentsInstallAction(String agent) {
    return 'تثبيت $agent';
  }

  @override
  String get agentsCancelSetup => 'إلغاء الإعداد';

  @override
  String agentsInstalling(String agent) {
    return 'جارٍ تثبيت $agent…';
  }

  @override
  String get agentsSetupInterrupted =>
      'توقف الإعداد قبل أن ينتهي. ثبّت مرة أخرى للمتابعة.';

  @override
  String get agentsSetupFailed => 'لم ينتهِ الإعداد. ثبّت مرة أخرى للمحاولة.';

  @override
  String get agentsCheckTitle => 'فحص هذا الهاتف';

  @override
  String agentsCheckRunning(String agent) {
    return 'جارٍ فحص $agent…';
  }

  @override
  String agentsCheckPassed(String agent) {
    return '$agent جاهز على هذا الهاتف.';
  }

  @override
  String agentsCheckFailed(String agent) {
    return 'لم يجتز $agent الفحص.';
  }

  @override
  String agentsCheckAction(String agent) {
    return 'فحص $agent';
  }

  @override
  String get agentsStepInstall => 'مثبّت';

  @override
  String get agentsStepVersion => 'الإصدار';

  @override
  String get agentsStepConnection => 'الاتصال';

  @override
  String get agentsStepReady => 'جاهز';

  @override
  String get agentsFailUnavailable =>
      'لا يستطيع هذا الهاتف تشغيل هذا الوكيل بعد. افتح التفاصيل لمعرفة السبب.';

  @override
  String agentsResumeFailed(String agent) {
    return 'لم يبدأ $agent من جديد. اضغط استئناف للمحاولة مرة أخرى. إن لم يبدأ بعدها، شغّل فحص الهاتف له.';
  }

  @override
  String get agentsFailStorage =>
      'تعذر حفظ هذا الإعداد. حرّر بعض المساحة وشغّله مرة أخرى.';

  @override
  String get agentsFailInstall => 'لم يُثبّت الوكيل. ثبّته مرة أخرى.';

  @override
  String get agentsFailInterrupted =>
      'توقف الإعداد قبل أن ينتهي. ثبّته مرة أخرى.';

  @override
  String get agentsFailVersion => 'لم يجتز الوكيل المثبّت فحص الإصدار.';

  @override
  String get agentsFailDaemon => 'لم يبدأ اتصال الوكيل.';

  @override
  String get agentsFailHello =>
      'لم يؤكد Paseo الاتصال. أعد المحاولة لإعادة تشغيله والتحقق مجددًا.';

  @override
  String get agentsFailArchitecture => 'هذا الوكيل لا يطابق معالج هذا الهاتف.';

  @override
  String get agentsFailStale => 'هذا الفحص قديم. شغّله مرة أخرى.';

  @override
  String get agentsFailBusy => 'إعداد آخر قيد التشغيل. انتظر حتى ينتهي.';

  @override
  String agentsSignInTitle(String agent) {
    return 'تسجيل الدخول إلى $agent';
  }

  @override
  String agentsSignInIntro(String agent) {
    return 'يسجّل $agent الدخول عبر خطواته الخاصة، بحسابك أنت في Claude وفق شروط Anthropic. لا يرى التطبيق بيانات تسجيل دخولك.';
  }

  @override
  String agentsSignInTerminalIntro(String agent) {
    return 'يفتح $agent صفحة تسجيل الدخول. سجّل الدخول هناك بحسابك في Claude ثم عُد. إذا طلب $agent رمزًا، فانسخه من الصفحة واختر لصق.';
  }

  @override
  String agentsSignInIntroOther(String agent) {
    return 'يسجّل $agent الدخول عبر خطواته الخاصة، بحسابك أنت لدى مزوّده. لا يرى التطبيق بيانات تسجيل دخولك.';
  }

  @override
  String agentsSignInTerminalIntroOther(String agent) {
    return 'يسجّل $agent الدخول هنا عبر خطواته الخاصة. إذا فتح صفحة، فسجّل الدخول هناك ثم عُد. إذا طلب رمزًا أو مفتاحًا، فاختر لصق.';
  }

  @override
  String get agentsSignInAgain => 'تسجيل الدخول مرة أخرى';

  @override
  String agentsSignInTerminalNotYet(String agent) {
    return 'لم يُسجَّل الدخول إلى $agent بعد. ابدأ تسجيل الدخول مرة أخرى عندما تكون مستعدًا.';
  }

  @override
  String get agentsSignInStopFailed =>
      'تعذّر إيقاف تسجيل الدخول. أبقِ التطبيق مفتوحًا وحاول مرة أخرى.';

  @override
  String get agentsSignInChecking => 'جارٍ التحقق من تسجيل الدخول…';

  @override
  String agentsSignInStart(String agent) {
    return 'تسجيل الدخول إلى $agent';
  }

  @override
  String get agentsSignedIn => 'تم تسجيل الدخول';

  @override
  String agentsSignedInBody(String agent) {
    return '$agent جاهز لمحادثات جديدة.';
  }

  @override
  String agentsSignInDone(String agent) {
    return 'استخدام $agent';
  }

  @override
  String agentsSignedInAs(String account) {
    return 'تم تسجيل الدخول باسم $account';
  }

  @override
  String agentsSignOutAction(String agent) {
    return 'تسجيل الخروج من $agent';
  }

  @override
  String agentsSignOutTitle(String agent) {
    return 'تسجيل الخروج من $agent؟';
  }

  @override
  String agentsSignOutBody(String agent) {
    return 'لن يتمكن $agent من بدء محادثات جديدة حتى تسجّل الدخول مرة أخرى.';
  }

  @override
  String agentsSignOutKept(String agent) {
    return 'تبقى محادثاتك مع $agent كما هي.';
  }

  @override
  String agentsSignOutFailed(String agent) {
    return 'تعذّر التأكد من تسجيل الخروج من $agent. حاول مرة أخرى.';
  }

  @override
  String agentsRemoveAction(String agent) {
    return 'إزالة $agent';
  }

  @override
  String agentsRemoveTitle(String agent) {
    return 'إزالة $agent؟';
  }

  @override
  String get agentsRemoveBody =>
      'تزيل هذه الخطوة الوكيل المثبّت من هذا الهاتف. تبقى حساباتك ومحادثاتك، ويمكنك تثبيته مرة أخرى.';

  @override
  String agentsRemoving(String agent) {
    return 'جارٍ إزالة $agent…';
  }

  @override
  String agentsRemoved(String agent, String size) {
    return 'تمت إزالة $agent. تم تحرير $size.';
  }

  @override
  String agentsAlreadyRemoved(String agent) {
    return 'تمت إزالة $agent بالفعل.';
  }

  @override
  String get agentsRemoveUnsupported => 'لا يمكن إزالة هذا الوكيل هنا.';

  @override
  String get agentsRemoveBusy =>
      'هذا الوكيل قيد الاستخدام. أنهِ عمله ثم حاول مرة أخرى.';

  @override
  String get agentsRemoveUnconfirmed =>
      'تعذّر التأكد من إزالة هذا الوكيل. تحقق من هذا الهاتف وحاول مرة أخرى.';

  @override
  String get agentsDone => 'تم';

  @override
  String get agentsModelTitle => 'اختر نموذجًا';

  @override
  String get agentsModelDefault => 'النموذج الافتراضي';

  @override
  String get agentsModelLoading => 'جارٍ قراءة النماذج…';

  @override
  String get agentsSignInBadCode =>
      'هذا الرمز غير مكتمل. انسخ الرمز كاملًا من المتصفح (في وسطه علامة #) والصقه مرة أخرى.';

  @override
  String get agentsSignInFailed => 'لم يكتمل تسجيل الدخول. ابدأه مرة أخرى.';

  @override
  String agentsSignInRejected(String agent) {
    return 'لم يقبل $agent هذا الرمز. ربما انتهت صلاحيته أو استُخدم من قبل. اضغط «الحصول على رمز جديد».';
  }

  @override
  String get agentsSignInHostDown => 'الوكيل لا يعمل. استأنفه ثم سجّل الدخول.';

  @override
  String get agentsSignInUnavailable =>
      'تسجيل الدخول غير جاهز على هذا الهاتف بعد';

  @override
  String agentsSignInLimit(String agent) {
    return 'بلغت خطة $agent حدها · حاول لاحقًا';
  }

  @override
  String agentsLimitReset(String agent, String time) {
    return 'بلغت خطة $agent حدها · تتجدد $time';
  }

  @override
  String agentsLimitUnknown(String agent) {
    return 'بلغت خطة $agent حدها · حاول لاحقًا';
  }

  @override
  String agentsSignedOutLine(String agent) {
    return 'تم تسجيل الخروج من $agent';
  }

  @override
  String agentsSignInAction(String agent) {
    return 'تسجيل الدخول إلى $agent';
  }

  @override
  String agentsStoppedLine(String agent) {
    return 'توقف $agent في الخلفية';
  }

  @override
  String agentsResumeAction(String agent) {
    return 'استئناف $agent';
  }

  @override
  String get agentsResumeNoticeTitle => 'بدء محادثة جديدة؟';

  @override
  String agentsResumeNoticeBody(String agent) {
    return 'تبدأ محادثة جديدة. لا يستطيع $agent إعادة فتح هذه المحادثة وتبقى كما هي.';
  }

  @override
  String get agentsStartNew => 'بدء محادثة جديدة';

  @override
  String get agentsCancel => 'إلغاء';

  @override
  String get agentsRestartLine =>
      'أغلق التطبيق وافتحه مرة أخرى لإنهاء مسح عمليات تسجيل الدخول.';

  @override
  String get agentsRestartAction => 'إغلاق التطبيق';

  @override
  String get agentsSectionTitle => 'الوكلاء';

  @override
  String get agentsRunOnBuiltIn => 'يعمل الوكلاء على الخادم المدمج';

  @override
  String get agentsSwitchToBuiltIn => 'التبديل إلى الخادم المدمج';

  @override
  String get agentsChecking => 'جارٍ البحث عن الوكلاء على هذا الهاتف…';

  @override
  String get agentsStateNeedsArm => 'يلزم هاتف 64 بت';

  @override
  String get agentsStateNoDownload => 'لا يوجد تنزيل موثّق بعد';

  @override
  String get agentsInstallHint => 'تثبيت';

  @override
  String get agentsActionFailed =>
      'لم ينجح ذلك على هذا الهاتف. افتح التفاصيل لمعرفة السبب.';

  @override
  String get chatsHomeOtherFolders => 'مجلدات أخرى';

  @override
  String get agentsSignInPaste => 'لصق';

  @override
  String get chatRequestAnswerAllowed => 'تم السماح';

  @override
  String get chatRequestAnswerRejected => 'تم الرفض';

  @override
  String agentCardAsks(String agent) {
    return '$agent يسأل';
  }

  @override
  String agentCardReports(String agent) {
    return '$agent يقدّم تقريرًا';
  }

  @override
  String agentCardAsksAnnouncement(String agent, String title) {
    return '$agent يسأل: $title';
  }

  @override
  String get agentCardSend => 'إرسال';

  @override
  String get agentCardConfirmDefault => 'نعم، تابع';

  @override
  String get agentCardCancelDefault => 'إلغاء';

  @override
  String get agentCardChoosePrompt => 'اختر خيارًا لإرساله.';

  @override
  String get agentCardChooseOneOrMore =>
      'اختر خيارًا واحدًا على الأقل لإرساله.';

  @override
  String get agentCardFormFix => 'املأ الحقول المطلوبة للإرسال.';

  @override
  String get agentCardFieldRequired => 'هذا الحقل مطلوب.';

  @override
  String get agentCardFieldNumber => 'أدخل رقمًا.';

  @override
  String agentCardFieldMin(String min) {
    return 'أدخل $min أو أكثر.';
  }

  @override
  String agentCardFieldMax(String max) {
    return 'أدخل $max أو أقل.';
  }

  @override
  String get agentCardTextNote => 'يرى الوكيل هذا ويُحفظ في المحادثة.';

  @override
  String get agentCardBusy => 'الوكيل ما زال يعمل. يمكنك الإجابة عندما يتوقف.';

  @override
  String get agentCardSending => 'جارٍ إرسال إجابتك…';

  @override
  String get agentCardUnknown =>
      'لا نعرف بعد إن كانت هذه البطاقة قد أُجيب عنها. ستتحدّث مع المحادثة.';

  @override
  String get agentCardDeliveryUnknown =>
      'لم نتمكن من التأكد من وصول إجابتك. تحقق من المحادثة قبل الإجابة مرة أخرى.';

  @override
  String get agentCardFailed =>
      'لم تصل إجابتك. لم يُرسل شيء ويمكنك المحاولة مرة أخرى.';

  @override
  String get agentCardSent => 'تم الإرسال';

  @override
  String get agentCardShow => 'عرض البطاقة';

  @override
  String get agentCardNotAnswered => 'لم يُجب عنها';

  @override
  String get agentCardUnreadable => 'تعذّر عرض هذه البطاقة';

  @override
  String get agentCardDetails => 'التفاصيل';

  @override
  String get agentCardComposerHint => 'أو اكتب إجابتك';

  @override
  String get agentCardDangerTitle => 'إرسال هذه الإجابة؟';

  @override
  String get agentCardDangerBody =>
      'تصل إجابتك إلى الوكيل كرسالة. والوكيل هو من يقرر ما يفعله بها.';

  @override
  String get agentCardPhotoTake => 'التقاط صورة';

  @override
  String get agentCardPhotoChoose => 'اختيار صورة';

  @override
  String agentCardPhotoRemove(int number) {
    return 'إزالة الصورة $number';
  }

  @override
  String agentCardPhotoName(int number) {
    return 'صورة $number';
  }

  @override
  String agentCardPhotoCount(int count, int max) {
    return '$count من $max';
  }

  @override
  String get agentCardPhotoMax => 'هذا أقصى عدد من الصور تقبله البطاقة.';

  @override
  String get agentCardPhotoNone => 'أضف صورة لإرسالها.';

  @override
  String get agentCardPhotoUnavailable =>
      'لا يمكن إضافة صور من هنا. اكتب إجابتك بدلًا من ذلك.';

  @override
  String get agentCardPhotoTooLarge =>
      'يصل حجم كل صورة إلى 10 ميغابايت وإجمالي الصور إلى 20 ميغابايت.';

  @override
  String get agentCardChartBar => 'مخطط أعمدة';

  @override
  String get agentCardChartLine => 'مخطط خطي';

  @override
  String agentCardChartSeries(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سلسلة',
      many: '$count سلسلة',
      few: '$count سلاسل',
      two: 'سلسلتان',
      one: 'سلسلة واحدة',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartPoints(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نقطة',
      many: '$count نقطة',
      few: '$count نقاط',
      two: 'نقطتان',
      one: 'نقطة واحدة',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartHighest(String where, String value) {
    return 'الأعلى $where: $value';
  }

  @override
  String get agentCardChartNone => 'لا توجد بيانات';

  @override
  String agentCardTableLabel(int columns, int rows) {
    return 'جدول، $columns أعمدة، $rows صفوف';
  }

  @override
  String get agentCardDiffFile => 'الملف';

  @override
  String get agentCardDiffAdded => 'المضاف';

  @override
  String get agentCardDiffRemoved => 'المحذوف';

  @override
  String agentCardDiffMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'و$count ملفات أخرى',
      one: 'وملف آخر',
    );
    return '$_temp0';
  }

  @override
  String agentCardProgressValue(int percent) {
    return '$percent%';
  }

  @override
  String get cardsFromAgentsTitle => 'بطاقات من الوكلاء';

  @override
  String get cardsFromAgentsSupporting =>
      'يمكن للوكلاء أن يعرضوا عليك خيارات ونماذج وتقارير كبطاقات وأن يطلبوا صورة.';

  @override
  String get cardsStatusOff => 'متوقفة';

  @override
  String get cardsStatusChecking => 'جارٍ فحص هذا الهاتف…';

  @override
  String cardsStatusOn(String agents) {
    return 'مفعّلة لـ $agents';
  }

  @override
  String cardsStatusPartial(String agents, String reason) {
    return 'مفعّلة لـ $agents. $reason';
  }

  @override
  String cardsStatusRestart(String agents) {
    return 'أعد تشغيل $agents لإكمال التفعيل.';
  }

  @override
  String cardsStatusUnavailable(String reason) {
    return 'غير متاحة. $reason';
  }

  @override
  String cardsStatusFailed(String reason) {
    return 'تعذّر التفعيل. $reason';
  }

  @override
  String get cardsSettingFailed => 'تعذّر تغيير هذا الإعداد. حاول مرة أخرى.';

  @override
  String get cardsAgentClaude => 'Claude Code';

  @override
  String get cardsProblemUnsupportedHost =>
      'لا يمكن للوكلاء على هذا الخادم عرض البطاقات.';

  @override
  String get cardsProblemRuntimeMissing =>
      'الأدوات التي تحتاجها البطاقات غير موجودة على هذا الهاتف بعد.';

  @override
  String get cardsProblemNotQualified =>
      'لم يُتحقق بعد من أن البطاقات تعمل مع الوكلاء هنا.';

  @override
  String get cardsProblemPermissionDenied => 'لم يسمح الهاتف بهذا التغيير.';

  @override
  String get cardsProblemConflict =>
      'إعداد آخر يستخدم الاسم نفسه. لم يتغير شيء.';

  @override
  String get cardsProblemInstallationFailed => 'تعذّر تثبيت البطاقات.';

  @override
  String get cardsProblemRegistrationFailed => 'تعذّر إعلام الوكيل بالبطاقات.';

  @override
  String get cardsProblemVerificationFailed =>
      'تم تثبيت البطاقات لكنها لم تجتز الفحص.';

  @override
  String get cardsProblemRemovalFailed =>
      'تعذّرت إزالة البطاقات بالكامل. حاول مرة أخرى بعد إعادة تشغيل الوكيل.';

  @override
  String get cardsProblemStorageFailed => 'تعذّر على الهاتف حفظ الإعداد.';

  @override
  String get cardsProblemBusy => 'الهاتف مشغول بأمر آخر. حاول بعد قليل.';

  @override
  String get agentCardFieldRequiredHint => 'مطلوب';

  @override
  String get agentCardYourAnswer => 'إجابتك';

  @override
  String get agentCardDangerConfirm => 'إرسال الإجابة';

  @override
  String get agentsSignInOpenPage => 'افتح صفحة تسجيل الدخول';

  @override
  String agentsSignInCopyCode(String code) {
    return 'انسخ الرمز $code';
  }

  @override
  String cardsProblemNotQualifiedFor(int count, String agents) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'لم يُتحقق بعد من أن $agents تعمل مع البطاقات.',
      two: 'لم يُتحقق بعد من أن $agents يعملان مع البطاقات.',
      one: 'لم يُتحقق بعد من أن $agents يعمل مع البطاقات.',
    );
    return '$_temp0';
  }

  @override
  String cardsProblemRuntimeMissingFor(String agents) {
    return 'ثبّت $agents على هذا الهاتف أولًا.';
  }

  @override
  String cardsProblemRegistrationFailedFor(String agents) {
    return 'تعذّر إعلام $agents بالبطاقات.';
  }

  @override
  String cardsProblemVerificationFailedFor(String agents) {
    return 'تم تثبيت البطاقات لـ $agents لكنها لم تجتز الفحص.';
  }

  @override
  String cardsProblemRemovalFailedFor(String agents) {
    return 'تعذّرت إزالة البطاقات بالكامل من $agents. حاول مرة أخرى بعد إعادة التشغيل.';
  }

  @override
  String get crashReportsLabel => 'تقارير الأعطال';

  @override
  String get crashReportsSwitch => 'حفظ تقارير الأعطال على هذا الهاتف';

  @override
  String get crashReportsSwitchBody =>
      'يُحفظ على هذا الهاتف ولا يُرسل تلقائيًا أبدًا.';

  @override
  String get crashReportsUnavailable =>
      'تقارير الأعطال غير متاحة الآن. أعد تشغيل التطبيق وحاول مرة أخرى.';

  @override
  String get crashReportsFailed =>
      'تعذّر تحديث تقارير الأعطال. أعد تشغيل التطبيق وحاول مرة أخرى.';

  @override
  String get crashReportsNone => 'لا توجد تقارير أعطال بعد';

  @override
  String get crashReportsNoneBody =>
      'يظهر تقرير هنا إذا أُغلق التطبيق أو توقف عن الاستجابة.';

  @override
  String get crashKindError => 'واجه التطبيق خطأً غير متوقع';

  @override
  String get crashKindScreen => 'تعذّر عرض إحدى الشاشات';

  @override
  String get crashKindClosed => 'أُغلق التطبيق بشكل غير متوقع';

  @override
  String get crashKindNotResponding => 'توقف التطبيق عن الاستجابة';

  @override
  String get crashReportPreviewBody =>
      'محفوظ على هذا الهاتف فقط. لا يُرسل إلا إذا أضفت الأخطاء المحفوظة إلى بلاغ عن مشكلة بنفسك.';

  @override
  String get crashReportDetailSource => 'المصدر';

  @override
  String get crashReportDetailCategory => 'الفئة';

  @override
  String crashReportsDelete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حذف $count تقرير عطل محفوظ',
      many: 'حذف $count تقريرًا محفوظًا',
      few: 'حذف $count تقارير أعطال محفوظة',
      two: 'حذف تقريرَي عطل محفوظَين',
      one: 'حذف تقرير عطل محفوظ واحد',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsDeleteTitle => 'هل تريد حذف تقارير الأعطال المحفوظة؟';

  @override
  String get crashReportsDeleteBody =>
      'يحذف تقارير الأعطال المحفوظة. يبقى حفظ تقارير الأعطال مفعّلًا.';

  @override
  String get crashReportsDeleteConfirm => 'حذف تقارير الأعطال';

  @override
  String get crashReportsNote =>
      'يُحفظ نوع المشكلة ووقت حدوثها فقط، دون رسائل الأخطاء أو المحادثات أو كلمات المرور. يحتفظ بآخر 20 تقريرًا.';

  @override
  String crashReportsOnClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تشغيل هذا الخيار يمسح الأخطاء المحفوظة أعلاه وعددها $count.',
      two: 'تشغيل هذا الخيار يمسح الخطأين المحفوظين أعلاه.',
      one: 'تشغيل هذا الخيار يمسح الخطأ المحفوظ أعلاه.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsOffDeletes =>
      'إيقاف هذا الخيار يحذف تقارير الأعطال المحفوظة.';

  @override
  String crashReportsOffDeletesAndClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'إيقاف هذا الخيار يحذف تقارير الأعطال المحفوظة ويمسح الأخطاء المحفوظة أعلاه وعددها $count.',
      two:
          'إيقاف هذا الخيار يحذف تقارير الأعطال المحفوظة ويمسح الخطأين المحفوظين أعلاه.',
      one:
          'إيقاف هذا الخيار يحذف تقارير الأعطال المحفوظة ويمسح الخطأ المحفوظ أعلاه.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsOffClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إيقاف هذا الخيار يمسح الأخطاء المحفوظة أعلاه وعددها $count.',
      two: 'إيقاف هذا الخيار يمسح الخطأين المحفوظين أعلاه.',
      one: 'إيقاف هذا الخيار يمسح الخطأ المحفوظ أعلاه.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsDeleteBodyWithErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'يحذف تقارير الأعطال ويمسح الأخطاء المحفوظة أعلاه وعددها $count. يبقى حفظ تقارير الأعطال مفعّلًا.',
      two:
          'يحذف تقارير الأعطال ويمسح الخطأين المحفوظين أعلاه. يبقى حفظ تقارير الأعطال مفعّلًا.',
      one:
          'يحذف تقارير الأعطال ويمسح الخطأ المحفوظ أعلاه. يبقى حفظ تقارير الأعطال مفعّلًا.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsClearedToo => 'تُحذف تقارير الأعطال المحفوظة أيضًا.';

  @override
  String get recentErrorScreen => 'تعذّر رسم إحدى الشاشات';

  @override
  String get recentErrorConnection => 'انقطع الاتصال المباشر بالخادم';

  @override
  String get recentErrorAndroidExit => 'أغلق Android التطبيق';

  @override
  String get recentErrorTemperature => 'تغيّرت حرارة الهاتف';

  @override
  String get recentErrorStartup => 'واجه التطبيق مشكلة أثناء التشغيل';

  @override
  String get recentErrorReportStore => 'تعذّر فتح بلاغات المشكلات المحفوظة';

  @override
  String get recentErrorGeneric => 'حدث خطأ ما';

  @override
  String get chatUiToolBackgroundTaskFinished => 'اكتملت مهمة في الخلفية';

  @override
  String get chatUiToolBackgroundTaskFailed => 'فشلت مهمة في الخلفية';

  @override
  String get chatUiToolBackgroundTaskEnded => 'انتهت مهمة في الخلفية';

  @override
  String get chatUiToolBackgroundTaskStopped => 'أُوقفت مهمة في الخلفية';

  @override
  String chatUiToolMessageTo(String name) {
    return 'رسالة إلى $name';
  }

  @override
  String get chatUiToolMessageToMain => 'رسالة إلى المحادثة الرئيسية';

  @override
  String get chatUiToolMessageToEveryone => 'رسالة إلى الفريق كله';

  @override
  String get chatUiToolMessageToAgent => 'رسالة إلى وكيل';

  @override
  String get toolCardMessageRecipientId => 'معرّف المستلم';

  @override
  String chatUiToolBackgroundNamedFinished(String name) {
    return 'اكتمل $name';
  }

  @override
  String chatUiToolBackgroundNamedFailed(String name) {
    return 'فشل $name';
  }

  @override
  String chatUiToolBackgroundNamedStopped(String name) {
    return 'توقف $name';
  }

  @override
  String chatUiToolBackgroundNamedEnded(String name) {
    return 'انتهى $name';
  }

  @override
  String chatUiToolBackgroundNamedCutOff(String name) {
    return 'توقف $name عند انتهاء المحادثة السابقة';
  }

  @override
  String get chatUiToolBackgroundTaskCutOff =>
      'توقفت مهمة في الخلفية عند انتهاء المحادثة السابقة';

  @override
  String get chatUiToolShowCard => 'عرض بطاقة';

  @override
  String get chatUiToolShowedCard => 'تم عرض بطاقة';

  @override
  String get chatUiToolCardFailed => 'تعذّر عرض هذه البطاقة';

  @override
  String get chatStallModelSlow =>
      'يستغرق النموذج وقتًا أطول من المعتاد. انتظر، أو أوقف الرد وحاول مرة أخرى.';

  @override
  String get chatStallHelperDown =>
      'توقف مساعد الوكيل على هذا الهاتف. أوقف الرد وحاول مرة أخرى.';

  @override
  String get chatStallConnectionLost =>
      'انقطع الاتصال بالوكيل. أوقف الرد وحاول مرة أخرى عند عودته.';

  @override
  String get chatStallConnectionUnchecked =>
      'لم يصل أي جديد منذ مدة، وتعذّر التحقق من الاتصال. انتظر، أو أوقف الرد وحاول مرة أخرى.';

  @override
  String connectionSwitchingTo(String target) {
    return 'جارٍ التبديل إلى $target…';
  }

  @override
  String get shellServerSwitching => 'جارٍ التبديل…';

  @override
  String get agentsStateNotCertified => 'لم يُعتمد على هذا الإصدار بعد';

  @override
  String get agentsChipSignIn => 'تسجيل الدخول';

  @override
  String get agentsChipResume => 'استئناف';

  @override
  String get agentsChipCheck => 'فحص';

  @override
  String agentNamesPair(String first, String second) {
    return '$first و $second';
  }

  @override
  String get diagnosticsExitHistoryTitle => 'آخر مرات إغلاق التطبيق';

  @override
  String get diagnosticsExitHistoryNote =>
      'يسجّل Android كل مرة يُغلق فيها التطبيق. لا يُرسل شيء من هنا تلقائيًا.';

  @override
  String get diagnosticsExitHistoryUnsupported => 'غير متاح على هذا الهاتف';

  @override
  String get diagnosticsExitHistoryUnsupportedBody =>
      'يحتفظ Android 11 والإصدارات الأحدث بهذا السجل.';

  @override
  String get diagnosticsExitHistoryFailed =>
      'تعذّرت قراءة آخر مرات إغلاق التطبيق. حاول مرة أخرى أو أعد فتح التطبيق.';

  @override
  String get diagnosticsExitNormal => 'أُغلق التطبيق';

  @override
  String get diagnosticsExitUpdate => 'حُدّث التطبيق';

  @override
  String get diagnosticsExitForceStop => 'أُوقف التطبيق';

  @override
  String get diagnosticsExitLowMemory => 'احتاج الهاتف إلى الذاكرة';

  @override
  String get diagnosticsExitCrash => 'توقف التطبيق بشكل غير متوقع';

  @override
  String get diagnosticsExitKilled => 'أنهى Android التطبيق';

  @override
  String get diagnosticsExitReasonCode => 'رمز السبب';

  @override
  String get diagnosticsExitImportance => 'الأهمية';

  @override
  String get diagnosticsExitSummary => 'الملخص';

  @override
  String get diagnosticsReportOpen => 'مشاركة تقارير الأعطال المحفوظة';

  @override
  String get diagnosticsReportTitle => 'معاينة تقرير الأعطال';

  @override
  String get diagnosticsReportPrivacy =>
      'فئات الأخطاء وأوقاتها فقط، دون رسائل أو محادثات. لا يغادر شيء هذا الهاتف حتى تنقر مشاركة التقرير.';

  @override
  String diagnosticsReportSize(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تقرير · $size',
      many: '$count تقريرًا · $size',
      few: '$count تقارير · $size',
      two: 'تقريران · $size',
      one: 'تقرير واحد · $size',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsReportShare => 'مشاركة التقرير';

  @override
  String get diagnosticsReportStale =>
      'تغيّرت التفاصيل المحفوظة. راجع التقرير الجديد قبل المشاركة.';

  @override
  String get diagnosticsReportShareFailed =>
      'تعذّر فتح المشاركة. حاول مرة أخرى.';

  @override
  String get diagnosticsReportCaptureOff =>
      'حفظ تقارير الأعطال متوقف. شغّله لحفظ أخطاء التطبيق القادمة.';

  @override
  String get diagnosticsReportEmpty => 'لا توجد تقارير أعطال محفوظة للمشاركة.';

  @override
  String get diagnosticsUnavailable =>
      'هذه التفاصيل غير متاحة. أعد فتح التطبيق وحاول مرة أخرى.';

  @override
  String backgroundPauseTimeLimit(String at) {
    return 'أُوقف الاتصال في الخلفية مؤقتًا $at لتوفير البطارية.';
  }

  @override
  String get backgroundPauseRestricted =>
      'أُوقف الاتصال في الخلفية مؤقتًا: استخدام البطارية مقيَّد.';

  @override
  String backgroundPauseUserStopped(String at) {
    return 'توقف الاتصال في الخلفية لأن التطبيق أُغلق $at.';
  }

  @override
  String backgroundPauseAppUpdated(String at) {
    return 'توقف الاتصال في الخلفية لأن التطبيق حُدِّث $at.';
  }

  @override
  String get backgroundPauseInterrupted =>
      'توقف الاتصال في الخلفية لسبب غير معروف.';

  @override
  String get backgroundPauseDeviceRestarted =>
      'توقف الاتصال في الخلفية عند إعادة تشغيل الهاتف.';

  @override
  String get backgroundPauseResume => 'استئناف الاتصال في الخلفية';

  @override
  String get backgroundPauseResumeShort => 'استئناف';

  @override
  String get backgroundPauseResuming => 'جارٍ استئناف الاتصال في الخلفية…';

  @override
  String get backgroundPauseResumeFailed =>
      'تعذّر الاستئناف. افتح إعدادات الاستمرار في العمل.';

  @override
  String get backgroundPauseOpenKeepRunning => 'فتح إعدادات الاستمرار في العمل';

  @override
  String get backgroundPauseResumed => 'تم استئناف الاتصال في الخلفية.';

  @override
  String get diagnosticsExitNoProblems =>
      'لا توجد مرات إغلاق غير متوقعة مؤخرًا.';

  @override
  String diagnosticsExitRoutine(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مرة إغلاق اعتيادية (تحديثات، أو أغلقته أنت)',
      many: '$count مرة إغلاق اعتيادية (تحديثات، أو أغلقته أنت)',
      few: '$count مرات إغلاق اعتيادية (تحديثات، أو أغلقته أنت)',
      two: 'إغلاقان اعتياديان (تحديثات، أو أغلقته أنت)',
      one: 'إغلاق اعتيادي واحد (تحديثات، أو أغلقته أنت)',
    );
    return '$_temp0';
  }

  @override
  String diagnosticsExitShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض الكل ($count)',
      one: 'عرض الكل (1)',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsExitUnknown => 'أُغلق لسبب غير معروف';

  @override
  String diagnosticsExitToday(String time) {
    return 'اليوم $time';
  }

  @override
  String diagnosticsExitYesterday(String time) {
    return 'أمس $time';
  }

  @override
  String get liveToolCommand => 'جارٍ تشغيل أمر…';

  @override
  String get liveToolEdit => 'جارٍ تعديل الملفات…';

  @override
  String get liveToolRead => 'جارٍ قراءة الملفات…';

  @override
  String get liveToolSearch => 'جارٍ البحث في الملفات…';

  @override
  String get liveToolWeb => 'جارٍ تصفح الويب…';

  @override
  String get liveToolSubagent => 'جارٍ تشغيل وكيل فرعي…';

  @override
  String get liveToolPlan => 'جارٍ التخطيط…';

  @override
  String get liveToolWorking => 'جارٍ العمل…';

  @override
  String get liveToolCard => 'جارٍ عرض بطاقة…';

  @override
  String get liveToolBackgroundTask => 'جارٍ فحص مهمة في الخلفية…';

  @override
  String liveToolOther(String tool) {
    return 'جارٍ تشغيل $tool…';
  }

  @override
  String get e7LocaleUiJapanese => '日本語';

  @override
  String get e7LocaleUiChinese => '简体中文';

  @override
  String get e7LocaleUiSpanish => 'Español';

  @override
  String get e7LocaleUiPortuguese => 'Português (Brasil)';

  @override
  String get e7LocaleUiRussian => 'Русский';

  @override
  String connectorCardSuggests(String agent) {
    return '$agent يقترح موصّلًا';
  }

  @override
  String connectorCardAnnouncement(String agent, String name) {
    return '$agent يقترح موصّلًا: $name';
  }

  @override
  String get connectorCardRuns => 'يعمل';

  @override
  String get connectorCardLasts => 'المدة';

  @override
  String get connectorCardLastsValue => 'ما دام هذا الخادم يعمل';

  @override
  String get connectorCardRunsNode => 'على هذا الخادم، عبر Node';

  @override
  String get connectorCardRunsPython => 'على هذا الخادم، عبر Python';

  @override
  String get connectorCardRunsDocker =>
      'داخل Docker، ولا يستطيع التطبيق تشغيله';

  @override
  String get connectorCardRunsNone => 'لا شيء يستطيع التطبيق تشغيله';

  @override
  String get connectorCardRunsHosted => 'مستضاف';

  @override
  String get connectorCardConnect => 'اتصال';

  @override
  String get connectorCardConnecting => 'جارٍ الاتصال…';

  @override
  String get connectorCardChecking => 'جارٍ فحص الاتصال…';

  @override
  String get connectorCardSignInNeeded => 'يحتاج هذا الموصّل إلى تسجيل دخولك.';

  @override
  String get connectorCardSignIn => 'تسجيل الدخول';

  @override
  String get connectorCardOpenSignIn => 'فتح صفحة تسجيل الدخول';

  @override
  String get connectorCardSignInWaiting =>
      'بانتظار اكتمال تسجيل الدخول في المتصفح.';

  @override
  String get connectorCardManualCode =>
      'الصق عنوان العودة من المتصفح أو رمز التفويض لإكمال تسجيل الدخول.';

  @override
  String get connectorCardCodeLabel => 'عنوان العودة أو الرمز';

  @override
  String get connectorCardCodeRequired => 'الصق عنوان العودة أو الرمز أولًا.';

  @override
  String get connectorCardFinishSignIn => 'إكمال تسجيل الدخول';

  @override
  String get connectorCardCheckStatus => 'تحديث';

  @override
  String get connectorCardCancelSignIn => 'إلغاء تسجيل الدخول';

  @override
  String get connectorCardCancelTitle => 'إلغاء تسجيل الدخول؟';

  @override
  String connectorCardCancelBody(String name) {
    return 'يتوقف $name عن الانتظار ويُمسح تسجيل دخوله المحفوظ. ستسجّل الدخول مجددًا لاستخدامه.';
  }

  @override
  String get connectorCardKeepWaiting => 'متابعة الانتظار';

  @override
  String get connectorCardConnected => 'متصل';

  @override
  String get connectorCardToolsReady => 'الأدوات جاهزة';

  @override
  String get connectorCardLoadedTools =>
      'تم تحميل الأدوات. تابع هذه المحادثة لاستخدامها.';

  @override
  String get connectorCardConnectedUnconfirmed =>
      'متصل. لم يتم تأكيد توفّر الأدوات.';

  @override
  String get connectorCardFailureUnavailable =>
      'لا يستطيع هذا الوكيل ربط الأدوات من المحادثة.';

  @override
  String get connectorCardFailureInvalidSuggestion =>
      'اقتراح هذا الموصّل غير متاح. تصفّح الموصّلات في الأدوات.';

  @override
  String get connectorCardFailureSetupRequired =>
      'يحتاج هذا الموصّل إلى إعداد في الأدوات قبل أن يتصل.';

  @override
  String get connectorCardFailureNameConflict =>
      'يوجد موصّل بهذا الاسم بالفعل. تحقق منه في الأدوات.';

  @override
  String get connectorCardFailureSourceChanged =>
      'تغيّر اتصال هذه المحادثة. أعد فتح بطاقة الموصّل.';

  @override
  String get connectorCardFailureConnectFailed =>
      'تعذّر تأكيد الاتصال. افحص حالته قبل المحاولة مجددًا.';

  @override
  String get connectorCardFailureAuthenticationFailed =>
      'تعذّر تأكيد تسجيل الدخول. افحص حالته قبل المحاولة مجددًا.';

  @override
  String get connectorCardFailureOauthUnavailable =>
      'تسجيل الدخول لهذا الموصّل غير متاح في هذه المحادثة.';

  @override
  String get connectorCardFailureNotConnected =>
      'الموصّل غير متصل بعد. تحقق من إعداده في الأدوات.';

  @override
  String get connectorCardOpenTools => 'فتح الموصّلات في الأدوات';

  @override
  String get connectorCardTryAgain => 'إعادة المحاولة';

  @override
  String get connectorCardReopen => 'إعادة فتح البطاقة';

  @override
  String get chatUiToolSearchedConnectors => 'بحث في الموصّلات';

  @override
  String get chatUiConnectorSearchEmpty => 'لا موصّلات في الفهرس المحمّل.';

  @override
  String get chatUiConnectorSearchNotLoaded =>
      'تعذّر تحميل الفهرس الآن. اسأل مجددًا بعد قليل.';

  @override
  String get chatUiConnectorSearchOff => 'الفهرس متوقف';

  @override
  String get chatUiConnectorSearchOffBody =>
      'فهرس الموصّلات قائمة عامة بالموصّلات. تشغيله ينزّل القائمة ويرسل كلمات البحث إلى السجل العام، ولا يتم توصيل أي شيء.';

  @override
  String get chatUiConnectorSearchTurnOn => 'تشغيل فهرس الموصّلات';

  @override
  String get chatUiConnectorSearchTurnedOn =>
      'الفهرس يعمل. اطلب من الوكيل البحث مجددًا.';

  @override
  String get chatUiConnectorSearchTurnOnFailed =>
      'تعذّر تشغيل الفهرس. حاول مجددًا.';

  @override
  String get chatUiConnectorSearchUnavailable =>
      'البحث في الموصّلات غير متاح. حاول مجددًا من الأدوات.';

  @override
  String get chatUiConnectorSearchNotConnected => 'غير متصل';

  @override
  String get kitMarkdownImage => 'صورة';

  @override
  String get toolCardPlan => 'الخطة';

  @override
  String get toolCardWorktreeSetup => 'إعداد نسخة منفصلة';

  @override
  String get toolCardWhatItDid => 'ما فعله';

  @override
  String get toolCardTechnicalDetails => 'تفاصيل تقنية';

  @override
  String toolCardPageStatus(String status) {
    return 'ردّت الصفحة $status';
  }

  @override
  String toolCardAsked(String prompt) {
    return 'السؤال: $prompt';
  }

  @override
  String get chatUiAgentNote => 'ملاحظة';

  @override
  String get chatUiAgentWarning => 'تحذير';

  @override
  String get chatUiAgentProblem => 'مشكلة';

  @override
  String get chatUiCompactedAutomatically =>
      'ضُغطت المحادثة تلقائيًا لتوفير المساحة.';

  @override
  String get chatUiCompactedWhenAsked => 'ضُغطت المحادثة عندما طلبت ذلك.';

  @override
  String chatUiCompactedFrom(String tokens) {
    return 'كان طولها نحو $tokens رمز.';
  }

  @override
  String get chatRequestTitleFetch => 'فتح صفحة ويب';

  @override
  String get chatRequestTitleWebSearch => 'البحث في الويب';

  @override
  String get chatRequestTitleSearch => 'البحث في المشروع';

  @override
  String get chatRequestTitleTask => 'تشغيل وكيل مساعد';

  @override
  String get chatRequestTitleSkill => 'استخدام مهارة';

  @override
  String get chatRequestTitleMode => 'تغيير وضع الوكيل';

  @override
  String get chatRequestDetailRunsIn => 'يعمل في';

  @override
  String get chatRequestDetailAsks => 'ما يطلبه';

  @override
  String get chatRequestDetailRange => 'جزء من الملف';

  @override
  String get chatRequestDetailHelper => 'نوع المساعد';

  @override
  String chatRequestReadFrom(String line) {
    return 'من السطر $line';
  }

  @override
  String chatRequestReadLines(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count أسطر',
      one: 'سطر واحد',
    );
    return '$_temp0';
  }

  @override
  String get toolCardWhatWasSent => 'ما أُرسل';

  @override
  String get toolCardWhatCameBack => 'ما عاد';

  @override
  String get kitRequestShowMore => 'عرض المزيد';

  @override
  String get kitRequestShowLess => 'عرض أقل';

  @override
  String paseoCheckReady(String agents) {
    return 'جاهز على ذلك الحاسوب: $agents.';
  }

  @override
  String get paseoCheckNoneReady => 'لا يوجد وكيل جاهز على ذلك الحاسوب بعد.';

  @override
  String get serverRowDetailsVersion => 'الإصدار';

  @override
  String get capabilityDevServices => 'خوادم التطوير';

  @override
  String get capabilityDevServicesDetail =>
      'شغّل خادم المشروع أو المعاينة وأوقفه عند الحاجة';

  @override
  String get capabilityWebSearch => 'البحث في الويب';

  @override
  String get capabilityWebSearchDetail => 'اسمح للوكيل بالبحث في الويب';

  @override
  String get capabilityDeleteMessage => 'حذف رسالة';

  @override
  String get capabilityDeleteMessageDetail => 'احذف رسالة من المحادثة';

  @override
  String get capabilitySavedPermissions => 'الموافقات المحفوظة';

  @override
  String get capabilitySavedPermissionsDetail =>
      'اطلع على الإجراءات التي سمحت بها دائما واحذفها';

  @override
  String get capabilityMcpAdd => 'إضافة خوادم MCP';

  @override
  String get capabilityMcpAddDetail =>
      'أضف خادم MCP أو غيّر إعداداته من التطبيق';

  @override
  String get capabilityApprovals => 'السؤال قبل التنفيذ';

  @override
  String get capabilityApprovalsDetail =>
      'اختر أن يطلب الوكيل موافقتك أو يعمل من تلقاء نفسه';

  @override
  String get capabilitySteer => 'الإرسال أثناء العمل';

  @override
  String get capabilitySteerDetail =>
      'أضف رسالة أثناء انشغال الوكيل؛ يقرؤها خلال الجولة الحالية';

  @override
  String get serverSettingsAuthPasswordSaved =>
      'كلمة المرور محفوظة على هذا الهاتف';

  @override
  String get serverSettingsAuthTokenSaved => 'رمز الاتصال محفوظ على هذا الهاتف';

  @override
  String get serverSettingsAuthTokenMissing => 'لا يوجد رمز اتصال محفوظ';

  @override
  String connectionPasswordRejectedFor(String server) {
    return 'تغيّرت كلمة مرور $server. حدّثها لإعادة الاتصال.';
  }

  @override
  String connectionTokenRejectedFor(String server) {
    return 'رفض $server رمز الاتصال. حدّثه لإعادة الاتصال.';
  }

  @override
  String serverSettingsEditTitle(String server) {
    return 'تعديل $server';
  }

  @override
  String get serverSettingsEditDetail => 'غيّر اسمه أو عنوانه أو تسجيل الدخول';

  @override
  String serverSettingsRemoveTitle(String server) {
    return 'إزالة $server';
  }

  @override
  String get serverSettingsRecheckAgents => 'التحقق من الوكلاء مجددا';

  @override
  String get serverSettingsRecheckAgentsDetail =>
      'استخدمه بعد تسجيل الدخول إلى وكيل على ذلك الحاسوب.';

  @override
  String get serverSettingsRecheckChecking => 'جارٍ التحقق من الوكلاء…';

  @override
  String get serverSettingsRecheckFailed =>
      'تعذر التحقق من الوكلاء. حاول بعد قليل.';

  @override
  String integrationsMcpDetails(String name) {
    return 'تفاصيل $name';
  }

  @override
  String get usageBusiestDays => 'أكثر الأيام نشاطًا';

  @override
  String usageDaySteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count خطوة',
      many: '$count خطوة',
      few: '$count خطوات',
      two: 'خطوتان',
      one: 'خطوة واحدة',
    );
    return '$_temp0';
  }

  @override
  String modelPickerAgentUses(String model) {
    return 'يستخدم $model';
  }

  @override
  String get sessionMenuArchive => 'أرشفة المحادثة';

  @override
  String get sessionMenuArchiveHint => 'تنتقل إلى المؤرشفة ويحتفظ بها الخادم';

  @override
  String get sessionMenuDelete => 'حذف المحادثة';

  @override
  String get sessionMenuDeleteHint => 'تُزال هي وسجلها نهائيًا';

  @override
  String sessionArchiveTitle(String title) {
    return 'أرشفة «$title»؟';
  }

  @override
  String get sessionArchiveBody => 'تنتقل إلى المؤرشفة ويمكنك قراءتها هناك.';

  @override
  String sessionDeleteTitle(String title) {
    return 'حذف «$title»؟';
  }

  @override
  String get sessionDeleteBody =>
      'تُزال المحادثة وسجلها نهائيًا. لا يمكن التراجع عن ذلك.';

  @override
  String monitorUnreachableTitle(String server) {
    return 'تعذر الوصول إلى $server';
  }

  @override
  String get monitorUnreachableDetail => 'ستظهر أسئلته هنا عند عودته.';

  @override
  String monitorUnreachableCheck(String server) {
    return 'تحقق من $server مجددا';
  }

  @override
  String agentsStateLimitReset(String time) {
    return 'تم بلوغ حد الخطة · يُعاد ضبطه $time';
  }

  @override
  String get exportMarkdownUnredacted =>
      'النص المحفوظ غير منقّح. يحتوي بالضبط على ما تراه، بما في ذلك أي أسرار أو مسارات خاصة في المحادثة.';

  @override
  String teamUiGateAnswerConfirmCancelNamedBody(String task) {
    return 'تتوقف «$task». ويبقى عملها المفتوح كما هو.';
  }

  @override
  String teamProjectStopNamedBody(String project) {
    return 'يتوقف «$project». تتوقف مهامه الجارية ويبقى عملها وسجله.';
  }

  @override
  String teamProjectTaskStopNamedBody(String task) {
    return 'تتوقف «$task». وتبقى محادثتها وتغييراتها للمراجعة.';
  }

  @override
  String get teamAgentUnavailableState => 'لا يمكن تشغيله';

  @override
  String get teamAgentUnavailableModelWords => 'نموذجه غير مُعدّ على الكمبيوتر';

  @override
  String get teamAgentUnavailableOtherWords =>
      'لا يستطيع الكمبيوتر تشغيله الآن';

  @override
  String teamAgentUnavailableTitle(String agent) {
    return 'لا يمكن تشغيل $agent';
  }

  @override
  String teamAgentUnavailableBody(String reason) {
    return '$reason. أصلح ذلك على الكمبيوتر ثم تحقق مجددًا.';
  }

  @override
  String get teamAgentUnavailableHostSays => 'ما يقوله الكمبيوتر';

  @override
  String get teamControlForceStop => 'إيقاف بالقوة';

  @override
  String get teamProjectControlSuspendWord => 'إيقاف المشروع مؤقتًا';

  @override
  String get teamProjectControlResumeWord => 'استئناف المشروع';

  @override
  String get teamProjectControlRemoveWord => 'حذف المشروع';

  @override
  String get teamJobTurnOnWord => 'تشغيل';

  @override
  String get teamJobTurnOffWord => 'إيقاف';

  @override
  String get teamHomeProjectsTitle => 'المشاريع';

  @override
  String get teamRigStateActive => 'نشط';

  @override
  String get teamRigStatePaused => 'متوقف مؤقتًا';

  @override
  String teamRigPausedTitle(String project) {
    return '$project متوقف مؤقتًا';
  }

  @override
  String get teamRigPausedBody => 'لا يبدأ أي عمل جديد فيه حتى تستأنفه.';

  @override
  String teamRigPause(String project) {
    return 'إيقاف المشروع $project مؤقتًا';
  }

  @override
  String teamRigResume(String project) {
    return 'استئناف المشروع $project';
  }

  @override
  String teamRigPauseTitle(String project) {
    return 'إيقاف $project مؤقتًا؟';
  }

  @override
  String teamRigPauseBody(String project) {
    return 'لا يبدأ أي عمل جديد في «$project» حتى تستأنفه. لا يُحذف شيء.';
  }

  @override
  String get teamRigPauseConfirm => 'إيقاف المشروع مؤقتًا';

  @override
  String teamRigDelete(String project) {
    return 'حذف المشروع $project';
  }

  @override
  String teamRigDeleteTitle(String project) {
    return 'حذف $project؟';
  }

  @override
  String teamRigDeleteBody(String project) {
    return 'تتم إزالة «$project» من هذا الفريق. لا يُمَسّ مجلده ولا ملفاته على الكمبيوتر.';
  }

  @override
  String get teamRigDeleteConfirm => 'حذف المشروع';

  @override
  String get teamRigDeleteLost => 'يتوقف الفريق عن العمل عليه';

  @override
  String get teamRigDeleteKeptFiles => 'مجلده وملفاته على الكمبيوتر';

  @override
  String get teamRigDeleteKeptHistory => 'مهامه وسجله السابق';

  @override
  String get teamRigFolderLabel => 'المجلد على الكمبيوتر';

  @override
  String get teamRigNotFoundTitle => 'هذا المشروع لم يعد موجودًا';

  @override
  String get teamRigNotFoundBody => 'لم يعد الكمبيوتر يعرضه. ارجع إلى الفريق.';

  @override
  String teamProjectDelete(String project) {
    return 'حذف المشروع $project';
  }

  @override
  String teamProjectDeleteTitle(String project) {
    return 'حذف $project؟';
  }

  @override
  String teamProjectDeleteBody(String project) {
    return 'يُحذف «$project» من فريق الذكاء الاصطناعي على هذا الهاتف مع نسخة الفريق من شيفرته. لا يمكن التراجع عن ذلك.';
  }

  @override
  String get teamProjectDeleteConfirm => 'حذف المشروع';

  @override
  String get teamProjectDeleteLostPlan => 'تُحذف خطته ومهامه وسجله';

  @override
  String get teamProjectDeleteLostRunning => 'يتوقف العمل الجاري';

  @override
  String get teamProjectDeleteKept => 'يبقى المجلد الذي أضفته منه كما هو';

  @override
  String teamAgentForceStop(String agent) {
    return 'إيقاف $agent بالقوة';
  }

  @override
  String teamAgentForceStopTitle(String agent) {
    return 'إيقاف $agent بالقوة؟';
  }

  @override
  String teamAgentForceStopBody(String agent) {
    return 'ينتهي $agent فورًا دون إكمال ما يعمله. قد يضيع ما لم يحفظه.';
  }

  @override
  String teamAgentStopStuckTitle(String agent) {
    return '$agent لم يتوقف';
  }

  @override
  String get teamAgentStopStuckBody =>
      'لم ينهِ الإيقاف العادي عمله. يمكنك إنهاؤه بالقوة الآن.';

  @override
  String get teamJobsTitle => 'المهام المجدولة';

  @override
  String get teamJobsWholeTeamTitle => 'للفريق كله';

  @override
  String get teamJobsEmptyTitle => 'لا توجد مهام مجدولة';

  @override
  String get teamJobsEmptyBody =>
      'لا يبدأ الكمبيوتر أي شيء من تلقاء نفسه لهذا المشروع.';

  @override
  String get teamJobsFailedTitle => 'تعذّرت قراءة المهام المجدولة';

  @override
  String get teamJobsFailedBody => 'تحقق من الاتصال بالكمبيوتر ثم حاول مجددًا.';

  @override
  String get teamJobsLoading => 'جارٍ قراءة المهام المجدولة';

  @override
  String teamJobEveryDayAt(String time) {
    return 'كل يوم في $time';
  }

  @override
  String teamJobEveryWeekdayAt(String time) {
    return 'كل يوم عمل في $time';
  }

  @override
  String teamJobEveryDowAt(String day, String time) {
    return 'كل $day في $time';
  }

  @override
  String get teamJobEveryHour => 'كل ساعة';

  @override
  String teamJobEveryMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'كل $count دقيقة',
      few: 'كل $count دقائق',
      two: 'كل دقيقتين',
      one: 'كل دقيقة',
    );
    return '$_temp0';
  }

  @override
  String teamJobEveryHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'كل $count ساعة',
      few: 'كل $count ساعات',
      two: 'كل ساعتين',
      one: 'كل ساعة',
    );
    return '$_temp0';
  }

  @override
  String teamJobEverySeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'كل $count ثانية',
      few: 'كل $count ثوانٍ',
      two: 'كل ثانيتين',
      one: 'كل ثانية',
    );
    return '$_temp0';
  }

  @override
  String get teamJobOnSchedule => 'وفق جدول';

  @override
  String get teamJobRepeats => 'يتكرر من تلقاء نفسه';

  @override
  String get teamJobWhenEvent => 'عند حدوث شيء';

  @override
  String get teamJobManual => 'فقط عند بدئه يدويًا';

  @override
  String get teamJobWebhook => 'عند وصول طلب ويب';

  @override
  String get teamJobCondition => 'عندما ينجح فحصه';

  @override
  String get teamJobOther => 'يبدأ من تلقاء نفسه';

  @override
  String get agentFeaturesLabel => 'إعدادات الوكيل';

  @override
  String agentFeaturesSheetTitle(String agent) {
    return 'إعدادات $agent';
  }

  @override
  String agentFeatureChipOff(String name) {
    return '$name، متوقف';
  }

  @override
  String agentFeatureChipOn(String name) {
    return '$name، يعمل';
  }

  @override
  String get agentFeatureFastChip => 'سريع';

  @override
  String get agentFeaturesLoadFailed => 'تعذر تحميل إعدادات هذا الوكيل.';

  @override
  String get agentFeatureFastTitle => 'الوضع السريع';

  @override
  String get agentFeatureFastDetail =>
      'ردود أسرع من النماذج المدعومة. تكلفته أعلى.';

  @override
  String get agentFeatureSaving => 'جارٍ الحفظ…';

  @override
  String agentFeatureChangeFailed(String name) {
    return 'تعذر تغيير $name. يبقى إعداده القديم.';
  }

  @override
  String get claudeImportTitle => 'استيراد من Claude Code';

  @override
  String claudeImportIntro(String project) {
    return 'اختر محادثة بدأتها في Claude Code داخل $project. تُفتح هنا لتتابعها.';
  }

  @override
  String claudeImportRowDetail(String project, String when) {
    return '$project · $when';
  }

  @override
  String get claudeImportUntitled => 'محادثة بلا عنوان';

  @override
  String get claudeImportEmptyTitle => 'لا شيء للاستيراد';

  @override
  String get claudeImportEmptyBody =>
      'تظهر هنا المحادثات التي تبدأها في Claude Code داخل هذا المشروع.';

  @override
  String get claudeImportLoadFailedTitle => 'تعذر البحث';

  @override
  String get claudeImportLoadFailedBody =>
      'لم تُحمَّل القائمة. تحقق من الاتصال ثم حاول مرة أخرى.';

  @override
  String claudeImportAlready(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count محادثات أخرى موجودة هنا بالفعل.',
      one: 'محادثة واحدة أخرى موجودة هنا بالفعل.',
    );
    return '$_temp0';
  }

  @override
  String get claudeImportWorking => 'جارٍ الاستيراد…';

  @override
  String get claudeImportFailed => 'تعذر استيراد تلك المحادثة. حاول مرة أخرى.';

  @override
  String hostUpdateRow(String computer) {
    return 'تحديث الوكلاء على $computer';
  }

  @override
  String hostUpdateRowRunning(String version) {
    return 'يعمل $version. يثبّت أحدث برنامج للوكلاء.';
  }

  @override
  String get hostUpdateRowUnknown =>
      'يثبّت أحدث برنامج للوكلاء على ذلك الحاسوب.';

  @override
  String get hostUpdateConfirmTitle => 'تحديث الوكلاء؟';

  @override
  String hostUpdateConfirmBody(String computer) {
    return 'سيثبّت هذا أحدث برنامج للوكلاء على $computer ويعيد تشغيله.';
  }

  @override
  String hostUpdateConfirmPauses(String computer) {
    return 'تتوقف المحادثات على $computer مؤقتا أثناء إعادة التشغيل.';
  }

  @override
  String get hostUpdateConfirmKept => 'تبقى محادثاتك وإعداداتك كما هي.';

  @override
  String get hostUpdateConfirmAction => 'تحديث الوكلاء';

  @override
  String get hostUpdateStarting => 'جارٍ بدء التحديث…';

  @override
  String get hostUpdateDownloading => 'جارٍ تنزيل أحدث إصدار…';

  @override
  String get hostUpdateInstalling => 'جارٍ التثبيت…';

  @override
  String get hostUpdateFinishing => 'جارٍ الإنهاء…';

  @override
  String hostUpdateDone(String version) {
    return 'تم التحديث إلى $version. يعيد التطبيق الاتصال تلقائيا.';
  }

  @override
  String get hostUpdateDoneNoVersion =>
      'تم تحديث Paseo. يعيد التطبيق الاتصال تلقائيا.';

  @override
  String hostUpdateCurrent(String computer) {
    return '$computer محدّث بالفعل.';
  }

  @override
  String get hostUpdateFailed =>
      'لم يكتمل التحديث. حاول مرة أخرى، أو حدّثه على ذلك الحاسوب.';

  @override
  String get hostUpdateDropped =>
      'انقطع الاتصال أثناء التحديث. يعيد التطبيق الاتصال.';

  @override
  String get hostUpdateDetailsRow => 'سبب الفشل';

  @override
  String get hostUpdateDetailsTitle => 'تفاصيل التحديث';

  @override
  String get hostUpdateDetailsMessage => 'رسالة من الحاسوب';

  @override
  String get filesTextSurface => 'نص داخل الملفات';

  @override
  String get filesSearchText => 'البحث عن نص داخل الملفات';

  @override
  String filesSearchInside(String query) {
    return 'ابحث داخل الملفات عن \"$query\"';
  }

  @override
  String get filesTextHintTitle => 'ابحث داخل الملفات';

  @override
  String get filesTextHintBody => 'اكتب كلمة أو عبارة لترى كل سطر يحتوي عليها.';

  @override
  String get filesNoTextTitle => 'لا توجد نتائج';

  @override
  String get filesNoTextBody =>
      'لا يحتوي أي ملف على هذا النص. تحقق من الإملاء أو جرّب كلمات أقل.';

  @override
  String get filesTextFailedTitle => 'تعذّر البحث في الملفات';

  @override
  String filesTextLimited(int count) {
    return 'تظهر أول $count نتيجة. اكتب أكثر لتضييقها.';
  }

  @override
  String filesTextMatchLine(int line) {
    return 'السطر $line';
  }

  @override
  String get productErrorComputer =>
      'تعذّر الوصول إلى حاسوبك. تأكد من أنه يعمل ومتصل، ثم حاول مرة أخرى.';

  @override
  String get productErrorProjectGit =>
      'تعذّرت قراءة سجل Git لهذا المشروع، لذا لم تبدأ المحادثة. استعد المشروع من نسخة، أو انسخه من جديد.';

  @override
  String kitGalleryPosition(int current, int total) {
    return '$current من $total';
  }

  @override
  String kitGalleryPictureLabel(int current, int total, String name) {
    return 'الصورة $current من $total: $name';
  }

  @override
  String get kitGalleryEdit => 'تعديل';

  @override
  String get kitGallerySend => 'إرسال';

  @override
  String get kitGalleryLoading => 'جارٍ تحميل الصورة';

  @override
  String get kitGalleryLoadFailed => 'تعذّر تحميل هذه الصورة';

  @override
  String get kitGalleryLoadFailedBody => 'تحقق من الاتصال ثم حاول مرة أخرى.';

  @override
  String get kitGalleryTryAgain => 'حاول مرة أخرى';

  @override
  String get kitMarkupTitle => 'تعديل الصورة';

  @override
  String get kitMarkupColours => 'لون القلم';

  @override
  String get kitMarkupRed => 'أحمر';

  @override
  String get kitMarkupYellow => 'أصفر';

  @override
  String get kitMarkupWhite => 'أبيض';

  @override
  String get kitMarkupBlack => 'أسود';

  @override
  String get kitMarkupUndo => 'تراجع';

  @override
  String get kitMarkupClear => 'مسح';

  @override
  String get kitMarkupCrop => 'اقتصاص';

  @override
  String get kitMarkupApply => 'تطبيق';

  @override
  String get kitMarkupCancel => 'إلغاء';

  @override
  String get kitMarkupDone => 'تم';

  @override
  String get kitMarkupCanvas => 'الصورة قيد التعديل. ارسم عليها بإصبعك.';

  @override
  String get kitMarkupCropHandle => 'زاوية الاقتصاص. اسحبها لتغيير الحجم.';

  @override
  String get kitMarkupCropArea => 'منطقة الاقتصاص. اسحبها لتحريكها.';

  @override
  String get kitMarkupFailed => 'تعذّر إنشاء الصورة المعدلة';

  @override
  String get kitMarkupFailedBody =>
      'حاول مرة أخرى، أو ارجع وأرسل الصورة الأصلية.';

  @override
  String kitContextRingSemantics(String percent) {
    return 'السياق مستخدم بنسبة $percent %';
  }

  @override
  String get kitContextRingTapHint => 'عرض تفاصيل السياق';

  @override
  String get kitContextRingCompacting => 'جارٍ الضغط';

  @override
  String get kitContextRingCompactingSemantics => 'السياق، جارٍ الضغط';

  @override
  String get kitContextSheetTitle => 'نافذة السياق';

  @override
  String kitContextSheetTokens(String used, String limit) {
    return '$used من $limit رمز';
  }

  @override
  String get kitContextSheetModel => 'النموذج';

  @override
  String get kitContextSheetWindow => 'نافذة السياق';

  @override
  String get kitContextSheetMeasured => 'وقت القياس';

  @override
  String get kitContextSheetMeasuredValue => 'بعد آخر رد';

  @override
  String get kitContextSheetExplain =>
      'نافذة السياق هي مقدار المحادثة الذي يستطيع النموذج قراءته دفعة واحدة: رسائلك وردوده والملفات ونتائج الأدوات. عندما تمتلئ يجب تلخيص الأجزاء الأقدم أو تركها، لذا فضغط المحادثة قبل ذلك يُبقي الإجابات دقيقة.';

  @override
  String get kitContextSheetChartTitle => 'كل رد';

  @override
  String kitContextChartSemantics(String count, String percent) {
    return 'السياق بعد كل رد من $count ردًا، وآخرها $percent %';
  }

  @override
  String get kitContextChartCompacted => 'ضُغطت المحادثة هنا';

  @override
  String get kitContextSheetCompactNow => 'اضغط الآن';

  @override
  String get phraseChipsRowLabel => 'عبارات سريعة';

  @override
  String phraseChipsSend(String phrase) {
    return 'إرسال: $phrase';
  }

  @override
  String get phraseChipsMenuLabel => 'خيارات العبارة';

  @override
  String get phraseChipsEdit => 'تعديل';

  @override
  String get phraseChipsHide => 'إخفاء';

  @override
  String get settingsPhraseChipsTitle => 'اقتراح العبارات';

  @override
  String get settingsPhraseChipsSupporting =>
      'عباراتك القصيرة المعتادة فوق مربع الرسالة، تُتعلَّم على هذا الهاتف مما ترسله.';

  @override
  String get settingsPhraseChipsShowHidden => 'إظهار العبارات المخفية';

  @override
  String settingsPhraseChipsHiddenCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عبارة مخفية',
      many: '$count عبارة مخفية',
      few: '$count عبارات مخفية',
      two: 'عبارتان مخفيتان',
      one: 'عبارة مخفية واحدة',
    );
    return '$_temp0';
  }

  @override
  String get kitFactToldWord => 'قلت ذلك';

  @override
  String get kitFactSeenWord => 'من مصدر';

  @override
  String get kitFactGuessedWord => 'خمّنه النظام';

  @override
  String get kitFactCalculatedWord => 'حُسب';

  @override
  String kitFactToldLine(String date) {
    return 'قلت ذلك · $date';
  }

  @override
  String kitFactSeenLine(String source, String date) {
    return 'من $source · $date';
  }

  @override
  String kitFactSeenNoSourceLine(String date) {
    return 'من مصدر · $date';
  }

  @override
  String kitFactGuessedLine(String date) {
    return 'خمّنه النظام · $date';
  }

  @override
  String kitFactGuessedFromLine(String date, String source) {
    return 'خمّنه النظام · $date · من $source';
  }

  @override
  String kitFactCalculatedLine(String date) {
    return 'حُسب · $date';
  }

  @override
  String kitFactWaitingLine(String line) {
    return '$line · بانتظار موافقتك';
  }

  @override
  String get fbSourceChat => 'محادثة حفظتها';

  @override
  String get fbSourceShare => 'نص شاركته';

  @override
  String get fbSourceNote => 'ملاحظة';

  @override
  String get fbSourceImport => 'نص ألصقته';

  @override
  String get fbSourceStatement => 'كشف حساب';

  @override
  String get fbSourceOther => 'مصدر آخر';

  @override
  String get fbKindPerson => 'شخص';

  @override
  String get fbKindDocument => 'مستند';

  @override
  String get fbKindSubscription => 'اشتراك';

  @override
  String get fbKindWarranty => 'ضمان';

  @override
  String get fbKindBill => 'فاتورة';

  @override
  String get fbKindCard => 'بطاقة';

  @override
  String get fbKindAccount => 'حساب';

  @override
  String get fbKindOrganization => 'جهة';

  @override
  String get fbKindPlace => 'مكان';

  @override
  String get fbKindProject => 'مشروع';

  @override
  String get fbKindTopic => 'موضوع';

  @override
  String get fbKindEvent => 'حدث';

  @override
  String get fbKindOther => 'عنصر';

  @override
  String get fbMemoryTitle => 'الذاكرة';

  @override
  String fbMemoryFromFactsBook(int count) {
    return '$count بانتظارك';
  }

  @override
  String get fbMemoryFromFactsBookNone => 'من FactsBook';

  @override
  String fbMemoryReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تغيير للمراجعة',
      many: '$count تغييرًا للمراجعة',
      few: '$count تغييرات للمراجعة',
      two: 'تغييران للمراجعة',
      one: 'تغيير واحد للمراجعة',
    );
    return '$_temp0';
  }

  @override
  String fbMemoryNotSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملاحظة لم تُحفظ بعد',
      many: '$count ملاحظةً لم تُحفظ بعد',
      few: '$count ملاحظات لم تُحفظ بعد',
      two: 'ملاحظتان لم تُحفظا بعد',
      one: 'ملاحظة واحدة لم تُحفظ بعد',
    );
    return '$_temp0';
  }

  @override
  String get fbMemoryNotSavedDetail => 'محفوظة على هذا الهاتف';

  @override
  String get fbMemoryReviewDetail => 'بانتظار موافقتك';

  @override
  String get fbMemoryRecent => 'أُضيف مؤخرًا إلى الذاكرة';

  @override
  String get fbMemoryOpenAboutYou => 'افتح «عنّي»';

  @override
  String get fbMemoryOpenAboutYouDetail => 'ابحث وتصفّح وشاهد ما ينتظر';

  @override
  String get fbMemoryAddSomething => 'أضف شيئًا';

  @override
  String get fbMemoryAddSomethingDetail => 'ملاحظة أو نص ملصوق';

  @override
  String get fbAboutYouTitle => 'عنّي';

  @override
  String get fbStatusStale => 'يُعرض آخر ما حُفظ';

  @override
  String get fbSearchLabel => 'ابحث في ما يعرفه FactsBook';

  @override
  String get fbEmptyTitle => 'لا شيء هنا بعد';

  @override
  String get fbEmptyBody =>
      'أضف ملاحظة، أو شارك نصًا من تطبيق آخر، أو ألصق نصًا من مساعد آخر. ما تضيفه يظهر هنا مع مصدره.';

  @override
  String get fbListAll => 'الكل';

  @override
  String get fbListCards => 'البطاقات';

  @override
  String get fbListPeople => 'الأشخاص';

  @override
  String get fbListDocuments => 'المستندات';

  @override
  String get fbListLockedNote =>
      'الملاحظات المقفلة غير معروضة. افتحها ببصمتك أو رمزك لتضمينها.';

  @override
  String fbKindLine(String kind, String detail) {
    return '$kind · $detail';
  }

  @override
  String get fbAreaMoney => 'منطقة المال';

  @override
  String get fbAreaHealth => 'منطقة الصحة';

  @override
  String fbAreaOther(String area) {
    return 'منطقة $area';
  }

  @override
  String get fbErrorTitle => 'لم يستجب FactsBook';

  @override
  String get fbErrorUnreachable =>
      'تعذّر الوصول إلى FactsBook. ما تضيفه يبقى على هذا الهاتف إلى أن يُحفظ.';

  @override
  String get fbErrorLocked => 'هذا في المجلد المقفل. افتحه أولًا.';

  @override
  String get fbErrorNotFound => 'لم يعد هذا في الذاكرة.';

  @override
  String get fbErrorGeneric => 'حدث خطأ ما. حاول مرة أخرى.';

  @override
  String fbErrorRequest(String id) {
    return 'الطلب $id';
  }

  @override
  String get fbSearchTitle => 'بحث';

  @override
  String fbSearchMatches(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نتيجة لـ «$query»',
      many: '$count نتيجةً لـ «$query»',
      few: '$count نتائج لـ «$query»',
      two: 'نتيجتان لـ «$query»',
      one: 'نتيجة واحدة لـ «$query»',
      zero: 'لا نتائج لـ «$query»',
    );
    return '$_temp0';
  }

  @override
  String get fbSearchWordsOnly => 'يطابق الكلمات في ملاحظاتك. لا يبحث بالمعنى.';

  @override
  String get fbSearchEachHit =>
      'تُظهر كل نتيجة مصدرها ووقتها. وقد يكون المصدر مخطئًا.';

  @override
  String get fbSearchLocked => 'لا يُبحث في الملاحظات المقفلة حتى تفتحها.';

  @override
  String get fbSearchNoneTitle => 'لا شيء يطابق';

  @override
  String get fbSearchNoneBody => 'جرّب كلمات أخرى.';

  @override
  String get fbThingFacts => 'الحقائق';

  @override
  String get fbThingNoFacts => 'لا حقائق بعد.';

  @override
  String get fbThingLocked => 'المجلد المقفل';

  @override
  String get fbCaptureTitle => 'أضف شيئًا';

  @override
  String get fbCaptureKindLabel => 'ما تضيفه';

  @override
  String get fbCaptureNote => 'ملاحظة';

  @override
  String get fbCapturePaste => 'لصق';

  @override
  String get fbCaptureNoteLabel => 'ماذا يجب أن يتذكر؟';

  @override
  String get fbCaptureNoteHint =>
      'موعد طبيب الأسنان الأربعاء القادم الساعة 10. أحضر بطاقة التأمين.';

  @override
  String get fbCapturePasteLabel => 'ألصق نصًا من تطبيق أو مساعد آخر';

  @override
  String get fbCaptureSave => 'حفظ';

  @override
  String get fbCaptureFootnote =>
      'أي شيء يُشارك من تطبيق آخر يصل إلى هنا أيضًا. الملاحظة التي لا يمكن حفظها الآن تنتظر في «لم يُحفظ بعد» ولا تختفي.';

  @override
  String get fbCaptureSaved =>
      'حُفظت. ستفحصها الذاكرة وتعرض عليك التغييرات لتراجعها.';

  @override
  String fbCaptureSavedReminder(String when) {
    return 'تم الحفظ مع تذكير في $when.';
  }

  @override
  String get fbCaptureKept => 'تعذّر حفظه الآن. هو محفوظ على هذا الهاتف.';

  @override
  String get fbCaptureSeeNotSaved => 'عرض «لم يُحفظ بعد»';

  @override
  String get fbReminderTitle => 'تحقق من هذا التذكير';

  @override
  String fbReminderWrote(String text) {
    return 'كتبتَ: «$text»';
  }

  @override
  String get fbReminderDate => 'التاريخ';

  @override
  String get fbReminderTime => 'الوقت';

  @override
  String get fbReminderZone => 'المنطقة الزمنية';

  @override
  String get fbReminderZoneHome => 'المنطقة الزمنية للمنزل';

  @override
  String get fbReminderZoneHomeDetail =>
      'يرنّ في هذا الوقت حسب منطقتك الزمنية الأم، أينما كنت.';

  @override
  String get fbReminderZoneFloating => 'توقيت هذا الهاتف';

  @override
  String get fbReminderZoneFloatingDetail => 'يبقى على نفس الساعة أينما كنت.';

  @override
  String get fbReminderLocale => 'لغة التواريخ';

  @override
  String get fbReminderRepeat => 'التكرار';

  @override
  String get fbRepeatNever => 'أبدًا';

  @override
  String get fbRepeatDaily => 'كل يوم';

  @override
  String get fbRepeatWeekly => 'كل أسبوع';

  @override
  String get fbRepeatMonthly => 'كل شهر';

  @override
  String get fbRepeatYearly => 'كل سنة';

  @override
  String get fbReminderAmbiguousDate => 'لم أتمكن من تحديد التاريخ. تحقق منه.';

  @override
  String get fbReminderAmbiguousTime => 'لم أتمكن من تحديد الوقت. تحقق منه.';

  @override
  String get fbReminderHelp => 'غيّر أي شيء خاطئ قبل الحفظ.';

  @override
  String get fbReminderSave => 'حفظ التذكير';

  @override
  String get fbReminderChange => 'تعديل ما كتبته';

  @override
  String get fbReminderWhat => 'ذكّرني بـ';

  @override
  String get fbNeedsYouTitle => 'يحتاجك';

  @override
  String fbNeedsYouWaiting(int count) {
    return '$count بانتظارك';
  }

  @override
  String get fbNeedsYouNone => 'لا شيء من FactsBook يحتاج إليك.';

  @override
  String get fbNeedsYouNoneBody =>
      'تظهر هنا الأسئلة والتغييرات التي تنتظر المراجعة والملاحظات التي لم تُحفظ بعد.';

  @override
  String get fbNotSavedTitle => 'لم يُحفظ بعد';

  @override
  String fbNotSavedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر محفوظ على هذا الهاتف',
      many: '$count عنصرًا محفوظًا على هذا الهاتف',
      few: '$count عناصر محفوظة على هذا الهاتف',
      two: 'عنصران محفوظان على هذا الهاتف',
      one: 'عنصر واحد محفوظ على هذا الهاتف',
    );
    return '$_temp0';
  }

  @override
  String get fbNotSavedIntro =>
      'محفوظة حتى تتجاهلها: على هذا الهاتف أو لدى FactsBook. لا شيء منها في سحابتك بعد.';

  @override
  String get fbNotSavedFooter =>
      'تبقى هنا حتى تتجاهلها. بعد 30 يومًا يسألك FactsBook: إعادة المحاولة أو التصدير أو التجاهل.';

  @override
  String get fbNotSavedEmptyTitle => 'كل شيء محفوظ';

  @override
  String get fbNotSavedEmptyBody =>
      'الملاحظات التي لا يمكن حفظها الآن ستنتظر هنا.';

  @override
  String get fbNotSavedTryNow => 'حاول الآن';

  @override
  String get fbNotSavedCopy => 'نسخ النص';

  @override
  String get fbNotSavedDiscard => 'تجاهل';

  @override
  String get fbNotSavedRetry => 'إعادة المحاولة';

  @override
  String get fbNotSavedExport => 'تصدير';

  @override
  String get fbNotSavedThirtyDays =>
      'بقي هذا 30 يومًا. أعد المحاولة أو صدّر النص أو تجاهله.';

  @override
  String get fbNotSavedDiscardTitle => 'تجاهل هذه الملاحظة؟';

  @override
  String get fbNotSavedDiscardBody =>
      'لم تُحفظ أبدًا. تجاهلها يزيلها من هذا الهاتف.';

  @override
  String get fbNotSavedSaved => 'تم الحفظ.';

  @override
  String get fbNotSavedStill => 'لم تُحفظ بعد. تبقى هنا.';

  @override
  String fbNotSavedSince(String when) {
    return 'منذ $when';
  }

  @override
  String get fbReasonUnreachable => 'تعذّر الوصول إلى FactsBook.';

  @override
  String get fbReasonStorageFull =>
      'هذا الهاتف ممتلئ. أفرغ بعض المساحة ثم حاول مرة أخرى.';

  @override
  String get fbReasonCloudQuota =>
      'سحابتك ممتلئة. أفرغ بعض المساحة ثم حاول مرة أخرى.';

  @override
  String get fbReasonKeyLocked => 'المجلد المقفل مقفل. افتحه ثم حاول مرة أخرى.';

  @override
  String get fbReasonRejected => 'تعذّر على FactsBook قبوله.';

  @override
  String get fbReasonClassification =>
      'تعذّر على FactsBook تصنيفه الآن. حاول مرة أخرى.';

  @override
  String get fbDismiss => 'إغلاق';

  @override
  String get fbCaptureNeedsText => 'اكتب شيئًا أولًا.';

  @override
  String get fbAreaFamily => 'منطقة العائلة';

  @override
  String get fbAreaWork => 'منطقة العمل';

  @override
  String get fbAreaNameMoney => 'المال';

  @override
  String get fbAreaNameHealth => 'الصحة';

  @override
  String get fbAreaNameFamily => 'العائلة';

  @override
  String get fbAreaNameWork => 'العمل';

  @override
  String get fbAreasTitle => 'المناطق والمشاركة';

  @override
  String get fbAreasIntro =>
      'لكل منطقة قواعدها. لا تقرأ التطبيقات والوكلاء الآخرون منطقة إلا إذا سمحت لهم.';

  @override
  String get fbAreasOthersRead => 'يمكن للتطبيقات والوكلاء الآخرين قراءتها';

  @override
  String get fbAreasOthersReadDetail => 'أطفئه لتبقى هذه المنطقة لك وحدك.';

  @override
  String get fbAreasLockedFolder => 'المجلد المقفل';

  @override
  String get fbAreasNormalStorage => 'تخزين عادي';

  @override
  String get fbAreasReviewFirst => 'تنتظر الحقائق الجديدة موافقتك أولًا.';

  @override
  String get fbAreasReviewAuto => 'لا يحدث من تلقاء نفسه إلا التنظيف الآمن.';

  @override
  String get fbAreasFixed => 'هذه المجالات مدمجة.';

  @override
  String fbAreasShareConfirmTitle(String area) {
    return 'السماح للآخرين بقراءة $area؟';
  }

  @override
  String get fbAreasShareConfirmBody =>
      'ستتمكن التطبيقات والوكلاء الذين ربطتهم من قراءة ما في هذه المنطقة. يمكنك إيقافه لاحقًا.';

  @override
  String get fbAreasShareConfirm => 'السماح بالقراءة';

  @override
  String get fbCaptureFull =>
      'قائمة الانتظار على هذا الهاتف ممتلئة، لذلك لم تُحفظ الملاحظة. نصّك ما زال هنا. أرسل ملاحظة منتظرة أو تجاهلها أولًا.';

  @override
  String get fbCaptureQueuedNode =>
      'يحتفظ بها FactsBook إلى أن يتمكن من حفظها.';

  @override
  String get fbCaptureReminderFailed => 'لم يُنشأ التذكير. حاول مرة أخرى.';

  @override
  String fbCareWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count بانتظار موافقتك',
      many: '$count بانتظار موافقتك',
      few: '$count بانتظار موافقتك',
      two: 'اثنان بانتظار موافقتك',
      one: 'واحد بانتظار موافقتك',
    );
    return '$_temp0';
  }

  @override
  String get fbCareApprove => 'موافقة';

  @override
  String get fbCareReject => 'رفض';

  @override
  String get fbCareApproved => 'تمت الموافقة.';

  @override
  String get fbCareRejected => 'رُفض. لن يُقترح مرة أخرى.';

  @override
  String get fbCareSkipped => 'تم التخطي. يبقى هنا.';

  @override
  String get fbCareStale =>
      'تغيّر ذلك بعد اقتراحه، لذلك لم يُطبَّق شيء. أُزيل من هذه القائمة.';

  @override
  String get fbCareNeedsOwner =>
      'لا يستطيع هذا الهاتف الموافقة على التغييرات بعد. أعدّ الموافقة للمتابعة.';

  @override
  String get fbCareNeedsOwnerShort => 'أعدّ الموافقة أولًا';

  @override
  String get fbCareSetupApproval => 'إعداد الموافقة';

  @override
  String get fbCareEditTitle => 'عدّل قبل الموافقة';

  @override
  String get fbCareEditLabel => 'الحقيقة';

  @override
  String get fbCareEditSave => 'موافقة مع تعديلي';

  @override
  String get fbCareClassFactAdd => 'إضافة حقيقة';

  @override
  String get fbCareClassFactUpdate => 'تغيير حقيقة';

  @override
  String get fbCareClassFactSupersede => 'استبدال حقيقة';

  @override
  String get fbCareClassMerge => 'دمج عنصرين';

  @override
  String get fbCareClassLink => 'ربط عنصرين';

  @override
  String get fbCareClassArchive => 'أرشفة';

  @override
  String get fbCareClassForget => 'نسيان';

  @override
  String get fbCareClassWatch => 'مراقبة شيء ما';

  @override
  String get fbCareClassReminder => 'تذكير';

  @override
  String get fbCareClassImport => 'من نص ملصوق';

  @override
  String get fbCareClassPromotion => 'حقيقة لمنطقة أكثر صرامة';

  @override
  String get fbCareRiskMoney => 'مال';

  @override
  String get fbCareRiskHealth => 'صحة';

  @override
  String get fbCareRiskId => 'هوية';

  @override
  String get fbCareRiskContradiction => 'يتعارض مع شيء قلته';

  @override
  String get fbCareRiskLowConfidence => 'غير متأكد';

  @override
  String get fbConnectTitle => 'الاتصال بخادمي الخاص';

  @override
  String get fbConnectRow => 'إعداد الذاكرة';

  @override
  String get fbConnectRowDetail => 'ملاحظاتك على هذا الهاتف';

  @override
  String get fbConnectIntro =>
      'صل هذا التطبيق بخادم ذاكرة على شبكتك المنزلية أو شبكة tailnet أو على هذا الهاتف.';

  @override
  String get fbConnectAddress => 'عنوان العقدة';

  @override
  String get fbConnectAddressHint => '127.0.0.1:8787';

  @override
  String get fbConnectCode => 'رمز الاقتران لمرة واحدة';

  @override
  String get fbConnectCodeHelp =>
      'تطبعه العقدة عند تشغيل أمر الاقتران. يعمل مرة واحدة ولمدة خمس دقائق.';

  @override
  String get fbConnectOwnerTitle =>
      'الموافقة على التغييرات من هذا الهاتف (اختياري)';

  @override
  String get fbConnectOwnerBody =>
      'تُوقَّع مراجعة الاقتراحات وتغيير المشاركة بمفتاح هذا الهاتف نفسه. أعطِ العقدة المفتاح العام أدناه، ثم أدخل معرّف جهاز المالك الذي تعيده.';

  @override
  String get fbConnectOwnerKey => 'المفتاح العام لهذا الهاتف';

  @override
  String get fbConnectOwnerId => 'معرّف جهاز المالك';

  @override
  String get fbConnectButton => 'اتصال';

  @override
  String get fbConnectNeeds => 'أدخل العنوان والرمز أولًا.';

  @override
  String get fbConnectFailedTitle => 'تعذّر الاتصال';

  @override
  String fbDisconnect(String name) {
    return 'فصل $name';
  }

  @override
  String get fbDisconnectDetail =>
      'انسَ الاقتران والرمز ومفتاح الموافقة على هذا الهاتف';

  @override
  String fbDisconnectTitle(String name) {
    return 'فصل $name؟';
  }

  @override
  String get fbDisconnectBody =>
      'ينسى هذا الهاتف اقتران العقدة ورمزها ومفتاح الموافقة. تبقى ملاحظات العقدة في مكانها، وتبقى الملاحظات المنتظرة على هذا الهاتف حتى تتجاهلها.';

  @override
  String get fbErrorAddress =>
      'هذا العنوان ليس على شبكة خاصة. استخدم هذا الهاتف أو شبكتك المنزلية أو شبكتك الخاصة.';

  @override
  String get fbErrorBadResponse =>
      'أجاب FactsBook بطريقة لا يفهمها هذا التطبيق. حدّث التطبيق أو العقدة.';

  @override
  String get fbErrorForbidden => 'لا يُسمح لهذا الهاتف بفعل ذلك.';

  @override
  String get fbErrorNotEnabled => 'هذا الخيار متوقف في هذه الذاكرة.';

  @override
  String get fbErrorOwner =>
      'لا يستطيع هذا الهاتف الموافقة على التغييرات بعد. أعدّ الموافقة أولًا.';

  @override
  String get fbErrorPairCode =>
      'لم ينجح هذا الرمز. اطلب رمزًا جديدًا من العقدة.';

  @override
  String get fbErrorSlow => 'FactsBook مشغول. حاول بعد قليل.';

  @override
  String get fbErrorStale =>
      'تغيّر ذلك منذ نظرت إليه. انظر مرة أخرى ثم حاول مجددًا.';

  @override
  String get fbErrorStarting => 'FactsBook قيد البدء. حاول بعد قليل.';

  @override
  String get fbErrorUnauthorized =>
      'هذا الهاتف غير مسجّل دخوله في FactsBook. أعدّ الإعداد.';

  @override
  String fbErrorCode(String code) {
    return 'الرمز $code';
  }

  @override
  String get fbKeyLocked => 'مقفل';

  @override
  String get fbKeyUnlocked => 'مفتوح';

  @override
  String get fbKindInvoice => 'مطالبة';

  @override
  String get fbKindHealth => 'صحة';

  @override
  String get fbModuleStorage => 'التخزين';

  @override
  String get fbModuleCloud => 'النسخ الاحتياطي السحابي';

  @override
  String get fbModuleCare => 'يحتاجك';

  @override
  String get fbNotSavedAtNode => 'يحتفظ بها FactsBook';

  @override
  String get fbNotSavedOnPhone => 'على هذا الهاتف';

  @override
  String get fbNotSavedNodeItem => 'ملاحظة يحتفظ بها FactsBook';

  @override
  String get fbReasonModuleNotConfigured => 'لا شيء متصل بعد';

  @override
  String get fbReasonModuleNotEnabled => 'لم يُفعَّل بعد';

  @override
  String get fbReasonOutboxFull =>
      'قائمة الانتظار على هذا الهاتف ممتلئة. أرسل ملاحظة منتظرة أو تجاهلها.';

  @override
  String get fbReasonParserError =>
      'تعذّر على FactsBook قراءتها. حاول مرة أخرى.';

  @override
  String get fbReminderMismatch =>
      'قرأ FactsBook هذا بشكل مختلف. تحقق من التاريخ والوقت والمنطقة ثم حاول مرة أخرى.';

  @override
  String fbReminderPause(String what) {
    return 'إيقاف «$what» مؤقتًا';
  }

  @override
  String fbReminderResume(String what) {
    return 'استئناف «$what»';
  }

  @override
  String get fbRemindersTitle => 'التذكيرات';

  @override
  String get fbRemindersActive => 'نشطة';

  @override
  String get fbRemindersPaused => 'متوقفة مؤقتًا';

  @override
  String get fbRemindersDone => 'منتهية';

  @override
  String get fbRemindersEmptyTitle => 'لا تذكيرات بعد';

  @override
  String get fbRemindersEmptyBody =>
      'أضف تذكيرًا، أو فعّل «ذكّرني بها أيضًا» عند إضافة ملاحظة، فيظهر هنا.';

  @override
  String get fbSourceSaved => 'شيء حفظته';

  @override
  String get fbStateReady => 'جاهز';

  @override
  String get fbStateWaiting => 'بانتظار';

  @override
  String get fbStateStale => 'قديم';

  @override
  String get fbStateFailed => 'فشل';

  @override
  String get fbStateOff => 'متوقف';

  @override
  String get fbStateNotSetUp => 'غير مُعدّ';

  @override
  String get fbStatusTitle => 'الحالة';

  @override
  String get fbStatusSearch => 'البحث';

  @override
  String get fbStatusBackup => 'النسخ الاحتياطي';

  @override
  String get fbStatusBackedUp => 'تم النسخ الاحتياطي';

  @override
  String get fbStatusNoBackup => 'لا نسخ احتياطي بعد';

  @override
  String get fbStatusThisPhone => 'هذا الهاتف';

  @override
  String get fbStatusApprove => 'الموافقة على التغييرات من هذا الهاتف';

  @override
  String fbStatusSince(String when) {
    return 'تم التحديث $when';
  }

  @override
  String get fbThingWaitingNote =>
      'المعلومات المعلّمة «بانتظار موافقتك» موجودة في «يحتاجك».';

  @override
  String fbWhenDaily(String time) {
    return 'كل يوم في $time';
  }

  @override
  String get fbWhenCustom => 'جدول مخصّص';

  @override
  String get fbDeveloperFactsBook => 'FactsBook (معاينة)';

  @override
  String get fbDeveloperFactsBookDetail =>
      'يعرض قسم الذاكرة في محادثاتك. متوقف افتراضيًا.';

  @override
  String get fbDeveloperTitle => 'المطوّر';

  @override
  String get fbErrorBadRequest =>
      'لم يقبل FactsBook ذلك. تحقق مما أدخلته وحاول مرة أخرى.';

  @override
  String get fbErrorNodeFull =>
      'قائمة الانتظار في FactsBook ممتلئة. احتفظ بنصّك وحاول لاحقًا.';

  @override
  String get fbErrorIndexRebuilding => 'البحث قيد التجهيز. حاول بعد قليل.';

  @override
  String get fbErrorAuthUnavailable =>
      'لا توجد بصمة أو وجه أو قفل شاشة على هذا الهاتف، لذلك لا يمكنه الموافقة على التغييرات. أعدّ واحدًا في إعدادات الهاتف ثم حاول مرة أخرى.';

  @override
  String get fbErrorAuthCancelled => 'أُلغيت الموافقة. لم يتغيّر شيء.';

  @override
  String get fbErrorAuthLocked =>
      'محاولات خاطئة كثيرة. انتظر قليلًا ثم حاول مرة أخرى.';

  @override
  String get fbErrorAuthFailed =>
      'تعذّر على الهاتف التأكد من أنك أنت. لم يتغيّر شيء.';

  @override
  String fbErrorSlowAfter(int seconds) {
    return 'FactsBook مشغول. حاول بعد $seconds ثانية.';
  }

  @override
  String get fbAuthReason => 'وافق على هذا التغيير في FactsBook';

  @override
  String fbWhenWeeklyOn(String days, String time) {
    return 'كل أسبوع يوم $days في $time';
  }

  @override
  String fbWhenMonthLast(String time) {
    return 'كل شهر في اليوم الأخير في $time';
  }

  @override
  String fbWhenMonthlyOn(int day, String time) {
    return 'كل شهر في اليوم $day في $time';
  }

  @override
  String fbWhenYearlyOn(String date, String time) {
    return 'كل سنة في $date في $time';
  }

  @override
  String get fbReminderNeedsTitle => 'اكتب ما تريد أن يُذكَّرك به أولًا.';

  @override
  String get fbReminderWhatHint => 'اتصل بطبيب الأسنان';

  @override
  String get fbReminderDayOfMonth => 'يوم الشهر';

  @override
  String get fbReminderLastDay => 'اليوم الأخير';

  @override
  String get fbRemindersAdd => 'أضف تذكيرًا';

  @override
  String fbThingOriginally(String source, String date) {
    return 'قُرئ أصلًا من $source · $date';
  }

  @override
  String get fbExportTitle => 'تصدير هذه الملاحظة';

  @override
  String get fbExportBody =>
      'هذه هي الملاحظة ككائن مشفّر. لا تفتح المجلد المقفل، وفتحها يحتاج تفاصيل الاسترداد من وقت إعداد FactsBook على جهازه.';

  @override
  String get fbExportObject => 'كائن مشفّر';

  @override
  String get fbExportCopy => 'نسخ التصدير';

  @override
  String get fbSettingsTitle => 'إعدادات FactsBook';

  @override
  String get fbSettingsBrief => 'الموجز الصباحي';

  @override
  String get fbSettingsBriefCap => 'عدد العناصر في اليوم';

  @override
  String get fbSettingsBriefCapDetail => 'أقصى ما يعرضه الموجز في يوم واحد.';

  @override
  String get fbSettingsBriefOff => 'لا شيء';

  @override
  String fbSettingsBriefItems(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر',
      many: '$count عنصرًا',
      few: '$count عناصر',
      two: 'عنصران',
      one: 'عنصر واحد',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsBriefTime => 'وقت الموجز';

  @override
  String get fbSettingsQuietHours => 'ساعات الهدوء';

  @override
  String get fbSettingsPrivacy => 'الخصوصية';

  @override
  String get fbSettingsLockTimeout => 'يبقى المجلد المقفل مفتوحًا لمدة';

  @override
  String fbSettingsMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count دقيقة',
      many: '$count دقيقة',
      few: '$count دقائق',
      two: 'دقيقتان',
      one: 'دقيقة واحدة',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsAreasDetail => 'من يمكنه قراءة كل منطقة غيرك';

  @override
  String get fbSettingsThisNode => 'هذه العقدة';

  @override
  String get fbSettingsHomeZone => 'المنطقة الزمنية للمنزل';

  @override
  String get fbSettingsSpending => 'حد الإنفاق';

  @override
  String get fbSettingsSpendingNone => 'لا يُسمح بإنفاق أي شيء';

  @override
  String get fbSettingsFootnote =>
      'يُضبط وقت القفل والمنطقة الزمنية على العقدة. التغييرات هنا تحتاج موافقة هذا الهاتف.';

  @override
  String get fbSettingsNeedsOwner =>
      'لا يستطيع هذا الهاتف الموافقة على التغييرات بعد، لذلك تُعرض الإعدادات ولا تتغيّر. أعدّ الموافقة لتغييرها.';

  @override
  String get fbModuleReplication => 'النسخ المتماثل';

  @override
  String get fbStatusChecked => 'آخر فحص';

  @override
  String get fbStatusUnknown => 'غير معروف';

  @override
  String get fbStatusHistory => 'السجل الموقّع';

  @override
  String get fbStatusHistoryNotChecked => 'لم يُفحص';

  @override
  String get fbStatusHistoryVerified =>
      'تم التحقق منه بالمفتاح المثبّت عند الاقتران';

  @override
  String get fbStatusHistoryFailed =>
      'تعذّر التحقق منه. لا توافق على شيء حتى يتحقق.';

  @override
  String get fbStatusHistoryForked => 'وُجد سجلّان متنافسان. الموافقات متوقفة.';

  @override
  String get fbStatusNoReplication =>
      'النسخ المتماثل متوقف، لذلك توجد ملاحظاتك على هذه العقدة فقط، ولا يمكن معرفة مدى حداثة أي نسخة سحابية.';

  @override
  String get fbStatusNotYet => 'غير مفعّل';

  @override
  String get fbNotYetCloud => 'المزامنة والنسخ الاحتياطي السحابيان';

  @override
  String get fbNotYetUnlock => 'فتح المجلد المقفل';

  @override
  String get fbNotYetAlarms => 'تنبيهات التذكير على هذا الهاتف';

  @override
  String get fbNotAvailableYet => 'متوقف';

  @override
  String get fbReminderClockNote =>
      'إذا لم يوجد الوقت في ذلك اليوم بسبب تغيير الساعة، يُنفَّذ في أول وقت متاح.';

  @override
  String get fbCaptureReminderNotMade =>
      'لم يُنشأ التذكير بعد. أضفه مرة أخرى بعد حفظ الملاحظة.';

  @override
  String get fbNotSavedDiscardBodyNode =>
      'سيحذف FactsBook النسخة التي يحتفظ بها. لم تُحفظ أبدًا.';

  @override
  String get fbAreaNameGeneral => 'عام';

  @override
  String get fbAreaNameHome => 'المنزل';

  @override
  String get fbAreaNameTravel => 'السفر';

  @override
  String get fbAreaNameUnsorted => 'غير مصنّف';

  @override
  String get fbAreaNameId => 'الهوية';

  @override
  String get fbModuleIndex => 'فهرس البحث';

  @override
  String get fbModuleMembership => 'الأجهزة';

  @override
  String get fbTriggersTitle => 'عند وصول شيء جديد';

  @override
  String get fbTriggersSettingsDetail =>
      'قواعد تقترح تذكيرات ومحادثات من الرسائل التي تشاركها';

  @override
  String get fbTriggersUnavailableTitle => 'القواعد غير مفعّلة في هذه الذاكرة';

  @override
  String get fbTriggersUnavailableBody =>
      'هذه الذاكرة لا تعرض قواعد. حدّثها لتستخدمها.';

  @override
  String get fbTriggersEmptyTitle => 'لا توجد قواعد بعد';

  @override
  String get fbTriggersEmptyBody =>
      'القاعدة تراقب نوعًا واحدًا من الرسائل وتقترح فقط. لا يُحفظ شيء ولا يُرسل قبل موافقتك.';

  @override
  String get fbTriggersNew => 'قاعدة جديدة';

  @override
  String fbTriggersGroupShare(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قاعدة',
      many: '$count قاعدةً',
      few: '$count قواعد',
      two: 'قاعدتان',
      one: 'قاعدة واحدة',
    );
    return 'نص تشاركه · $_temp0';
  }

  @override
  String fbTriggersGroupChat(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قاعدة',
      many: '$count قاعدةً',
      few: '$count قواعد',
      two: 'قاعدتان',
      one: 'قاعدة واحدة',
    );
    return 'محادثات تحفظها · $_temp0';
  }

  @override
  String fbTriggersGroupNote(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قاعدة',
      many: '$count قاعدةً',
      few: '$count قواعد',
      two: 'قاعدتان',
      one: 'قاعدة واحدة',
    );
    return 'ملاحظات تكتبها · $_temp0';
  }

  @override
  String get fbTriggersRecent => 'رسائل حديثة';

  @override
  String get fbTriggersFootnote =>
      'القاعدة تقترح فقط. لا يُحفظ شيء ولا يُرسل قبل موافقتك. العناصر المتجاهلة تُعدّ ولا تُحفظ.';

  @override
  String fbRuleFinds(String template, String outcomes) {
    return 'يبحث عن $template ← $outcomes';
  }

  @override
  String get fbTemplateAppointment => 'موعد';

  @override
  String get fbTemplateDelivery => 'توصيل';

  @override
  String get fbTemplateBooking => 'حجز';

  @override
  String get fbTemplateSubscription => 'تجديد اشتراك';

  @override
  String get fbTemplateBill => 'استحقاق فاتورة';

  @override
  String get fbOutcomeReminder => 'اقتراح تذكير';

  @override
  String get fbOutcomeFact => 'اقتراح معلومة';

  @override
  String get fbOutcomeConversation => 'عرض الحديث عنها';

  @override
  String fbRuleWeek(int proposed, int ignored) {
    return 'هذا الأسبوع: $proposed مقترح، $ignored متجاهل';
  }

  @override
  String fbRuleWeekOffers(int proposed, int ignored, int offers) {
    String _temp0 = intl.Intl.pluralLogic(
      offers,
      locale: localeName,
      other: '$offers عرض',
      many: '$offers عرضًا',
      few: '$offers عروض',
      two: 'عرضان',
      one: 'عرض واحد',
    );
    return 'هذا الأسبوع: $proposed مقترح، $ignored متجاهل، $_temp0';
  }

  @override
  String get fbRulePaused => 'متوقفة مؤقتًا';

  @override
  String fbRulePause(String name) {
    return 'إيقاف «$name» مؤقتًا';
  }

  @override
  String fbRuleResume(String name) {
    return 'استئناف «$name»';
  }

  @override
  String get fbRulePausedDone => 'أُوقفت القاعدة مؤقتًا.';

  @override
  String get fbRuleResumedDone => 'عادت القاعدة تعمل.';

  @override
  String get fbSourceGroupShare => 'نص تشاركه';

  @override
  String get fbSourceGroupChat => 'محادثة حفظتها';

  @override
  String get fbSourceGroupNote => 'ملاحظة كتبتها';

  @override
  String fbEventWhen(String source, String when) {
    return '$source · $when';
  }

  @override
  String get fbReasonMatched => 'طابقت قاعدة هذه الرسالة.';

  @override
  String get fbReasonNoMatch => 'لم تطابق أي قاعدة هذه الرسالة.';

  @override
  String get fbReasonUncertain =>
      'لم يستطع FactsBook قراءتها بأمان، فلم يحتفظ بشيء ولم يقترح شيئًا.';

  @override
  String get fbReasonSensitive =>
      'بدت خاصة والملاحظات المقفلة مغلقة، فجرى تنحيتها جانبًا.';

  @override
  String get fbReasonSourceCap =>
      'وصلت رسائل كثيرة من هذا المصدر اليوم، فجرى عدّ هذه فقط.';

  @override
  String get fbReasonVaultCap => 'وصلت رسائل كثيرة اليوم، فجرى عدّ هذه فقط.';

  @override
  String get fbReasonRuleCap =>
      'بلغت هذه القاعدة حدّها اليومي، فجرى عدّ هذه فقط.';

  @override
  String get fbReasonStorm =>
      'وصلت رسائل متشابهة كثيرة معًا، فجرى دمجها في واحدة.';

  @override
  String get fbReasonNoRule => 'لا توجد قاعدة مفعّلة لهذا المصدر.';

  @override
  String get fbRuleNewTitle => 'قاعدة جديدة';

  @override
  String get fbRuleEditTitle => 'تعديل القاعدة';

  @override
  String get fbRuleNameLabel => 'اسم القاعدة';

  @override
  String get fbRuleNameHint => 'مثال: مواعيد المكتبة';

  @override
  String get fbRuleWhen => 'عندما';

  @override
  String get fbRuleWhenLabel => 'مصدر الرسالة';

  @override
  String get fbSourceKindShare => 'نص مشارَك';

  @override
  String get fbSourceKindChat => 'محادثة محفوظة';

  @override
  String get fbSourceKindNote => 'ملاحظة';

  @override
  String get fbRuleSays => 'يقول شيئًا مثل';

  @override
  String get fbRuleTemplateLabel => 'نوع الرسالة';

  @override
  String get fbRuleLanguageLabel => 'لغة الرسالة';

  @override
  String get fbRuleLangEn => 'الإنجليزية';

  @override
  String get fbRuleLangAr => 'العربية';

  @override
  String get fbRuleTemplateHelp =>
      'يقرأ FactsBook سطرًا واحدًا مثل «موعد: زيارة المكتبة | 2026-10-15T09:00».';

  @override
  String get fbRuleWordsLabel => 'كلمات يجب أن ترد فيها (اختياري)';

  @override
  String get fbRuleWordsHelp =>
      'حتى 4 كلمات تفصل بينها فواصل. نص عادي وليس شيفرة.';

  @override
  String get fbRuleThen => 'ثم';

  @override
  String get fbRuleOutcomeReminder => 'اقتراح تذكير';

  @override
  String get fbRuleOutcomeReminderDetail => 'تؤكد التاريخ والوقت أولًا.';

  @override
  String get fbRuleOutcomeConversation => 'عرض الحديث عنها';

  @override
  String get fbRuleOutcomeConversationDetail => 'تقرر أنت هل تُفتح محادثة.';

  @override
  String get fbRuleOutcomeFactKept =>
      'تقترح هذه القاعدة معلومة أيضًا. تُضبط المعلومات على خادم FactsBook، وتبقى كما هي.';

  @override
  String get fbRuleAutoStart => 'ابدأ هذه المحادثات تلقائيًا';

  @override
  String get fbRuleAutoStartOff =>
      'تُعرض المحادثات فقط ولا تبدأ من تلقاء نفسها.';

  @override
  String get fbRuleOn => 'هذه القاعدة تعمل';

  @override
  String get fbRuleOnDetail =>
      'أوقفها لإيقافها مؤقتًا. لا يُزال ما اقترحته سابقًا.';

  @override
  String get fbRuleLimit => 'الحد الأقصى يوميًا';

  @override
  String fbRuleLimitValue(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count في اليوم',
      many: '$count في اليوم',
      few: '$count في اليوم',
      two: '2 في اليوم',
      one: '1 في اليوم',
    );
    return '$_temp0';
  }

  @override
  String get fbRuleTest => 'جرّبها على عيّنة';

  @override
  String get fbRuleSampleLabel => 'رسالة تجريبية';

  @override
  String get fbRuleSampleExample => 'استخدم مثالًا';

  @override
  String get fbRuleTestRun => 'اختبار';

  @override
  String get fbRuleTestAgain => 'اختبر مجددًا';

  @override
  String fbRuleTestMatch(String summary, String when) {
    return 'تطابق. وجدت: $summary، $when.';
  }

  @override
  String get fbRuleTestMatchPlain => 'تطابق.';

  @override
  String fbRuleTestWould(String outcomes) {
    return 'ستقترح: $outcomes. الاختبار لا يحفظ شيئًا.';
  }

  @override
  String get fbRuleTestNoMatch => 'لا تطابق.';

  @override
  String get fbRuleSave => 'حفظ القاعدة';

  @override
  String get fbRuleSaved => 'حُفظت القاعدة.';

  @override
  String get fbRuleDelete => 'حذف القاعدة';

  @override
  String get fbRuleDeleteTitle => 'حذف هذه القاعدة؟';

  @override
  String fbRuleDeleteBody(String name) {
    return 'تتوقف «$name» عن الاقتراح. ما اقترحته سابقًا يبقى لتقرر فيه.';
  }

  @override
  String get fbRuleNeedsOwner =>
      'لا يستطيع هذا الهاتف توقيع تغييرات القواعد بعد، فتُعرض القواعد دون تغييرها. أعدّ الموافقة لتغييرها.';

  @override
  String get fbRuleStale =>
      'تغيّرت هذه القاعدة على الخادم. أُعيد تحميلها: راجعها ثم احفظ مجددًا.';

  @override
  String get fbRuleProblemName => 'أعطِ القاعدة اسمًا لا يزيد على 80 حرفًا.';

  @override
  String get fbRuleProblemWords => 'استخدم حتى 4 كلمات، كل منها حتى 40 حرفًا.';

  @override
  String get fbRuleProblemOutcome =>
      'اختر شيئًا واحدًا على الأقل لتقترحه القاعدة.';

  @override
  String get fbRuleProblemTemplate =>
      'هذه الذاكرة لا تقرأ هذا النوع من الرسائل.';

  @override
  String get fbRuleProblemLimit => 'اختر بين 1 و20 في اليوم.';

  @override
  String get fbEventTitle => 'ما الذي حدث لهذه الرسالة';

  @override
  String get fbEventFrom => 'من أين جاءت';

  @override
  String get fbEventSender => 'المرسل (ادّعاء)';

  @override
  String get fbEventApp => 'التطبيق (ادّعاء)';

  @override
  String get fbEventTrust => 'درجة الثقة';

  @override
  String get fbTrustUntrusted => 'من شخص آخر، غير موثّقة';

  @override
  String get fbTrustSelected => 'محادثة اخترتها';

  @override
  String get fbTrustAuthored => 'كتبتها أنت';

  @override
  String get fbEventRule => 'القاعدة المطابقة';

  @override
  String get fbEventIgnored => 'ملاحظات أخرى';

  @override
  String fbEventCollapsed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'دُمجت $count رسالة متشابهة أخرى في هذه.',
      many: 'دُمجت $count رسالةً متشابهة أخرى في هذه.',
      few: 'دُمجت $count رسائل متشابهة أخرى في هذه.',
      two: 'دُمجت رسالتان متشابهتان أخريان في هذه.',
      one: 'دُمجت رسالة متشابهة واحدة أخرى في هذه.',
    );
    return '$_temp0';
  }

  @override
  String get fbEventUnverified =>
      'مرسل غير موثّق. لا يستطيع FactsBook التحقق ممن أرسل هذا النص فعلًا، لذا لن يغيّر معلومة مالية دون موافقتك.';

  @override
  String get fbEventProposed => 'ما اقترحته';

  @override
  String get fbEventFootnote =>
      'لا يُعرض النص الأصلي هنا ولا يُتبع أبدًا كتعليمات.';

  @override
  String get fbTriggerChip => 'من محفّز';

  @override
  String get fbWhyFired => 'ما الذي حدث لهذه الرسالة';

  @override
  String fbCareProposalWhen(String what, String when) {
    return '$what، $when';
  }

  @override
  String fbNeedsYouOffers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عرض من محفّزات',
      many: '$count عرضًا من محفّزات',
      few: '$count عروض من محفّزات',
      two: 'عرضان من محفّزين',
      one: 'عرض واحد من محفّز',
    );
    return '$_temp0';
  }

  @override
  String get fbOfferTitle => 'هل تريد الحديث عن هذا مع وكيلك؟';

  @override
  String fbOfferFromRule(String name) {
    return 'عرض · $name';
  }

  @override
  String fbOfferSource(String source, String date) {
    return 'من $source · $date';
  }

  @override
  String get fbOfferTalk => 'تحدث عن هذا';

  @override
  String get fbOfferNotNow => 'ليس الآن';

  @override
  String get fbOfferExplain =>
      'تُفتح محادثة جديدة مع وكيلك. يحصل على ملخص قصير لا على الرسالة كاملة. يستطيع القراءة والاقتراح دون التنفيذ. الرد أو الدفع يسألك أولًا دائمًا.';

  @override
  String get fbOfferChatLead =>
      'لاحظ FactsBook شيئًا قد ترغب في الحديث عنه. هذا ملخص قصير وليس الرسالة الأصلية:';

  @override
  String get fbOfferStale => 'تغيّر هذا العرض أو انتهى، فجرى تحديثه.';

  @override
  String get fbOfferUnknown =>
      'لم يستطع FactsBook تأكيد بدء المحادثة، فلم يُفتح شيء. لن يحاول مجددًا من تلقاء نفسه.';

  @override
  String get fbErrorBusy => 'الخادم مشغول للحظة. حاول مجددًا.';

  @override
  String get fbRuleOfferUnavailable =>
      'هذه الذاكرة لا تعرض محادثات من القواعد.';

  @override
  String get fbNodeTitle => 'استخدم الذاكرة على هذا الهاتف';

  @override
  String get fbNodeRow => 'استخدم الذاكرة على هذا الهاتف';

  @override
  String get fbNodeRowDetail =>
      'تبقى ذاكرتك على هذا الهاتف. لا يغادر شيء الهاتف.';

  @override
  String get fbNodeUnsupportedTitle => 'غير متاح على هذا الجهاز';

  @override
  String get fbNodeUnsupportedBody =>
      'تحتاج الذاكرة على الهاتف إلى «على هذا الهاتف»، وهو متوفر في هواتف أندرويد فقط.';

  @override
  String get fbNodeLinuxTitle => 'أعدّ هذا الهاتف أولًا';

  @override
  String get fbNodeLinuxBody =>
      'تعمل الذاكرة في «على هذا الهاتف». أعدّه أولًا ثم عد.';

  @override
  String get fbNodeLinuxAction => 'إعداد هذا الهاتف';

  @override
  String get fbNodeNoBuildTitle => 'لا يوجد برنامج ذاكرة لهذا الهاتف بعد';

  @override
  String get fbNodeNoBuildBody =>
      'لا يستطيع هذا الإصدار من التطبيق تثبيت الذاكرة على هذا الهاتف. حدّث التطبيق إلى إصدار أحدث، أو اتصل بخادم خاص بك أدناه.';

  @override
  String get fbNodeNotInstalledTitle => 'الذاكرة ليست على هذا الهاتف بعد';

  @override
  String get fbNodeNotInstalledBody =>
      'يثبّت التطبيق الذاكرة في «على هذا الهاتف» ويشغّلها ويتصل بها تلقائيًا. تستمع داخل هذا الهاتف فقط.';

  @override
  String get fbNodeRun => 'إعداد الذاكرة على هذا الهاتف';

  @override
  String get fbNodeStepCopying => 'تثبيت البرنامج';

  @override
  String get fbNodeStepPreparing => 'إنشاء الذاكرة';

  @override
  String get fbNodeStepStarting => 'جارٍ التشغيل';

  @override
  String get fbNodeStepPairing => 'ربط هذا التطبيق';

  @override
  String get fbNodeRunningTitle => 'الذاكرة تعمل على هذا الهاتف';

  @override
  String get fbNodeRunningBody =>
      'تعمل الذاكرة في «على هذا الهاتف» ومتصلة بهذا التطبيق. تستمع داخل هذا الهاتف فقط.';

  @override
  String get fbNodeStoppedTitle => 'الذاكرة متوقفة';

  @override
  String get fbNodeStoppedBody =>
      'ذاكرتك ما زالت على هذا الهاتف. شغّلها لاستخدامها.';

  @override
  String get fbNodeStart => 'تشغيل الذاكرة';

  @override
  String get fbNodeStop => 'إيقاف الذاكرة';

  @override
  String get fbNodeRestart => 'إعادة تشغيل الذاكرة';

  @override
  String get fbNodeStopping => 'جارٍ الإيقاف';

  @override
  String get fbNodeRemoving => 'جارٍ الإزالة';

  @override
  String get fbNodeOpen => 'فتح عنك';

  @override
  String get fbNodeAddress => 'العنوان';

  @override
  String get fbNodeAddressValue => 'داخل هذا الهاتف فقط';

  @override
  String get fbNodeRemove => 'إزالة الذاكرة من هذا الهاتف';

  @override
  String get fbNodeRemoveTitle => 'إزالة الذاكرة من هذا الهاتف؟';

  @override
  String get fbNodeRemoveBody =>
      'يوقف هذا FactsBook ويحذف كل ما يحفظه على هذا الهاتف. لا يمكن التراجع.';

  @override
  String get fbNodeRemoveLoses1 =>
      'كل ملاحظة ومعلومة وتذكير يحتفظ بها FactsBook هنا';

  @override
  String get fbNodeRemoveLoses2 => 'الاتصال به وربط مفتاح الموافقة';

  @override
  String get fbNodeRemoved => 'أُزيلت الذاكرة من هذا الهاتف.';

  @override
  String get fbNodeFailInstall =>
      'تعذّر تثبيت البرنامج هنا. حاول مجددًا؛ تقول التفاصيل السبب.';

  @override
  String get fbNodeFailStart => 'لم تبدأ الذاكرة. حاول مجددًا.';

  @override
  String get fbNodeFailPair =>
      'الذاكرة تعمل لكن تعذّر على هذا التطبيق الاتصال بها. حاول مجددًا.';

  @override
  String get fbNodeFailStop => 'لم تتوقف الذاكرة. حاول مجددًا.';

  @override
  String get fbNodeFailRemove =>
      'تعذّرت إزالة الذاكرة. قد تبقى بعض بياناتها على هذا الهاتف.';

  @override
  String get fbNodeLog => 'عرض السجل';

  @override
  String get fbReasonTargetStale =>
      'تغيّر الشيء الذي تحدّثه هذه القاعدة، فلم يُقترح شيء.';

  @override
  String get fbReasonTargetGone =>
      'الشيء الذي تحدّثه هذه القاعدة لم يعد موجودًا أو لم يعد متاحًا لها، فلم يُقترح شيء.';

  @override
  String get fbReasonRejectedEvent => 'لم يقبل FactsBook هذه الرسالة.';

  @override
  String get fbRuleProblemSender => 'يجب أن يكون اسم المرسل من 1 إلى 80 حرفًا.';

  @override
  String get fbRuleSenderLabel => 'اسم المرسل المزعوم (اختياري)';

  @override
  String get fbRuleSenderHelp =>
      'فقط الرسائل التي تدّعي أنها من هذا الاسم. يستطيع أي شخص ادّعاء أي اسم، فهذا يضيّق القاعدة ولا يثبت من أرسلها.';

  @override
  String get fbRuleAreaLabel => 'المجال';

  @override
  String get fbRuleTestDraftNote =>
      'يستخدم الاختبار ما تراه هنا، محفوظًا أو لا. لا يحفظ شيئًا.';

  @override
  String get fbCareProposalEdited => 'جرى التعديل. راجعه ثم وافق عليه.';

  @override
  String get fbCaptureRulesLook => 'اسمح لقواعدي بقراءة هذا';

  @override
  String get fbAuthReasonRules => 'السماح لقواعدك بقراءة هذا النص';

  @override
  String get fbNodeWhatTitle => 'ما هذا؟';

  @override
  String get fbNodeWhatBody =>
      'تحفظ الذاكرة ملاحظاتك وتذكيراتك ومعلوماتك على هذا الهاتف وحدك. تعمل على هذا الهاتف ولا تغادره.';

  @override
  String get fbNodeOwnServer => 'الاتصال بخادمي الخاص';

  @override
  String get fbNodeOwnServerBody =>
      'متقدم: صل هذا التطبيق بخادم ذاكرة تشغّله بنفسك بدل استخدام هذا الهاتف.';

  @override
  String get fbMemoryStopped => 'الذاكرة متوقفة';

  @override
  String get fbMemoryStoppedDetail => 'شغّلها لترى ملاحظاتك وتذكيراتك';

  @override
  String get fbMemoryStart => 'ابدأ';

  @override
  String get fbMemoryStarting => 'جارٍ تشغيل الذاكرة…';

  @override
  String get fbCareTapHint => 'المس البطاقة لتعديلها أو تخطيها.';

  @override
  String fbCareApproveNamed(String claim) {
    return 'الموافقة على «$claim»';
  }

  @override
  String fbCareRejectNamed(String claim) {
    return 'رفض «$claim»';
  }

  @override
  String fbCareEditNamed(String claim) {
    return 'تعديل «$claim»';
  }

  @override
  String fbCareSkipNamed(String claim) {
    return 'تخطي «$claim»';
  }

  @override
  String get fbCareSkipDetail => 'تبقى هنا لوقت لاحق';

  @override
  String get fbCareSourceRule => 'اقترحته قاعدة';

  @override
  String get fbCareSourceAgent => 'اقترحه وكيل';

  @override
  String get fbNodeNameThisPhone => 'ذاكرة هذا الهاتف';

  @override
  String get fbNodeNameDefault => 'هذه الذاكرة';

  @override
  String get fbStatusAllWorking => 'كل شيء يعمل';

  @override
  String fbStatusNeedsLook(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count أشياء تحتاج إلى نظرة',
      one: 'شيء واحد يحتاج إلى نظرة',
    );
    return '$_temp0';
  }

  @override
  String fbStatusBackedUpAge(String age) {
    return 'تم النسخ الاحتياطي · النسخة عمرها $age';
  }

  @override
  String get fbReminderNotSounded => 'لا يرن على هذا الهاتف';

  @override
  String fbEventOutcomeProposed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'اقترحت عليك $count أشياء',
      one: 'اقترحت عليك شيئًا واحدًا',
    );
    return '$_temp0';
  }

  @override
  String get fbEventWaitingForOk => 'بانتظار موافقتك';

  @override
  String get fbCaptureAddTo => 'أضِف إلى';

  @override
  String get fbCaptureAddToNone => 'دع الذاكرة تصنّفها';

  @override
  String get fbCaptureAddToFactNote => 'تُحفظ سطرًا واحدًا لتوافق عليه';

  @override
  String get fbCaptureArea => 'المجال';

  @override
  String get fbCaptureAreaAuto => 'دع الذاكرة تختار';

  @override
  String get fbCaptureRemindToo => 'ذكّرني بها أيضًا';

  @override
  String get fbCaptureRemindTooDetail => 'تراجع التاريخ والوقت قبل حفظ أي شيء.';

  @override
  String fbCaptureRulesOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قواعد مفعّلة',
      one: 'قاعدة واحدة مفعّلة',
      zero: 'لا قواعد مفعّلة',
    );
    return '$_temp0';
  }

  @override
  String get fbCaptureSeeRules => 'اعرض قواعدك';

  @override
  String fbCaptureFactAdded(String thing) {
    return 'أُضيفت إلى $thing.';
  }

  @override
  String get fbCaptureFactProposed => 'أُرسلت لموافقتك. تنتظر في «يحتاجك».';

  @override
  String get fbCaptureSeeNeedsYou => 'اعرض «يحتاجك»';

  @override
  String fbThingAddFact(String thing) {
    return 'أضِف معلومة إلى $thing';
  }

  @override
  String get fbThingAddFactSave => 'أضِف المعلومة';

  @override
  String fbThingRemind(String thing) {
    return 'ذكّرني بـ $thing';
  }

  @override
  String fbThingReminderMade(String thing) {
    return 'حُفظ تذكير بشأن $thing.';
  }

  @override
  String get fbThingEditFact => 'تعديل هذه المعلومة';

  @override
  String get fbThingEditFactSave => 'أرسل تعديلي';

  @override
  String get fbThingFactChanged => 'تم التعديل.';

  @override
  String get fbThingChangeProposed => 'تعديلك ينتظر موافقتك في «يحتاجك».';

  @override
  String get fbThingOneLine => 'سطر واحد. ينتظر موافقتك قبل أن يُحفظ.';

  @override
  String get fbThingTapHint => 'المس معلومة لترى مصدرها أو لتعدّلها.';

  @override
  String fbThingExplainTold(String date) {
    return 'أخبرتَ الذاكرة بهذا في $date.';
  }

  @override
  String fbThingExplainSeen(String source, String date) {
    return 'قرأت الذاكرة هذا من $source في $date. قد يكون المصدر مخطئًا، فتحقق قبل أن تعتمد عليه.';
  }

  @override
  String fbThingExplainSeenNoSource(String date) {
    return 'قرأت الذاكرة هذا في $date. قد يكون المصدر مخطئًا، فتحقق قبل أن تعتمد عليه.';
  }

  @override
  String fbThingExplainGuessed(String source, String date) {
    return 'خمّنت الذاكرة هذا من $source في $date. لم يؤكده أحد.';
  }

  @override
  String fbThingExplainGuessedNoSource(String date) {
    return 'خمّنت الذاكرة هذا في $date. لم يؤكده أحد.';
  }

  @override
  String fbThingExplainCalculated(String date) {
    return 'استنتجت الذاكرة هذا من معلومات أخرى في $date.';
  }

  @override
  String get fbThingExplainWaiting => 'ينتظر موافقتك في «يحتاجك».';

  @override
  String get settingsV3Servers => 'الخوادم';

  @override
  String get settingsV3ThisPhone => 'هذا الهاتف';

  @override
  String get settingsV3Providers => 'المزوّدون';

  @override
  String get settingsV3Shell => 'الصدفة للأوامر';

  @override
  String get settingsV3VoiceTyping => 'الكتابة الصوتية';

  @override
  String get settingsV3Permissions => 'الأذونات والأتمتة';

  @override
  String get settingsV3Notifications => 'الإشعارات';

  @override
  String get settingsV3Background => 'الخلفية';

  @override
  String get settingsV3Language => 'اللغة';

  @override
  String get settingsV3ByServer => 'حسب الخادم';

  @override
  String get settingsV3On => 'مفعّل';

  @override
  String get settingsV3Off => 'متوقف';

  @override
  String get settingsV3Connected => 'متصل';

  @override
  String get settingsV3NotConnected => 'غير متصل';

  @override
  String get settingsV3PhoneRunning => 'يعمل';

  @override
  String get settingsV3PhoneInstalled => 'مثبّت';

  @override
  String get settingsV3PhoneSetUp => 'إعداد';

  @override
  String get settingsV3ServersBlock => 'يمكنك تغيير بعضها لخادم واحد';

  @override
  String get settingsV3ServerGone => 'تمت إزالة هذا الخادم';

  @override
  String get settingsV3SectionConnection => 'الاتصال';

  @override
  String get settingsV3SectionInApp => 'في هذا التطبيق';

  @override
  String get settingsV3StatusVersion => 'الحالة والإصدار';

  @override
  String get settingsV3SignIn => 'تسجيل الدخول';

  @override
  String get settingsV3SignInPassword => 'كلمة مرور';

  @override
  String get settingsV3SignInToken => 'رمز';

  @override
  String get settingsV3SignInNone => 'غير محدد';

  @override
  String get settingsV3NameAddress => 'الاسم والعنوان';

  @override
  String get settingsV3Supports => 'ما يدعمه هذا الخادم';

  @override
  String get settingsV3ModelNew => 'النموذج للمحادثات الجديدة';

  @override
  String get settingsV3ServerPick => 'يختاره الخادم';

  @override
  String get settingsV3DifferentHere => 'مختلف هنا';

  @override
  String get settingsV3Watch => 'مراقبة هذا الخادم';

  @override
  String get settingsV3ShareRead => 'مشاركة حالة القراءة';

  @override
  String get settingsV3Updates => 'التحديثات والخدمة';

  @override
  String get settingsV3UseMyDefault => 'استخدام إعدادي الافتراضي';

  @override
  String get settingsV3AgentsPhone => 'على هذا الهاتف';

  @override
  String get settingsV3ModelByServerNote =>
      'يحتفظ كل خادم باختياره للمحادثات الجديدة. المحادثات المفتوحة تحتفظ بنموذجها.';

  @override
  String get settingsV3ProvidersByServerNote =>
      'يسجّل كل خادم دخوله إلى المزوّدين بنفسه. تسجيل الدخول في أحدها لا يسجّل الدخول في الباقي.';

  @override
  String get settingsV3ShellByServerNote =>
      'يُحفظ على كل خادم. التطبيقات الأخرى التي تستخدم الخادم تحصل على صدفته أيضًا.';

  @override
  String settingsV3ServerConnected(String name) {
    return '$name · متصل';
  }

  @override
  String settingsV3ServersCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count خادم',
      many: '$count خادمًا',
      few: '$count خوادم',
      two: 'خادمان',
      one: 'خادم واحد',
    );
    return '$_temp0';
  }

  @override
  String settingsV3DifferentOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'مختلف على $count خادم',
      many: 'مختلف على $count خادمًا',
      few: 'مختلف على $count خوادم',
      two: 'مختلف على خادمين',
      one: 'مختلف على خادم واحد',
    );
    return '$_temp0';
  }

  @override
  String settingsV3ModelNewOn(String name) {
    return 'المحادثات الجديدة على $name';
  }

  @override
  String settingsV3SectionOn(String name) {
    return 'على $name';
  }

  @override
  String settingsV3SavedOn(String name) {
    return 'يُحفظ على $name. التطبيقات الأخرى التي تستخدم $name تحصل على هذا أيضًا.';
  }

  @override
  String settingsV3ConnectedVersion(String version) {
    return 'متصل · $version';
  }

  @override
  String settingsV3ConnectTo(String name) {
    return 'الاتصال بـ $name';
  }

  @override
  String settingsV3UseMyDefaultDetail(String name) {
    return 'تعود إشعارات $name والنموذج للمحادثات الجديدة ومشاركة حالة القراءة إلى اختياراتك.';
  }

  @override
  String get settingsV3ServerSettings => 'إعدادات الخادم';

  @override
  String get settingsV3ShellAutomatic => 'تلقائي';

  @override
  String get settingsV3ReadStateByServerNote =>
      'يُسأل كل خادم على حدة. لا يُشارك شيء مع خادم حتى تفعّله لهذا الخادم.';

  @override
  String settingsV3SharingCount(int count, int total) {
    return '$count من $total تشارك';
  }

  @override
  String get fbSettingsUsePhoneZone => 'استخدم المنطقة الزمنية لهذا الهاتف';

  @override
  String fbSettingsUsePhoneZoneDetail(String zone) {
    return '$zone. تحتفظ التذكيرات بأوقاتها.';
  }

  @override
  String get fbReasonKeyLockedNotYet =>
      'فيها كلمة مرور أو رقم حساب، ولا تستطيع الذاكرة حفظ ذلك بعد. تبقى هنا على هذا الهاتف.';

  @override
  String get fbListLockedNoteNotYet =>
      'الملاحظات التي فيها كلمات مرور أو أرقام حسابات لا تُحفظ بعد. تنتظر في «لم تُحفظ بعد».';

  @override
  String get fbReminderNotSoundedYet =>
      'لا يستطيع هذا الهاتف تشغيل صوت التذكيرات بعد. يُحفظ التذكير ويظهر في «التذكيرات».';

  @override
  String settingsV3ReconnectTo(String name) {
    return 'إعادة الاتصال بـ $name';
  }

  @override
  String get projectToolsNeedGitCommit =>
      'تحتاج مساحات العمل المنفصلة والبيئات السحابية إلى مشروع يتتبعه git وفيه إيداع واحد على الأقل. اطلب من الوكيل إعداد git وعمل أول إيداع.';

  @override
  String settingsV3UseMyDefaultDone(String name, String what) {
    return 'أُعيد على $name إلى اختياراتك: $what.';
  }

  @override
  String get settingsV3NotificationsAllServers => 'الإشعارات لكل الخوادم';

  @override
  String get settingsV3AgentsOnPhone => 'الوكلاء على هذا الهاتف';

  @override
  String get settingsV3AgentsReady => 'جاهز';

  @override
  String settingsV3AgentsExternalCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count متصلون',
      one: 'وكيل واحد متصل',
      zero: 'لا يوجد وكيل متصل',
    );
    return '$_temp0';
  }

  @override
  String settingsV3NotConnectedVersion(String version) {
    return 'غير متصل · آخر إصدار $version';
  }

  @override
  String get settingsV3ConnectToChange =>
      'اتصل لترى مزوّديه وأدواته وأذوناته وتغيّرها.';

  @override
  String quotaInAppStepChecking(String server) {
    return 'جارٍ فحص $server';
  }

  @override
  String get quotaInAppStepCopying => 'جارٍ نسخ مساعد الاستخدام';

  @override
  String get quotaInAppStepSignIns => 'جارٍ البحث عن تسجيلات الدخول';

  @override
  String get quotaInAppStepStarting => 'جارٍ تشغيل مساعد الاستخدام';

  @override
  String get quotaInAppStepReading => 'جارٍ قراءة ما تبقى';

  @override
  String quotaInAppFailLinux(String server) {
    return '$server ليس جاهزًا بعد. أكمل إعداد «على هذا الهاتف» ثم حاول مرة أخرى.';
  }

  @override
  String quotaInAppFailNoNode(String server) {
    return 'لا يوجد Node في $server بعد وهو مطلوب للاستخدام. شغّل إعداد «على هذا الهاتف» مرة أخرى لإضافته ثم حاول مرة أخرى.';
  }

  @override
  String quotaInAppFailNodeOld(String server) {
    return 'إصدار Node على $server أقدم من 20. شغّل إعداد «على هذا الهاتف» مرة أخرى لتحديثه ثم حاول مرة أخرى.';
  }

  @override
  String quotaInAppFailPrepare(String server) {
    return 'تعذّر تجهيز الاستخدام على $server. حاول مرة أخرى؛ وإن استمر الفشل فأعد تشغيل التطبيق.';
  }

  @override
  String quotaInAppFailStart(String server) {
    return 'لم يبدأ مساعد الاستخدام على $server. حاول مرة أخرى بعد قليل.';
  }

  @override
  String quotaInAppFailRemove(String server) {
    return 'تعذّر إيقاف الاستخدام على $server. حاول مرة أخرى.';
  }

  @override
  String get settingsV3ToolsByServerNote =>
      'الأدوات هي ما يستطيع الوكيل استخدامه، مثل قراءة الملفات أو تشغيل الأوامر. يحتفظ كل خادم بمجموعته الخاصة.';

  @override
  String get launchUiUsageSignInServerGone =>
      'هذا الخادم لم يعد محفوظًا، فهذا استخدامك بدلًا منه.';

  @override
  String get launchUiUsageSignInConnectionFailed =>
      'تعذّر الاتصال بهذا الخادم لتسجيل الدخول. افحصه في الخوادم ثم جرّب الأداة مرة أخرى.';

  @override
  String get settingsHubSearchReasoningAliases =>
      'transcript display thinking reasoning سجل عرض تفكير استدلال';

  @override
  String get usageTabPlans => 'الخطط';

  @override
  String get usageTabSpending => 'الإنفاق';

  @override
  String get usagePlansRefresh => 'تحديث الخطط';

  @override
  String get usageEmptyTitle => 'لا توجد اشتراكات بعد';

  @override
  String get usageEmptyBody => 'أضف خطة لترى كم تبقّى وموعد تجدّدها.';

  @override
  String get usageAddSection => 'إضافة اشتراك';

  @override
  String usageLeft(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'المتبقي $percentString%';
  }

  @override
  String usageUsedPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'المستخدم $percentString%';
  }

  @override
  String get usageWindowFiveHour => '5 ساعات';

  @override
  String get usageWindowWeek => 'أسبوع';

  @override
  String get usageWindowMonth => 'شهر';

  @override
  String get usageWindowTools => 'الأدوات · شهر';

  @override
  String get usageWindowOther => 'حد';

  @override
  String usageOnConnection(String connection) {
    return 'على $connection';
  }

  @override
  String get usageThisPhone => 'هذا الهاتف';

  @override
  String get usageAlertsTitle => 'التنبيهات';

  @override
  String get usageAlertsSummaryOff => 'متوقفة';

  @override
  String get usageWidgetRow => 'أداة الشاشة الرئيسية';

  @override
  String usageApiKeyOnly(String provider, String connection) {
    return 'مفتاح API لـ $provider على $connection · الدفع حسب الاستخدام، بلا حدود خطة';
  }

  @override
  String get usagePillSignInRanOut => 'انتهى تسجيل الدخول';

  @override
  String get usagePillBroken => 'تعذّرت القراءة';

  @override
  String get usagePillCantRead => 'لا يمكن القراءة بعد';

  @override
  String get usagePillOffline => 'غير متصل';

  @override
  String usageCantReadBody(String connection) {
    return 'مسجّل الدخول على $connection. لا يستطيع التطبيق قراءة حدوده من هناك بعد.';
  }

  @override
  String get usageCantReadBodyOne =>
      'مسجّل الدخول، لكن التطبيق لا يستطيع قراءة حدوده بعد.';

  @override
  String usageSignInRanOut(String provider, String connection) {
    return 'انتهى تسجيل دخول $provider على $connection';
  }

  @override
  String usageSignInRanOutOne(String provider) {
    return 'انتهى تسجيل دخولك إلى $provider.';
  }

  @override
  String get usageNotSignedInAnywhere => 'غير مسجّل الدخول في أي مكان';

  @override
  String get usageNotSignedInOne => 'غير مسجّل الدخول';

  @override
  String usageReaderBroken(String provider) {
    return 'تعذّرت قراءة $provider الآن';
  }

  @override
  String get usageNoLimits => 'لم تُبلَّغ حدود لهذه الخطة';

  @override
  String usageOfflineStale(String connection, String age) {
    return '$connection غير متصل · قُرئ قبل $age';
  }

  @override
  String usageOfflineStaleOne(String age) {
    return 'غير متصل · قُرئ قبل $age';
  }

  @override
  String usageDurationMinutes(int minutes) {
    return '$minutes د';
  }

  @override
  String usageDurationHours(int hours) {
    return '$hours س';
  }

  @override
  String usageDurationHoursMinutes(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String usageDurationDays(int days) {
    return '$days ي';
  }

  @override
  String usageDurationDaysHours(int days, int hours) {
    return '$days ي $hours س';
  }

  @override
  String usageResetsIn(String time, String duration) {
    return 'تتجدد عند $time، بعد $duration';
  }

  @override
  String get usageResetPassed => 'مرّ وقت التجدد. حدّث للتحقق.';

  @override
  String usageResetsAt(String time) {
    return 'تتجدد $time';
  }

  @override
  String usageFiveHourResets(String time) {
    return 'حد الـ5 ساعات يتجدد $time';
  }

  @override
  String usageWeekResets(String time) {
    return 'الحد الأسبوعي يتجدد $time';
  }

  @override
  String usageOutUntil(String time) {
    return 'متوقف حتى $time';
  }

  @override
  String usageResetSince(String time) {
    return 'تجدد عند $time منذ آخر قراءة';
  }

  @override
  String usageReadAgo(String age) {
    return 'قُرئ قبل $age';
  }

  @override
  String usageCheckedAgo(String age) {
    return 'فُحص قبل $age';
  }

  @override
  String usageReadFrom(String connection) {
    return 'قراءة الاستخدام من $connection';
  }

  @override
  String get usageSignIn => 'تسجيل الدخول';

  @override
  String get usageSignInAgain => 'تسجيل الدخول مجددًا';

  @override
  String get usageAddKey => 'إضافة مفتاح';

  @override
  String get usageTryAgain => 'حاول مجددًا';

  @override
  String get usageSignInUnavailable =>
      'لا يوجد مكان لتسجيل الدخول منه هنا بعد.';

  @override
  String usageAddTitle(String provider) {
    return 'إضافة $provider';
  }

  @override
  String usageAddWhere(String provider) {
    return 'أين تريد تسجيل الدخول إلى $provider؟ يقرأ التطبيق الاستخدام من المكان الذي تختاره.';
  }

  @override
  String get usageBest => 'الأفضل';

  @override
  String get usageAddInAppNote => 'على هذا الهاتف · يعمل الاستخدام فورًا';

  @override
  String get usageAddServerNote => 'خادم حفظته';

  @override
  String usageAddPhoneNote(String provider) {
    return 'يسجّل دخول وكيل $provider على هذا الهاتف';
  }

  @override
  String usageAddNoneTitle(String provider) {
    return 'لا يمكن إضافة $provider بعد';
  }

  @override
  String get usageAddNoneBody =>
      'احفظ خادمًا أو أعدّ «على هذا الهاتف» وسيظهر هنا.';

  @override
  String get usageReadFromTitleHere => 'قراءة الاستخدام';

  @override
  String get usageReadFromGoneTitle => 'هذا الاتصال اختفى';

  @override
  String get usageReadFromGoneBody => 'ارجع إلى الخطط لترى ما تبقّى.';

  @override
  String usageReadFromSignedIn(String providers, String connection) {
    return '$providers مسجّلة الدخول على $connection';
  }

  @override
  String get usageReadFromBodyInApp =>
      'لعرض حدودها يشغّل التطبيق قارئًا صغيرًا في «على هذا الهاتف». يقرأ فقط كم تبقّى، ولا يقرأ محادثاتك أو مفاتيحك أبدًا.';

  @override
  String get usageReadFromSetUpInApp => 'إعداد الاستخدام';

  @override
  String get usageNotNow => 'ليس الآن';

  @override
  String get usageSubGoneTitle => 'هذه الخطة اختفت';

  @override
  String get usageSubGoneBody => 'ارجع إلى الخطط لترى ما تبقّى.';

  @override
  String get usageStateSignedIn => 'مسجّل الدخول';

  @override
  String get usageStateExpired => 'انتهى تسجيل الدخول';

  @override
  String get usageStateNotSignedIn => 'غير مسجّل الدخول';

  @override
  String get usageConnOffline => 'غير متصل';

  @override
  String get usageConnReadsHere => 'الأرقام تأتي من هنا';

  @override
  String get usageSameAccount => 'الحساب نفسه';

  @override
  String get usageSignedInOn => 'مسجّل الدخول على';

  @override
  String get usageAlertAt => 'نبّهني عند';

  @override
  String usageAlertAtPlan(String provider, int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'لـ $provider فقط. الخطط الأخرى تستخدم $percentString%.';
  }

  @override
  String get usageAlertAtSame => 'مثل خططك الأخرى';

  @override
  String usageAlertSameAsOthers(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'مثل الخطط الأخرى ($percentString%)';
  }

  @override
  String get usageNotifyReset => 'أخبرني عند التجدد';

  @override
  String get usageNotifyResetHelp => 'رسالة عندما يعود حد كان قد نفد.';

  @override
  String get usageSaveFailed => 'تعذّر الحفظ. يبقى كما كان.';

  @override
  String get usageDetails => 'التفاصيل';

  @override
  String get usageDetailReader => 'القارئ';

  @override
  String get usageDetailRead => 'القراءة';

  @override
  String get usageDetailChecked => 'الفحص';

  @override
  String get usageDetailAccount => 'الحساب';

  @override
  String get usageDetailWindows => 'النوافذ';

  @override
  String get usageYes => 'نعم، الحساب معروف';

  @override
  String get usageNo => 'لا حساب معروف';

  @override
  String get usageAlertsSwitch => 'تنبيهات الاستخدام';

  @override
  String get usageAlertsDifferent => 'مختلفة عن الافتراضي';

  @override
  String get usageAlertOverrideResetOn => 'يُعلمك عند التجدد';

  @override
  String get usageAlertOverrideResetOff => 'بلا رسالة تجدد';

  @override
  String get usageAlertsNothingToWatch =>
      'لا شيء للمراقبة بعد. تبدأ التنبيهات عندما يمكن قراءة خطة.';

  @override
  String get usageWidgetSettingsShow => 'عرض هذه الخطط';

  @override
  String get usageWidgetSettingsWindows =>
      'عرض حد الـ5 ساعات أو الأسبوعي أو كليهما';

  @override
  String get usageWidgetWindowBoth => 'كلاهما';

  @override
  String get usageWidgetWindowFiveHour => '5 ساعات';

  @override
  String get usageWidgetWindowWeekly => 'أسبوعي';

  @override
  String get usageWidgetSettingsSetup => 'عرض الصفوف التي تحتاج إعدادًا';

  @override
  String get usageWidgetSettingsSetupHelp =>
      'صفوف تسجيل الدخول وإضافة المفتاح والإعداد';

  @override
  String get usageSources => 'المصادر';

  @override
  String get usageNoKey => 'لم يُضف مفتاح';

  @override
  String get usageStateCouldntCheck => 'تعذّر الفحص';

  @override
  String usageViaOpenCodeNoPlan(String connection) {
    return 'Anthropic على $connection · لا حدود للخطة عبر OpenCode';
  }

  @override
  String get usageNotReadYet => 'لم تُقرأ بعد';

  @override
  String get usageConnectionsHeader => 'الاتصالات';

  @override
  String get usageReadUsage => 'قراءة الاستخدام';

  @override
  String usageSetupSignedInOn(String connection) {
    return 'تم تسجيل الدخول على $connection';
  }

  @override
  String get usageSetupSignedIn => 'تم تسجيل الدخول';

  @override
  String usageSetupRanOutOn(String connection) {
    return 'انتهى تسجيل الدخول على $connection';
  }

  @override
  String get usageSetupRanOut => 'انتهى تسجيل الدخول';

  @override
  String get usageSetupCouldntRead => 'تعذّرت القراءة';

  @override
  String get usageNeedsSetupHeader => 'يحتاج إعدادًا';

  @override
  String get usageNoNumbersYet => 'لا أرقام للخطط بعد. أعدّ إحداها أدناه.';

  @override
  String usageComputerExplain(String server) {
    return 'يشغّل التطبيق فحصًا صغيرًا للقراءة فقط على $server عبر OpenCode عند كل تحديث. لا يُثبَّت شيء ولا يبقى شيء قيد التشغيل. ولا يعود إلا المتبقي من الحد، ولا تعود مفاتيحك أو محادثاتك أبدًا.';
  }

  @override
  String get usageComputerWhatRuns => 'ما الذي يعمل';

  @override
  String get usageComputerCommandLabel => 'الأمر';

  @override
  String get usageComputerReadsLabel => 'يقرأ';

  @override
  String usageComputerReadsValue(String server) {
    return 'ملف تسجيل الدخول الخاص بـ OpenCode على $server، وملف Codex CLI لـ Codex. للقراءة فقط ولا يُكتب شيء.';
  }

  @override
  String get usageComputerTurnOn => 'تشغيل';

  @override
  String get usageComputerTurnOff => 'إيقاف';

  @override
  String get usageComputerReadNow => 'اقرأ الآن';

  @override
  String get usageComputerDone => 'تم';

  @override
  String usageComputerChecking(String server) {
    return 'جارٍ فحص $server…';
  }

  @override
  String usageComputerFound(String plans, String server) {
    return 'وُجد $plans على $server.';
  }

  @override
  String usageComputerFoundNone(String server) {
    return 'نجح الفحص، لكن لا توجد خطة مسجَّل الدخول إليها على $server.';
  }

  @override
  String usageComputerOnNote(String server) {
    return 'تتم القراءة من $server عبر OpenCode.';
  }

  @override
  String usageComputerNeedsNode(String server) {
    return 'يحتاج $server إلى Node.js بإصدار 20 أو أحدث.';
  }

  @override
  String usageComputerServerTooOld(String server) {
    return 'إصدار OpenCode على $server قديم لهذا. حدّثه ثم حاول مجددًا.';
  }

  @override
  String usageComputerOffline(String server) {
    return 'تعذّر الوصول إلى $server الآن.';
  }

  @override
  String usageComputerNoAnswer(String server) {
    return 'لم ينتهِ الفحص على $server. حاول مجددًا.';
  }

  @override
  String usageComputerRowTitle(String server) {
    return 'القراءة من $server عبر OpenCode';
  }

  @override
  String get usageDetailLastCheck => 'آخر فحص';

  @override
  String get usagePhoneNotRunning => 'غير قيد التشغيل';

  @override
  String get kitRequestNeedsYouLabel => 'بحاجة إليك';

  @override
  String get kitRequestAlwaysAllowLike => 'السماح دائمًا بطلبات مثل هذا';

  @override
  String get chatRequestAlwaysAllowCommands => 'السماح دائمًا بأوامر مثل هذا';

  @override
  String get chatRequestAskCommand => 'تشغيل هذا الأمر؟';

  @override
  String get chatRequestAskEdit => 'تعديل هذا الملف؟';

  @override
  String get chatRequestAskRead => 'قراءة هذا الملف؟';

  @override
  String get chatRequestAskDirectory => 'العمل خارج هذا المشروع؟';

  @override
  String get chatRequestAskFetch => 'فتح صفحة الويب هذه؟';

  @override
  String usageBackAt(String time) {
    return 'تعود في $time';
  }

  @override
  String get usageAlertsOnWord => 'مفعّلة';

  @override
  String usageWidgetPlans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count خطط',
      two: 'خطتان',
      one: 'خطة واحدة',
      zero: 'لا خطط',
    );
    return '$_temp0';
  }

  @override
  String get usageAlertsForEvery => 'لكل خطة يستطيع التطبيق قراءتها';

  @override
  String get usageAlertsTellMe => 'أخبرني عندما';

  @override
  String get usageAlertsQuietGroup => 'هدوء';

  @override
  String get usageAlertPlanDownTo => 'تنخفض خطة إلى';

  @override
  String get usageAlertUsedUpBack => 'تعود خطة نفدت';

  @override
  String get usageQuietSameAsOthers => 'مثل تنبيهاتك الأخرى';

  @override
  String get usageWifiOnlyShort => 'الفحص عبر Wi-Fi فقط';

  @override
  String kitTaskChipCount(int done, int total) {
    return '$done/$total';
  }

  @override
  String kitTaskChipDone(int done, int total) {
    return '✓ $done/$total';
  }

  @override
  String kitTaskChipAdded(int done, int total) {
    return '+ $done/$total';
  }

  @override
  String kitTaskChipSpoken(int done, int total) {
    return 'المهام، اكتمل $done من $total';
  }

  @override
  String kitTaskChipSpokenNow(int done, int total, String task) {
    return 'المهام، اكتمل $done من $total، والآن: $task';
  }

  @override
  String get toolCardUpdatedTasks => 'تم تحديث المهام';

  @override
  String kitDiffFoldAbove(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سطرًا أعلاه',
      one: 'سطر واحد أعلاه',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBelow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سطرًا أدناه',
      one: 'سطر واحد أدناه',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBetween(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سطرًا',
      one: 'سطر واحد',
    );
    return '… $_temp0';
  }

  @override
  String notifStatusNeedsYou(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count طلب ينتظرك',
      many: '$count طلبًا ينتظرك',
      few: '$count طلبات تنتظرك',
      two: 'طلبان ينتظرانك',
      one: 'طلب واحد ينتظرك',
    );
    return '$_temp0';
  }

  @override
  String notifStatusStillWorking(int count) {
    return '$count ما زالت تعمل';
  }

  @override
  String notifStatusChatsWorking(int count) {
    return '$count محادثات تعمل';
  }

  @override
  String notifStatusMore(int count) {
    return '+$count أخرى';
  }

  @override
  String notifStatusWaitingCount(int count) {
    return '$count في الانتظار';
  }

  @override
  String notifStatusTasksProgress(int done, int total) {
    return '$done من $total مهام';
  }

  @override
  String notifStatusMinutes(int count) {
    return '$count د';
  }

  @override
  String notifStatusHours(int count) {
    return '$count س';
  }

  @override
  String notifStatusWaitingProvider(String provider) {
    return 'في الانتظار · حدّ $provider';
  }

  @override
  String get notifStatusWaitingGeneric => 'في الانتظار · المزوّد مشغول';

  @override
  String notifStatusTriesAgainAt(String time) {
    return 'يعيد المحاولة في $time';
  }

  @override
  String get notifStatusTriesAgainSoon => 'يعيد المحاولة قريبًا';

  @override
  String get notifStatusPausedAndroidTitle => 'متوقف · حدّ الخلفية في أندرويد';

  @override
  String get notifStatusPausedAndroidBody =>
      'يوقف أندرويد العمل في الخلفية بعد 6 ساعات يوميًا. افتح OpenCode لتواصل.';

  @override
  String get notifStatusPausedHotTitle => 'متوقف · الهاتف ساخن';

  @override
  String get notifStatusPausedHotBody => 'يستأنف عندما يبرد';

  @override
  String get notifStatusPausedIdleTitle => 'متوقف · لم يكن شيء يعمل';

  @override
  String get notifStatusPausedIdleBody => 'المس لتشغيل OpenCode من جديد';

  @override
  String notifStatusReconnectingTitle(String server) {
    return 'إعادة الاتصال بـ $server';
  }

  @override
  String get notifStatusReconnectingGeneric => 'إعادة الاتصال';

  @override
  String get notifStatusReconnectingBody => 'بانتظار الاتصال';

  @override
  String get notifStatusReadyTitle => 'جاهز · لا شيء قيد التشغيل';

  @override
  String get notifStatusReadyBody => 'جاهز عندما تكون جاهزًا';

  @override
  String notifStatusLastReply(String ago) {
    return 'آخر رد منذ $ago';
  }

  @override
  String get notifStatusLastReplyNow => 'آخر رد قبل لحظات';

  @override
  String notifStatusSetupTitle(int step, int steps) {
    return 'إعداد OpenCode · الخطوة $step من $steps';
  }

  @override
  String notifStatusOnServer(String server) {
    return 'على $server';
  }

  @override
  String get notifStatusOnThisPhone => 'على هذا الهاتف';

  @override
  String notifStatusPublicWorking(int count) {
    return 'OpenCode · $count تعمل';
  }

  @override
  String get notifStatusPublicWaiting => 'OpenCode · في الانتظار';

  @override
  String get notifStatusPublicPaused => 'OpenCode · متوقف';

  @override
  String get notifStatusPublicReconnecting => 'OpenCode · إعادة الاتصال';

  @override
  String get notifStatusPublicReady => 'OpenCode · جاهز';

  @override
  String get notifStatusPublicSetup => 'OpenCode · الإعداد';

  @override
  String get integrationsSignInCodeLabel => 'رمزك';

  @override
  String get integrationsSignInCodeHint =>
      'فتح المتصفح ينسخ الرمز لك. الصقه في الصفحة التي تُفتح.';

  @override
  String get integrationsCodeCopied => 'تم نسخ الرمز';

  @override
  String get integrationsCopyCode => 'نسخ الرمز';

  @override
  String get integrationsOpenBrowserAgain => 'فتح المتصفح مرة أخرى';

  @override
  String get integrationsSignInWaitingBrowser => 'بانتظار الموافقة في المتصفح';

  @override
  String get integrationsSignInWaitingDetail =>
      'أدخل الرمز في المتصفح ووافق. التطبيق ينتظر ويُنهي تسجيل الدخول تلقائيًا عند عودتك.';

  @override
  String get integrationsSignInEnded => 'انتهى تسجيل الدخول';

  @override
  String get integrationsSignInEndedDetail =>
      'انتهى تسجيل الدخول قبل الموافقة. ربما انتهت صلاحية الرمز أو أُعيد تشغيل الخادم. لم تتم إضافة شيء.';

  @override
  String get integrationsStartAgain => 'ابدأ من جديد';

  @override
  String get mentionToolTitle => 'ملف من المشروع';

  @override
  String get mentionToolSupporting => 'وجّه الوكيل إلى ملف في هذا المشروع';

  @override
  String get mentionSheetTitle => 'ملفات المشروع';

  @override
  String get mentionSearchLabel => 'البحث عن ملف بالاسم';

  @override
  String get mentionSearchHint =>
      'اكتب جزءًا من اسم الملف للبحث في هذا المشروع.';

  @override
  String get mentionSearchFailedTitle => 'تعذّر البحث في هذا المشروع';

  @override
  String get mentionSearchFailedMessage =>
      'تحقق من الاتصال بالخادم وحاول مرة أخرى.';

  @override
  String get mentionNoMatchWhat => 'ملفات المشروع';

  @override
  String get mentionProjectFolder => 'مجلد المشروع';

  @override
  String get inAppServerOwnershipRecoveryBody =>
      'تعذّر إعادة تشغيل الخادم على هذا الهاتف بأمان. افتح إعدادات التطبيق، واضغط «فرض الإيقاف»، ثم افتح التطبيق مجددًا دون مسح التخزين؛ ستبقى محادثاتك.';

  @override
  String get liveStyleAurora => 'انجراف الشفق';

  @override
  String get liveStyleMesh => 'شبكة تتنفس';

  @override
  String get liveStyleDotRipple => 'تموّج النقاط';

  @override
  String get liveStyleTopo => 'خطوط الكنتور';

  @override
  String get liveStyleFlowThreads => 'خيوط متدفقة';

  @override
  String get liveStyleGlowSpill => 'انسكاب الوهج';

  @override
  String get liveStyleLightRays => 'أشعة الضوء';

  @override
  String get liveStyleConstellation => 'كوكبة';

  @override
  String get liveStyleHalftone => 'نصف تدرّج';

  @override
  String get liveStyleLiquidBlobs => 'فقاعات سائلة';

  @override
  String get liveStyleMatrixRain => 'مطر الشيفرة';

  @override
  String get liveStyleBlueprint => 'مخطط هندسي';

  @override
  String get liveStyleCircuit => 'دارة';

  @override
  String get liveStyleOscilloscope => 'راسم الذبذبات';

  @override
  String get liveStyleRadar => 'رادار';

  @override
  String get liveStyleHoneycomb => 'خلية النحل';

  @override
  String get liveStyleBrushedMetal => 'معدن مصقول';

  @override
  String get liveStyleSilk => 'حرير';

  @override
  String get liveStyleCaustics => 'انعكاسات الماء';

  @override
  String get liveStyleBokeh => 'بوكيه';

  @override
  String get liveIntensitySubtle => 'خفيفة';

  @override
  String get liveIntensityNormal => 'عادية';

  @override
  String get liveIntensityBold => 'قوية';

  @override
  String get effectsLiveBackground => 'خلفية حيّة';

  @override
  String get continueWithMenu => 'المتابعة مع…';

  @override
  String get continueWithMenuHint => 'انقل هذا العمل إلى وكيل أو نموذج آخر';

  @override
  String get continueWithTitle => 'تابع هذا العمل';

  @override
  String continueWithSubtitle(String title, String project) {
    return '$title · $project';
  }

  @override
  String get continueWithModelsHere => 'نماذج على هذا الخادم';

  @override
  String get continueWithAgents => 'وكلاء على هذا الهاتف';

  @override
  String get continueWithNothingReady =>
      'لا يوجد وكيل أو نموذج آخر جاهز للمتابعة.';

  @override
  String get continueWithLoadFailed => 'تعذّر تحميل الوكلاء. حاول مرة أخرى.';

  @override
  String get continueWithTryAgain => 'حاول مرة أخرى';

  @override
  String get continueWithSignIn => 'تسجيل الدخول';

  @override
  String continueWithNeedsSignIn(String agent) {
    return 'سجّل الدخول لاستخدام $agent.';
  }

  @override
  String continueWithLimitReached(String agent) {
    return 'وصل $agent إلى حدّه الآن.';
  }

  @override
  String continueWithNotReady(String agent) {
    return '$agent ليس جاهزًا على هذا الهاتف بعد.';
  }

  @override
  String get continueWithNativeNote => 'تبقى محادثتك هنا.';

  @override
  String get continueWithUseModel => 'استخدم هذا النموذج';

  @override
  String get continueWithModelFailed => 'تعذّر تغيير النموذج. حاول مرة أخرى.';

  @override
  String get continueWithNewNote => 'ابدأ محادثة جديدة مع سجلّ لهذه المحادثة.';

  @override
  String get continueWithSourceStays => 'تبقى هذه المحادثة متاحة.';

  @override
  String get continueWithFactAgent => 'الوكيل';

  @override
  String get continueWithFactModel => 'النموذج';

  @override
  String get continueWithFactProject => 'المشروع';

  @override
  String get continueWithPreparing => 'جارٍ تجهيز سجلّ المحادثة…';

  @override
  String get continueWithSaving => 'جارٍ حفظ سجلّ المحادثة…';

  @override
  String get continueWithStarting => 'جارٍ بدء المحادثة…';

  @override
  String get continueWithLinking => 'جارٍ ربط المحادثتين…';

  @override
  String get continueWithRecordTitle => 'سجلّ المحادثة';

  @override
  String continueWithRecordSize(int kb) {
    return 'نحو $kb كيلوبايت';
  }

  @override
  String get continueWithOlderLeftOut =>
      'تُركت الأدوار الأقدم لإبقاء السجلّ صغيرًا';

  @override
  String continueWithOmittedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count رسائل متروكة',
      one: 'رسالة واحدة متروكة',
    );
    return '$_temp0';
  }

  @override
  String get continueWithRedacted => 'أُخفي كل ما بدا مثل كلمة مرور أو مفتاح.';

  @override
  String get continueWithReadPreview =>
      'اقرأ المعاينة أولًا. لا يمكنها التقاط كل تفصيل خاص.';

  @override
  String get continueWithShowPreview => 'عرض المعاينة';

  @override
  String get continueWithHidePreview => 'إخفاء المعاينة';

  @override
  String continueWithRecordFile(String path) {
    return 'يُحفظ باسم $path';
  }

  @override
  String get continueWithFirstMessage => 'الرسالة الأولى';

  @override
  String get continueWithFirstMessageHelp =>
      'أبقِ اسم الملف ليتمكن الوكيل من قراءة السجلّ.';

  @override
  String get continueWithMessageInvalid =>
      'اكتب رسالة أولى تُبقي اسم ملف السجلّ.';

  @override
  String get continueWithStart => 'ابدأ وتابع';

  @override
  String get continueWithCancel => 'إلغاء';

  @override
  String get continueWithPrepareAgain => 'جهّز مرة أخرى';

  @override
  String get continueWithSaveLinkAgain => 'احفظ الرابط مرة أخرى';

  @override
  String get continueWithOpenConversation => 'افتح المحادثة';

  @override
  String get continueWithAnother => 'تابع مع وكيل آخر';

  @override
  String get continueWithErrNoWriter =>
      'لا يستطيع هذا الوكيل حفظ سجلّ محادثة هنا بعد.';

  @override
  String get continueWithErrProject =>
      'هاتان المحادثتان غير مرتبطتين بالمشروع نفسه.';

  @override
  String get continueWithErrTarget => 'هذا النموذج غير متاح. اختر نموذجًا آخر.';

  @override
  String get continueWithErrSource =>
      'تغيّرت هذه المحادثة. جهّز السجلّ مرة أخرى.';

  @override
  String get continueWithErrScope => 'تغيّر الخادم أو المشروع. ابدأ من جديد.';

  @override
  String get continueWithErrRead =>
      'تعذّرت قراءة المحادثة. حاول تجهيزها مرة أخرى.';

  @override
  String get continueWithErrTooLarge => 'آخر دور كبير جدًا بحيث لا يمكن نقله.';

  @override
  String get continueWithErrFile => 'تعذّر حفظ السجلّ. لم تُرسل أي رسالة.';

  @override
  String get continueWithErrUnknown =>
      'النتيجة غير مؤكدة. تحقّق من المحادثات الهدف قبل المحاولة مرة أخرى.';

  @override
  String get continueWithErrLink => 'بدأت المحادثة لكن تعذّر حفظ رابطها.';

  @override
  String get continueWithErrGeneric => 'تعذّر تجهيز السجلّ. حاول مرة أخرى.';

  @override
  String continuedIn(String agent) {
    return 'تمت المتابعة في $agent';
  }

  @override
  String continuedFrom(String title) {
    return 'تمت المتابعة من $title';
  }

  @override
  String continuedFromShort(String title) {
    return 'من $title';
  }

  @override
  String continuedInShort(String agent) {
    return 'تابع في $agent';
  }

  @override
  String get continuedGoneShort => 'المحادثة الأخرى محذوفة';

  @override
  String get continuedGone => 'هذه المحادثة لم تعد متاحة.';

  @override
  String get continueWithChange => 'تغيير';

  @override
  String get workServerBusyPhone => 'OpenCode على هذا الهاتف مشغول';

  @override
  String get workServerRestartBodyCommand =>
      'سيتوقف الأمر قيد التشغيل أيضًا. تبقى محادثاتك محفوظة.';

  @override
  String get connectionReconnectingQuiet => 'جارٍ إعادة الاتصال…';

  @override
  String get connectionTryNow => 'جرّب الآن';

  @override
  String kitComposerWaitsForAgent(String agent) {
    return 'يُرسل عندما يستجيب $agent';
  }

  @override
  String get textSizeTitle => 'حجم النص';

  @override
  String get textSizeSmallest => 'الأصغر';

  @override
  String get textSizeSmall => 'صغير';

  @override
  String get textSizeDefault => 'افتراضي';

  @override
  String get textSizeLarge => 'كبير';

  @override
  String get kitComposerAddToTurnNote => 'سيقرأ الوكيل هذا أثناء عمله.';

  @override
  String get continuedOpenFromList => 'ستجدها في محادثات ذلك الخادم.';

  @override
  String get teamModelsSection => 'النماذج';

  @override
  String get teamModelsNote =>
      'تبدأ كل مهمة جديدة بالنموذج المختار لدورها. الاختيار المحفوظ ينطبق على المهام الجديدة لا على الجارية.';

  @override
  String get teamModelServerDefault => 'الافتراضي للخادم';

  @override
  String get teamModelServerDefaultHint =>
      'يستخدم النموذج الذي يعتمده الخادم عند عدم اختيار نموذج. قد يختلف عن نموذج محادثتك.';

  @override
  String get teamModelPlannerSaved =>
      'حُفظ نموذج المخطِّط. اطلب مرة أخرى ليخطط به.';

  @override
  String get teamProjectEditorTypePath => 'اكتب مساراً';

  @override
  String get teamProjectEditorChooseFolder => 'اختر مجلداً';

  @override
  String get teamProjectEditorFolderNone => 'لم يُختر مجلد بعد';

  @override
  String get teamProjectEditorFirstCommit => 'أنشئ أول إيداع لي';

  @override
  String get teamProjectEditorFirstCommitManual =>
      'افتح الطرفية في ذلك المجلد ونفّذ: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorFirstCommitFailed =>
      'تعذّر إنشاء أول إيداع. افتح الطرفية في ذلك المجلد ونفّذ: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorStarting => 'جارٍ فحص المجلد وبدء المخطِّط…';

  @override
  String get teamProjectEditorDraftRestored => 'عادت مسودتك غير المرسلة.';

  @override
  String get teamProjectEditorStartFresh => 'ابدأ من جديد';

  @override
  String get teamProjectEditorStarterNote =>
      'صيغ معلم أول من مهمتك. عدّله قبل الموافقة.';

  @override
  String get teamRefusalRepoPathInvalid =>
      'لا يستطيع الفريق إيجاد ذلك المجلد على هذا الهاتف.';

  @override
  String get teamRefusalRepoPathInvalidNext =>
      'اختر المجلد من القائمة بدلاً من كتابته.';

  @override
  String effectsLookPreviewLabel(String name) {
    return 'معاينة $name. تفتح الأشكال.';
  }

  @override
  String get effectsLookPreviewOffLabel =>
      'الخلفية الحيّة متوقفة. تفتح الأشكال.';

  @override
  String get effectsLookPreviewOff => 'متوقفة';

  @override
  String get teamProjectEditorFolderEmpty =>
      'لا توجد في هذا المجلد أي عمليات حفظ بعد، والفريق يحتاج إلى واحدة ليبدأ منها.';

  @override
  String get teamProjectEditorChooseModeBudget =>
      'اختر مسارًا واحدًا أو وكلاء متوازيين، وحدد ميزانية أو اختر بلا حد.';

  @override
  String get teamProjectEditorFallbackSection => 'اختياري';

  @override
  String get teamProjectEditorFallbackNote =>
      'يُحفظ مع الدور، لكن الفريق لا ينتقل إليه تلقائيًا بعد.';

  @override
  String get teamUiRowChecking => 'جارٍ التحقق…';

  @override
  String get teamRefusalCheckerUnreadable =>
      'انتهى الفحص، لكن تعذّرت قراءة نتيجته.';

  @override
  String get teamRefusalCheckerUnreadableNext =>
      'الاستئناف يقرأ النتيجة مرة أخرى دون سؤال النموذج. إذا نجح العمل فسيُدمج في dev، وتبدأ المهام المنتظرة بعده.';

  @override
  String get teamProjectInterruptedResumeNote =>
      'الاستئناف يقرأ نتيجة الفحص المكتمل مرة أخرى دون سؤال النموذج. إذا نجح العمل فسيُدمج في dev، وتبدأ المهام المنتظرة بعده.';

  @override
  String get teamProjectInterruptedResume => 'استئناف';

  @override
  String get widgetV2ConfigureTitle => 'شكل الأداة';

  @override
  String get widgetProjectTitle => 'اعرض المحادثات من';

  @override
  String get widgetProjectUnavailable =>
      'تعذّر حفظ هذا الاختيار. حاول مرة أخرى.';

  @override
  String get widgetV2BackgroundLabel => 'الخلفية';

  @override
  String get widgetV2BackgroundSolid => 'سادة';

  @override
  String get widgetV2BackgroundTinted => 'ملوّنة بخفة';

  @override
  String get widgetV2BackgroundTransparent => 'شفافة';

  @override
  String get widgetV2AccentLabel => 'لون التمييز';

  @override
  String get widgetV2AccentWallpaper => 'خلفية الهاتف';

  @override
  String get widgetV2AccentApp => 'مظهر التطبيق';

  @override
  String get widgetV2AccentGreen => 'أخضر';

  @override
  String get widgetV2AccentTeal => 'أخضر مزرق';

  @override
  String get widgetV2AccentBlue => 'أزرق';

  @override
  String get widgetV2AccentAmber => 'كهرماني';

  @override
  String get widgetV2SaveStyle => 'حفظ الشكل';

  @override
  String get widgetV2SteerTitle => 'إضافة إلى هذا الدور';

  @override
  String get widgetV2AfterReplyTitle => 'إرسال بعد هذا الرد';

  @override
  String get widgetV2VoiceTitle => 'رسالة جديدة';

  @override
  String get widgetV2MessageHint => 'اكتب رسالة';

  @override
  String get widgetV2Dictate => 'إملاء رسالة';

  @override
  String get widgetV2Send => 'إرسال';

  @override
  String get widgetV2Sending => 'جارٍ الإرسال…';

  @override
  String get widgetV2Accepted => 'تم الإرسال';

  @override
  String get widgetV2StaleRequest => 'تغيّرت المحادثة. رسالتك ما زالت هنا.';

  @override
  String get widgetV2Unavailable =>
      'الاتصال في الخلفية غير متاح. رسالتك ما زالت هنا.';

  @override
  String get widgetV2Unsupported => 'لا يدعم هذا الاتصال هذا الإجراء.';

  @override
  String get widgetV2Uncertain =>
      'تعذّر تأكيد إرسال الرسالة. تحقّق من المحادثة قبل الإرسال مرة أخرى.';

  @override
  String get widgetV2Busy => 'انتظر انتهاء الإجراء الحالي.';

  @override
  String get widgetV2InvalidRequest => 'اكتب رسالة قبل الإرسال.';

  @override
  String get widgetV2UnknownBehavior => 'بانتظار تفاصيل المحادثة…';

  @override
  String get widgetV2Cancel => 'إلغاء';

  @override
  String get widgetV2PreviewLabel => 'معاينة الأداة';

  @override
  String get widgetV2PreviewTitle => 'إصلاح صفحة تسجيل الدخول';

  @override
  String get widgetV2PreviewSecond => 'كتابة ملاحظات الإصدار';

  @override
  String get widgetV2PreviewStep => 'تعديل الملفات';

  @override
  String get widgetV2PreviewWorking => 'قيد العمل';

  @override
  String get widgetV2PreviewStop => 'إيقاف';

  @override
  String get widgetV2PreviewSteer => 'توجيه';

  @override
  String get widgetV2PreviewAllow => 'سماح';

  @override
  String get widgetV2SizeLabel => 'حجم المعاينة';

  @override
  String get widgetV2SizeCompact => 'صغير';

  @override
  String get widgetV2SizeCard => 'متوسط';

  @override
  String get widgetV2SizeList => 'عريض';

  @override
  String get widgetV2StyleStale =>
      'تغيّرت هذه الأداة. أغلق هذه النافذة وحاول مرة أخرى.';

  @override
  String get widgetV2StyleUnavailable => 'تعذّر حفظ الشكل. حاول مرة أخرى.';

  @override
  String get widgetV2DictationUnavailable =>
      'الإملاء غير متاح الآن. يمكنك الكتابة بدلاً من ذلك.';

  @override
  String get widgetV2RestartUnavailable =>
      'لا يمكن تغيير هذه المحادثة إلا من داخل التطبيق.';

  @override
  String get phoneSetupErrorPackageRepair =>
      'تحتاج بعض حزم Linux إلى إصلاح. تابع الإعداد للمحاولة مرة أخرى. توضح «التفاصيل» سبب التوقف.';

  @override
  String get agentsFailNeedsSignIn =>
      'تم تثبيت Claude Code. سجّل الدخول إلى حساب Claude لاستخدامه.';

  @override
  String get agentsFailHelperVersion =>
      'يحتاج Paseo إلى تحديث قبل أن يتمكن هذا الوكيل من الاتصال. حدّثه وحاول مجددًا.';

  @override
  String get agentsFailTimeout =>
      'استغرق الهاتف وقتًا طويلًا لإكمال الفحص. أعد المحاولة عندما ينخفض الضغط عليه.';

  @override
  String get agentsCheckRetry => 'إعادة المحاولة';

  @override
  String get agentsCheckUpdateHelper => 'تحديث Paseo وإعادة المحاولة';

  @override
  String get agentsCheckPrevious => 'استخدام الإصدار السابق';

  @override
  String agentsCheckPreviousNote(String version) {
    return 'لا يزال الإصدار $version على هذا الهاتف. سنفحصه قبل استخدامه. يبقى تسجيل دخولك ومحادثاتك كما هي.';
  }

  @override
  String get agentsCheckAgentChanged =>
      'تغيّر إصدار الوكيل المطلوب. افحص هذا الهاتف مجددًا.';

  @override
  String get agentsCheckHelperChanged =>
      'تغيّر إصدار Paseo المطلوب. افحص الاتصال مجددًا.';

  @override
  String get agentsCheckInstallChanged => 'تغيّر إعداد الهاتف. افحصه مجددًا.';

  @override
  String agentsCheckDownload(
    String component,
    String downloaded,
    String total,
  ) {
    return 'جارٍ تنزيل $component · $downloaded من $total';
  }

  @override
  String agentsCheckDownloadUnknown(String component, String downloaded) {
    return 'جارٍ تنزيل $component · $downloaded';
  }

  @override
  String agentsCheckDownloading(String component) {
    return 'جارٍ تنزيل $component';
  }

  @override
  String agentsCheckPreparing(String component) {
    return 'جارٍ تجهيز $component';
  }

  @override
  String get agentsCheckPhoneTools => 'أدوات الهاتف';

  @override
  String get phoneSetupErrorInterrupted =>
      'توقف الإعداد قبل اكتماله. تابع الإعداد للمحاولة مجددًا. ستُحفظ الخطوات المكتملة.';

  @override
  String get agentErrorProviderAuth =>
      'يحتاج مزوّد النموذج إلى تسجيل دخول صالح.';

  @override
  String get agentErrorProviderAuthHint =>
      'افتح إعدادات المزوّد لتسجيل الدخول أو استبدال مفتاح API، ثم حاول مجددًا.';

  @override
  String get agentErrorUnknown => 'تعذّر إكمال الطلب.';

  @override
  String get agentErrorUnknownHint =>
      'تحقّق من المحادثة قبل الإرسال مجددًا. قد تساعد التفاصيل في توضيح ما حدث.';

  @override
  String get serverTrustHttpTitle => 'هل تريد استخدام HTTP غير المشفّر؟';

  @override
  String get serverTrustHttpPublicBody =>
      'عند استخدام عنوان HTTP العام هذا، تنتقل كلمة المرور والشيفرة دون تشفير ويمكن للآخرين على الشبكة قراءتها أو تغييرها. هذا خطر على شبكات Wi-Fi العامة؛ يُفضّل استخدام HTTPS أو اتصال Tailscale خاص.';

  @override
  String get serverTrustHttpAccept => 'السماح باتصال HTTP لهذا الخادم';

  @override
  String get serverTrustCertificateTitle => 'هل تثق بهذه الشهادة؟';

  @override
  String get serverTrustCertificateBody =>
      'هذا الاتصال مشفّر، لكن هذا الجهاز لا يستطيع التحقق من جهة إصدار الشهادة أو اسم الخادم فيها. قارن بصمة SHA-256 هذه ببصمة خادمك عبر قناة منفصلة موثوقة ولا تثق بها إلا إذا تطابقتا؛ ستتطلب الاتصالات اللاحقة هذه الشهادة نفسها.';

  @override
  String get serverTrustCertificateChangedTitle => 'تغيّرت شهادة الخادم';

  @override
  String get serverTrustCertificateChangedBody =>
      'لم تعد الشهادة تطابق الشهادة التي وثقت بها، لذا حظر التطبيق الاتصال قبل إرسال كلمة المرور. قد يكون السبب تجديد الشهادة أو اعتراض الاتصال؛ أعد التحقق من البصمة مع مالك الخادم قبل الوثوق بالشهادة البديلة.';

  @override
  String get serverTrustCertificateInspect => 'التحقق من الشهادة';

  @override
  String get serverTrustCertificateRecheck => 'إعادة التحقق من الشهادة';

  @override
  String get serverTrustCertificateAccept => 'الوثوق بهذه البصمة';

  @override
  String get serverTrustCertificateReplace => 'الوثوق بالشهادة البديلة';

  @override
  String get serverTrustCertificatePinned =>
      'يستخدم هذا الخادم بصمة الشهادة التي وثقت بها. سيُحظر الاتصال إذا تغيّرت الشهادة.';

  @override
  String get serverTrustCertificateFailed =>
      'تعذّر التحقق من الشهادة. تحقق من العنوان والشبكة ثم حاول مجددًا. لم يتم الوثوق بأي شهادة.';

  @override
  String get serverTrustFingerprint => 'بصمة SHA-256';

  @override
  String get serverTrustInvalidPort => 'أدخل منفذ خادم بين 1 و65535.';

  @override
  String get serverTrustHttpUnconfirmed =>
      'يستخدم هذا الخادم HTTP غير المشفّر ولم توافق على ذلك بعد. عدّل الخادم لمراجعة تحذير HTTP والموافقة عليه، أو استخدم HTTPS.';

  @override
  String get serverTrustProbeFailed =>
      'تعذّر التحقق من الخادم. تحقق من العنوان وحاول مجددًا.';

  @override
  String get serverTrustPasswordRejected =>
      'رُفضت كلمة المرور. تحقق من كلمة مرور الخادم وحاول مجددًا.';

  @override
  String serverTrustCertificateValidity(String from, String to) {
    return 'صالحة من $from إلى $to';
  }

  @override
  String get serverTrustCertificateValidityError =>
      'انتهت صلاحية شهادة الخادم أو لم تبدأ بعد. اطلب من مالك الخادم تجديدها أو تحقق من ساعة الجهاز قبل إعادة الاتصال.';

  @override
  String get devPreviewTitle => 'المعاينة';

  @override
  String get devPreviewReload => 'تحديث الصفحة';

  @override
  String get devPreviewOpenInBrowser => 'فتح في المتصفح';

  @override
  String get devPreviewCopyAddress => 'نسخ العنوان';

  @override
  String get devPreviewLoading => 'جارٍ تحميل المعاينة';

  @override
  String get devPreviewFailedTitle => 'لا شيء يردّ بعد';

  @override
  String devPreviewFailedBody(String port) {
    return 'المنفذ $port لا يستجيب. قد يكون التطبيق لا يزال يبدأ.';
  }

  @override
  String get devPreviewRetry => 'حاول مرة أخرى';

  @override
  String devPreviewRemoteNote(String port, String host) {
    return 'تُعرض المنفذ $port على $host. يجب أن يستمع خادم التطوير على 0.0.0.0 (كل العناوين) ليمكن الوصول إليه من هذا الهاتف.';
  }

  @override
  String get devPreviewNoServerTitle => 'اتصل بخادم أولاً';

  @override
  String get devPreviewNoServerBody =>
      'الرابط إلى هذا الجهاز لا معنى له إلا على الجهاز الذي يشغّل التطبيق. اتصل بخادم أو افتح الرابط في المتصفح.';

  @override
  String get devPreviewNoHostTitle => 'تعذّر العثور على هذا الخادم';

  @override
  String get devPreviewNoHostBody =>
      'عنوان هذا الخادم غير مكتمل، لذا لا يمكن توجيه الرابط إليه. افتحه في المتصفح بدلاً من ذلك.';

  @override
  String get devPreviewUnsupportedTitle => 'المعاينة غير متاحة هنا';

  @override
  String get devPreviewUnsupportedBody =>
      'لا يستطيع هذا الجهاز عرض الصفحة داخل التطبيق. افتحها في المتصفح بدلاً من ذلك.';

  @override
  String get devPreviewMenu => 'معاينة تطبيق قيد التشغيل';

  @override
  String get devPreviewMenuHint => 'افتح خادم تطوير ذُكر في هذه المحادثة';

  @override
  String get devPreviewSheetSubtitle => 'الخوادم المذكورة في هذه المحادثة';

  @override
  String devPreviewPortTitle(String port) {
    return 'المنفذ $port';
  }

  @override
  String get devPreviewOnPhone => 'على هذا الهاتف';

  @override
  String devPreviewOnServer(String host) {
    return 'على $host';
  }

  @override
  String get openCode1UnsupportedTitle => 'حدّث هذا الخادم';

  @override
  String get openCode1Unsupported =>
      'يعمل هذا الخادم بإصدار OpenCode 1 الذي لم يعد هذا التطبيق يدعمه. حدّثه إلى OpenCode 2.';

  @override
  String get openCode2UpgradeTitle => 'التحديث إلى OpenCode 2';

  @override
  String get openCode2UpgradeFailed =>
      'تعذّر إكمال التحديث بأمان. لا تزال محادثات OpenCode 1 وملفاتك على هذا الهاتف دون تغيير. حاول مجددًا عندما يكون الخادم خاملًا وتتوفر مساحة كافية.';

  @override
  String get openCode2HostInstall => 'تثبيت OpenCode 2 وتشغيله';

  @override
  String get openCode2HostInstallDetail =>
      'يتطلب Node.js وnpm. يعمل على هذا الكمبيوتر فقط ويعرض كلمة المرور. أبقِ هذه الطرفية مفتوحة.';

  @override
  String get schedulePhoneBusy =>
      'تستخدم مطالبة مجدولة خادم الهاتف. حاول مرة أخرى بعد انتهائها.';

  @override
  String get schedulePhoneCheckFailed =>
      'تعذر التحقق من خادم الهاتف. حاول مرة أخرى.';

  @override
  String get scheduleTitle => 'المطالبات المجدولة';

  @override
  String get scheduleCreate => 'جدولة مطالبة';

  @override
  String get scheduleEdit => 'تعديل الجدول';

  @override
  String get schedulePrompt => 'المطالبة';

  @override
  String get scheduleTarget => 'المحادثة';

  @override
  String get scheduleThisConversation => 'هذه المحادثة';

  @override
  String get scheduleNewConversation => 'محادثة جديدة في كل تشغيل';

  @override
  String get scheduleOnce => 'مرة واحدة';

  @override
  String get scheduleRepeat => 'الموعد';

  @override
  String get scheduleTime => 'الوقت';

  @override
  String get scheduleDelete => 'حذف الجدول';

  @override
  String get schedulePaused => 'متوقف مؤقتًا';

  @override
  String get scheduleFinished => 'لا توجد مرات تشغيل أخرى';

  @override
  String get scheduleDigest => 'الملخص الصباحي';

  @override
  String get scheduleEmpty => 'لا توجد مطالبات مجدولة بعد';

  @override
  String get scheduleUnsupported =>
      'المطالبات المجدولة غير متاحة لهذا الاتصال.';

  @override
  String get scheduleSaveFailed => 'تعذر حفظ هذا التغيير. حاول مجددًا.';

  @override
  String get scheduleNeedsYou => 'افتح المحادثة للرد على طلب.';

  @override
  String get scheduleBusy => 'كانت المحادثة تعمل بالفعل.';

  @override
  String get scheduleUnavailable =>
      'تعذر الوصول إلى الخادم. تأكد من أنه يعمل وأن هذا الهاتف يستطيع الوصول إليه.';

  @override
  String get scheduleSignInAgain =>
      'يحتاج الخادم إلى كلمة المرور مجددًا. افتح الخادم وسجّل الدخول قبل التشغيل التالي.';

  @override
  String get scheduleAllowHttp =>
      'يستخدم هذا الخادم HTTP غير المشفر ولم يُسمح به بعد. افتح الخادم واسمح بـ HTTP قبل التشغيل التالي.';

  @override
  String get scheduleAgentFailed =>
      'توقف الوكيل بسبب خطأ. افتح المحادثة لمعرفة ما حدث.';

  @override
  String get scheduleCertificateChanged =>
      'تغيرت شهادة الخادم. افتح الخادم وتحقق منها قبل التشغيل التالي.';

  @override
  String get scheduleCertificateInvalid =>
      'شهادة الخادم منتهية الصلاحية أو لم تصبح صالحة بعد. افتح الخادم وتحقق منها قبل التشغيل التالي.';

  @override
  String get scheduleTargetChanged =>
      'تغير الاتصال أو الهدف المحفوظ. عدّل هذا الجدول.';

  @override
  String get scheduleInterrupted => 'انقطع التشغيل ولم تُرسل المطالبة مجددًا.';

  @override
  String get scheduleTimeout => 'بلغ التشغيل حده الزمني. تحقق من المحادثة.';

  @override
  String get scheduleUncertain =>
      'تعذر تأكيد التسليم. تحقق من المحادثة قبل الإرسال مجددًا.';

  @override
  String get scheduleMissed => 'تم التخطي وفق اختيارك للمواعيد الفائتة.';

  @override
  String get scheduleDeleteBody =>
      'ستُحذف مرات التشغيل المستقبلية وسجل هذا الجدول. تبقى المطالبات المرسلة في محادثاتها.';

  @override
  String scheduleToday(String time) {
    return 'اليوم، $time';
  }

  @override
  String scheduleTomorrow(String time) {
    return 'غدًا، $time';
  }

  @override
  String scheduleInMinutes(String minutes) {
    return 'خلال $minutes د';
  }

  @override
  String scheduleDeleteNamedTitle(String name) {
    return 'حذف «$name»؟';
  }

  @override
  String get openCode2MoveProgress => 'جارٍ نقل محادثاتك إلى OpenCode 2…';

  @override
  String openCode2MoveRetrying(int retry, int total) {
    return 'الهاتف لا يزال يستعد. سنعيد المحاولة بعد قليل (المحاولة $retry من $total).';
  }

  @override
  String get openCode2MoveFailedNotReady =>
      'لم يكن الخادم على هذا الهاتف جاهزًا. محادثات OpenCode 1 وملفاتك لا تزال على هذا الهاتف دون أي تغيير. أعد المحاولة بعد دقيقة.';

  @override
  String get openCode2MoveFailedSeparateHistory =>
      'يحتوي OpenCode 2 على هذا الهاتف على محادثات خاصة به، لذا توقف النقل لحماية الاثنين. لم يتغير أي شيء.';

  @override
  String get openCode2MoveFailedStopped =>
      'توقف النقل قبل اكتماله، وغالبًا لأن مساحة الهاتف الفارغة قليلة أو الخادم مشغول. محادثات OpenCode 1 وملفاتك لا تزال على هذا الهاتف دون أي تغيير.';

  @override
  String get openCode2MoveFailedNotConfirmed =>
      'لم يؤكد OpenCode 2 وصول محادثاتك. محادثات OpenCode 1 وملفاتك لا تزال على هذا الهاتف دون أي تغيير.';

  @override
  String get terminalLinkTitle => 'رابط في الطرفية';

  @override
  String get terminalLinkOpen => 'فتح الرابط';

  @override
  String get terminalLinkCopy => 'نسخ الرابط';

  @override
  String get terminalCopiedToClipboard => 'تم النسخ إلى الحافظة';

  @override
  String get chatRequestAskOutsideWrite => 'الكتابة خارج هذا المشروع؟';

  @override
  String get chatRequestAskOutsideRead => 'القراءة خارج هذا المشروع؟';

  @override
  String get chatRequestAskOutsideRun => 'تشغيل أمر خارج هذا المشروع؟';

  @override
  String get phoneSetupStageRetrying => 'توقف مؤقتًا. جارٍ المحاولة مجددًا';

  @override
  String get linuxMgrRow => 'لينكس';

  @override
  String get linuxMgrRowDetail =>
      'ما يعمل والمنافذ والمساحة والحزم في أوبونتو داخل هذا التطبيق';

  @override
  String get linuxMgrTitle => 'لينكس على هذا الهاتف';

  @override
  String get linuxMgrRunning => 'ما يعمل الآن';

  @override
  String get linuxMgrRunningDetail => 'ما يعمل داخل لينكس وإيقافه';

  @override
  String get linuxMgrPorts => 'المنافذ';

  @override
  String get linuxMgrPortsDetail => 'ما يستمع على منفذ ومعاينته';

  @override
  String get linuxMgrStorage => 'المساحة';

  @override
  String get linuxMgrStorageDetail => 'أين تذهب المساحة ومسح الذاكرة المؤقتة';

  @override
  String get linuxMgrPackages => 'الحزم';

  @override
  String get linuxMgrPackagesDetail => 'البرامج المثبتة: تثبيت وإزالة وتحديث';

  @override
  String get linuxMgrRepair => 'إصلاح لينكس';

  @override
  String get linuxMgrRepairDetail =>
      'يفحص لينكس وOpenCode وأدواتك ويصلح ما تعطل. مشاريعك لا تُمس.';

  @override
  String get linuxMgrRepairTitle => 'إصلاح لينكس؟';

  @override
  String get linuxMgrRepairBody =>
      'يفحص لينكس وOpenCode والأدوات التي أضفتها ويعيد تثبيت ما هو مفقود أو معطل. يحتاج إلى اتصال وقد يستغرق عدة دقائق.';

  @override
  String get linuxMgrRepairKept => 'مشاريعك وتسجيلات الدخول لا تُمس';

  @override
  String get linuxMgrReset => 'إعادة ضبط لينكس';

  @override
  String get linuxMgrResetDetail =>
      'ابدأ من جديد بنسخة أوبونتو نظيفة. مشاريعك تبقى.';

  @override
  String get linuxMgrResetTitle => 'إعادة ضبط لينكس؟';

  @override
  String get linuxMgrResetBody =>
      'يُمسح لينكس ويُعاد إعداده من الصفر. لا يمكن التراجع عن هذا.';

  @override
  String get linuxMgrResetLostTools =>
      'الحزم والأدوات المثبتة: OpenCode وPython وAI Team والكتابة الصوتية';

  @override
  String get linuxMgrResetLostSignIns => 'تسجيلات دخول الوكلاء وإعداداتها';

  @override
  String get linuxMgrResetLostChats =>
      'المحادثات التي يحفظها الخادم على هذا الهاتف';

  @override
  String get linuxMgrResetKept => 'مشاريعك تبقى على الهاتف';

  @override
  String get linuxMgrResetWord => 'إعادة';

  @override
  String get linuxMgrProblemBusy =>
      'مهمة أخرى تستخدم لينكس الآن. انتظر قليلا ثم حاول مرة أخرى.';

  @override
  String get linuxMgrProblemSetup => 'الإعداد قيد التنفيذ. انتظر حتى ينتهي.';

  @override
  String get linuxMgrProblemJob => 'مهمة حزم قيد التنفيذ. انتظر حتى تنتهي.';

  @override
  String get linuxMgrProblemUnavailable =>
      'لينكس غير مُعد أو لا يستجيب. افتح هذا الهاتف لإعداده.';

  @override
  String get linuxMgrProblemFailed => 'لم تكتمل العملية. حاول مرة أخرى.';

  @override
  String get linuxMgrLoadFailedTitle => 'تعذرت قراءة لينكس';

  @override
  String get linuxMgrProcTitle => 'ما يعمل في لينكس';

  @override
  String linuxMgrProcSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count برنامج',
      many: '$count برنامجا',
      few: '$count برامج',
      two: 'برنامجان',
      one: 'برنامج واحد',
    );
    return '$_temp0 · $memory';
  }

  @override
  String get linuxMgrKindServer => 'خادم OpenCode';

  @override
  String get linuxMgrKindPaseo => 'Paseo';

  @override
  String get linuxMgrKindAgent => 'وكيل';

  @override
  String get linuxMgrKindDev => 'خادم تطوير';

  @override
  String get linuxMgrKindOther => 'أخرى';

  @override
  String get linuxMgrAboutServer =>
      'خادم OpenCode الذي يتحدث إليه التطبيق. أوقفه من هذا الهاتف وليس من هنا.';

  @override
  String get linuxMgrAboutPaseo =>
      'يشغل Paseo وكلاء البرمجة على هذا الهاتف. إيقافه ينهي الوكلاء الذين بدأهم.';

  @override
  String get linuxMgrAboutAgent =>
      'وكيل برمجة أو جزء من AI Team. إيقافه ينهي عمله الجاري.';

  @override
  String get linuxMgrAboutDev =>
      'برنامج يعرض مشروعك أثناء بنائه. شغله من المشروع مرة أخرى عند الحاجة.';

  @override
  String get linuxMgrAboutOther =>
      'برنامج يعمل داخل لينكس. إيقافه ينهيه ولا شيء يعيد تشغيله.';

  @override
  String get linuxMgrProtected => 'يُدار من هذا الهاتف';

  @override
  String linuxMgrStopTitle(String name) {
    return 'إيقاف $name؟';
  }

  @override
  String get linuxMgrStopBody =>
      'ينتهي الآن ولا شيء يعيد تشغيله. أي عمل غير محفوظ فيه يضيع.';

  @override
  String linuxMgrStopHelpers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count برنامج بدأها يتوقف معه',
      many: '$count برنامجا بدأها يتوقف معه',
      few: '$count برامج بدأها تتوقف معه',
      two: 'برنامجان بدأهما يتوقفان معه',
      one: 'برنامج واحد بدأه يتوقف معه',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopLabel(String name) {
    return 'إيقاف $name';
  }

  @override
  String get linuxMgrStopping => 'جارٍ الإيقاف…';

  @override
  String linuxMgrStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تم إيقاف $count برنامج',
      many: 'تم إيقاف $count برنامجا',
      few: 'تم إيقاف $count برامج',
      two: 'تم إيقاف برنامجين',
      one: 'تم إيقاف برنامج واحد',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopForced(int count) {
    return 'تطلب إيقاف $count بالقوة';
  }

  @override
  String linuxMgrStopRemaining(int count) {
    return 'تعذر إيقاف $count';
  }

  @override
  String linuxMgrStopChanged(int count) {
    return 'تغير $count بالفعل فتُرك';
  }

  @override
  String get linuxMgrNotStoppedTitle => 'لم يتوقف كل شيء';

  @override
  String get linuxMgrProcEmpty => 'لا شيء يعمل في لينكس';

  @override
  String get linuxMgrProcEmptyBody => 'يبدأ OpenCode وأدواتك عند استخدامها.';

  @override
  String get linuxMgrCopyCommand => 'نسخ الأمر';

  @override
  String get linuxMgrPortsTitle => 'المنافذ في لينكس';

  @override
  String linuxMgrPortRow(int port) {
    return 'المنفذ $port';
  }

  @override
  String linuxMgrPortUsedBy(String name) {
    return 'يستخدمه $name';
  }

  @override
  String get linuxMgrPortUnknown => 'البرنامج غير معروف';

  @override
  String get linuxMgrPortLocal => 'على هذا الهاتف فقط';

  @override
  String get linuxMgrPortAll => 'مفتوح للأجهزة الأخرى على شبكتك';

  @override
  String get linuxMgrPortNotPage => 'ليس صفحة ويب';

  @override
  String get linuxMgrPortPreview => 'فتح المعاينة';

  @override
  String linuxMgrPortPreviewNamed(int port) {
    return 'فتح معاينة المنفذ $port';
  }

  @override
  String get linuxMgrPortCopy => 'نسخ العنوان';

  @override
  String get linuxMgrPortsNote =>
      'تُعرض فقط المنافذ التي يستطيع هذا الهاتف فتحها.';

  @override
  String get linuxMgrPortsEmpty => 'لا شيء يستمع على منفذ';

  @override
  String get linuxMgrPortsEmptyBody => 'عندما يبدأ خادم تطوير يظهر منفذه هنا.';

  @override
  String get linuxMgrStorageTitle => 'مساحة لينكس';

  @override
  String linuxMgrStorageTotal(String size) {
    return 'مستخدم داخل لينكس: $size';
  }

  @override
  String get linuxMgrCatBase => 'لينكس نفسه';

  @override
  String get linuxMgrCatBaseDetail => 'أوبونتو وملفات النظام';

  @override
  String get linuxMgrCatPackages => 'تنزيلات الحزم';

  @override
  String get linuxMgrCatPackagesDetail => 'الحزم المنزلة وقوائم الحزم';

  @override
  String get linuxMgrCatTools => 'Node والذاكرة المؤقتة';

  @override
  String get linuxMgrCatToolsDetail => 'Node وnpm وتنزيلات الإعداد';

  @override
  String get linuxMgrCatAgents => 'الوكلاء وOpenCode';

  @override
  String get linuxMgrCatAgentsDetail =>
      'OpenCode وPaseo وClaude Code وAI Team وإعداداتها المحفوظة';

  @override
  String get linuxMgrCatProjects => 'المشاريع';

  @override
  String get linuxMgrCatProjectsDetail => 'عملك. لا يُمسح من هنا أبدا.';

  @override
  String get linuxMgrCatTemp => 'الملفات المؤقتة والسجلات';

  @override
  String get linuxMgrCatTempDetail => 'ملفات مؤقتة وسجلات البرامج';

  @override
  String get linuxMgrFreeUp => 'تحرير مساحة';

  @override
  String get linuxMgrCleanApt => 'مسح تنزيلات الحزم';

  @override
  String get linuxMgrCleanAptDetail => 'ينزلها لينكس مرة أخرى إذا احتاج إليها';

  @override
  String get linuxMgrCleanTools => 'مسح ذاكرة Node والتنزيلات المؤقتة';

  @override
  String get linuxMgrCleanToolsDetail =>
      'تنزيلات npm والإعداد، تُجلب مرة أخرى عند الحاجة';

  @override
  String get linuxMgrCleanTemp => 'مسح الملفات المؤقتة والسجلات القديمة';

  @override
  String get linuxMgrCleanTempDetail =>
      'ملفات لم تُمس منذ يوم وسجلات أقدم من أسبوع';

  @override
  String get linuxMgrCleanAgents => 'إزالة إصدارات الوكلاء القديمة';

  @override
  String get linuxMgrCleanAgentsDetail => 'إصدارات Paseo التي لم تعد مستخدمة';

  @override
  String linuxMgrCleanBody(String size) {
    return 'تعود مساحة نحو $size. لا يمكن التراجع، ولا يُمس شيء من عملك.';
  }

  @override
  String get linuxMgrCleanKept => 'المشاريع وتسجيلات الدخول والإعدادات تبقى';

  @override
  String linuxMgrCleanDone(String size) {
    return 'تم تحرير $size';
  }

  @override
  String get linuxMgrCleanNothing => 'لم يكن هناك ما يُمسح';

  @override
  String get linuxMgrCleanNone => 'لا شيء لمسحه الآن';

  @override
  String get linuxMgrMeasuring => 'جارٍ القياس…';

  @override
  String get linuxMgrPkgTitle => 'الحزم';

  @override
  String linuxMgrPkgCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count حزمة مثبتة',
      many: '$count حزمة مثبتة',
      few: '$count حزم مثبتة',
      two: 'حزمتان مثبتتان',
      one: 'حزمة واحدة مثبتة',
    );
    return '$_temp0';
  }

  @override
  String get linuxMgrPkgSearch => 'ابحث في الحزم المثبتة';

  @override
  String get linuxMgrPkgNoMatch => 'لا توجد حزمة مثبتة مطابقة';

  @override
  String linuxMgrPkgShowAll(int count) {
    return 'عرض كل الحزم ($count)';
  }

  @override
  String get linuxMgrPkgUpdate => 'تحديث النظام';

  @override
  String get linuxMgrPkgUpdateDetail =>
      'يحدث قوائم الحزم ويثبت التحديثات المتاحة';

  @override
  String get linuxMgrPkgUpdateTitle => 'تحديث حزم لينكس؟';

  @override
  String get linuxMgrPkgUpdateBody =>
      'ينزل أحدث التحديثات ويثبتها. يستخدم اتصالك وقد يستغرق عدة دقائق ويتوقف من تلقاء نفسه بعد 45 دقيقة.';

  @override
  String get linuxMgrPkgInstall => 'تثبيت حزمة';

  @override
  String get linuxMgrPkgInstallDetail => 'بالاسم، مثل htop';

  @override
  String get linuxMgrPkgName => 'اسم الحزمة';

  @override
  String get linuxMgrPkgNameHelper => 'أسماء حزم أوبونتو مفصولة بمسافات';

  @override
  String get linuxMgrPkgNameInvalid =>
      'استخدم أحرفا صغيرة وأرقاما والرموز + - . فقط في كل اسم';

  @override
  String get linuxMgrPkgCheck => 'تحقق';

  @override
  String linuxMgrPkgAlready(String name) {
    return '$name مثبتة بالفعل';
  }

  @override
  String linuxMgrPkgUnknown(String name) {
    return 'لا توجد حزمة باسم $name. تحديث النظام يحدث قائمة الحزم.';
  }

  @override
  String linuxMgrPkgInstallTitle(String names) {
    return 'تثبيت $names؟';
  }

  @override
  String get linuxMgrPkgInstallBody => 'ينزل من أوبونتو ويثبت. يستخدم اتصالك.';

  @override
  String linuxMgrPkgInstallLabel(String names) {
    return 'تثبيت $names';
  }

  @override
  String linuxMgrPkgRemove(String name) {
    return 'إزالة $name';
  }

  @override
  String linuxMgrPkgRemoveTitle(String name) {
    return 'إزالة $name؟';
  }

  @override
  String get linuxMgrPkgRemoveBody => 'تُزال من لينكس. مشاريعك لا تُمس.';

  @override
  String linuxMgrPkgAlsoRemoved(String list) {
    return 'تُزال أيضا: $list';
  }

  @override
  String get linuxMgrPkgLocked =>
      'يحتاج إليها لينكس أو أدواتك. تُزال الأدوات من هذا الهاتف.';

  @override
  String linuxMgrPkgBlockedPlan(String list) {
    return 'إزالتها ستزيل أيضا $list التي يحتاج إليها لينكس.';
  }

  @override
  String get linuxMgrPkgChecking => 'جارٍ فحص ما ستزيله…';

  @override
  String linuxMgrJobInstalling(String names) {
    return 'جارٍ تثبيت $names';
  }

  @override
  String linuxMgrJobRemoving(String names) {
    return 'جارٍ إزالة $names';
  }

  @override
  String get linuxMgrJobUpdating => 'جارٍ تحديث حزم لينكس';

  @override
  String get linuxMgrJobKeepsGoing => 'يستمر إذا غادرت هذه الصفحة.';

  @override
  String linuxMgrJobStopInstall(String names) {
    return 'إيقاف تثبيت $names';
  }

  @override
  String linuxMgrJobStopRemove(String names) {
    return 'إيقاف إزالة $names';
  }

  @override
  String get linuxMgrJobStopUpdate => 'إيقاف التحديث';

  @override
  String get linuxMgrJobStopBody =>
      'يتوقف الآن. تُصلح أي حزمة نصف مكتملة عند تشغيل مهمة حزم في المرة القادمة.';

  @override
  String linuxMgrJobDoneInstall(String names) {
    return 'تم تثبيت $names';
  }

  @override
  String linuxMgrJobDoneRemove(String names) {
    return 'تمت إزالة $names';
  }

  @override
  String get linuxMgrJobDoneUpdate => 'حزم لينكس محدثة';

  @override
  String get linuxMgrJobFailed =>
      'لم تكتمل العملية. لم يُمس شيء من عملك. حاول مرة أخرى وسيتابع من حيث توقف.';

  @override
  String get linuxMgrJobTimedOut =>
      'استغرق أكثر من 45 دقيقة فتوقف من تلقاء نفسه. شغله مرة أخرى للمتابعة.';

  @override
  String get linuxMgrJobInterrupted =>
      'توقف قبل أن ينتهي، مثلا عند إغلاق التطبيق. شغله مرة أخرى للمتابعة.';

  @override
  String get linuxMgrJobNoticeInstall => 'جارٍ تثبيت حزم لينكس';

  @override
  String get linuxMgrJobNoticeRemove => 'جارٍ إزالة حزم لينكس';

  @override
  String get linuxMgrJobNoticeUpdate => 'جارٍ تحديث حزم لينكس';

  @override
  String get linuxMgrJobDismiss => 'تم';

  @override
  String linuxMgrJobStopInstallTitle(String names) {
    return 'إيقاف تثبيت $names؟';
  }

  @override
  String linuxMgrJobStopRemoveTitle(String names) {
    return 'إيقاف إزالة $names؟';
  }

  @override
  String get linuxMgrJobStopUpdateTitle => 'إيقاف تحديث حزم لينكس؟';

  @override
  String get linuxMgrCleanAptAsk => 'مسح تنزيلات الحزم؟';

  @override
  String get linuxMgrCleanToolsAsk => 'مسح ذاكرة Node والتنزيلات المؤقتة؟';

  @override
  String get linuxMgrCleanTempAsk => 'مسح الملفات المؤقتة والسجلات القديمة؟';

  @override
  String get linuxMgrCleanAgentsAsk => 'إزالة إصدارات الوكلاء القديمة؟';

  @override
  String get kitLogSearch => 'البحث في هذا الإخراج';

  @override
  String get kitLogPause => 'إيقاف المتابعة مؤقتًا';

  @override
  String get kitLogFollow => 'متابعة الإخراج الجديد';

  @override
  String get kitLogPreviousMatch => 'النتيجة السابقة';

  @override
  String get kitLogNextMatch => 'النتيجة التالية';

  @override
  String get servicesAlsoRunning => 'قيد التشغيل أيضًا';

  @override
  String get servicesAlsoPhoneHint =>
      'تم العثور عليها من المنافذ التي تستمع إليها، أيًا كانت طريقة تشغيلها.';

  @override
  String get servicesAlsoServerHint =>
      'يعرض هذا الخادم الأوامر التي يشغّلها. لا يظهر هنا خادم بدأ من الطرفية.';

  @override
  String servicesPorts(int count, String ports) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'المنافذ $ports',
      one: 'المنفذ $ports',
    );
    return '$_temp0';
  }

  @override
  String get servicesNoPortYet => 'لم يُعثر على منفذ بعد';

  @override
  String get servicesOpenPreview => 'فتح المعاينة';

  @override
  String servicesOpenPreviewNamed(String name) {
    return 'معاينة $name';
  }

  @override
  String servicesPreviewPort(String port) {
    return 'معاينة المنفذ $port';
  }

  @override
  String get servicesSaveCommand => 'حفظ كأمر';

  @override
  String servicesStopFoundTitle(String name) {
    return 'إيقاف $name؟';
  }

  @override
  String get servicesStopFoundHint =>
      'سيُنهي هذا البرنامج على الجهاز الذي يعمل عليه. وأي شيء لم يحفظه سيضيع.';

  @override
  String get servicesLogsUnavailable =>
      'لم يبدأ هذا البرنامج من هذا التطبيق، لذلك لا يُحفظ إخراجه حيث يستطيع التطبيق قراءته. اختر «حفظ كأمر» في صفه، ثم أوقف هذا البرنامج وابدأ الأمر المحفوظ من هنا لرؤية سجلاته.';

  @override
  String devPreviewRunningNow(String name) {
    return 'قيد التشغيل الآن: $name';
  }

  @override
  String get devPreviewNoneFound => 'لم يُعثر على تطبيقات قيد التشغيل';

  @override
  String get linuxMgrResetNotErasedTitle => 'لم تتم إعادة ضبط لينكس';

  @override
  String get linuxMgrResetNotErasedBody =>
      'لم يُمسح شيء. أغلق الطرفيات وأوقف ما يعمل في لينكس، ثم حاول مرة أخرى.';

  @override
  String get linuxMgrResetDetailsLabel => 'رمز الخطأ';

  @override
  String get linuxMgrResetCleanupTitle => 'إعادة إعداد لينكس';

  @override
  String linuxMgrResetForgetFailed(String name) {
    return 'الاتصال المحفوظ «$name» من قبل إعادة الضبط ما زال على هذا الهاتف.';
  }

  @override
  String linuxMgrResetLeft(String name, String what) {
    return 'من قبل إعادة الضبط، ما زال لدى «$name» محفوظًا: $what.';
  }

  @override
  String get linuxMgrResetLeftConnection => 'اتصاله المحفوظ';

  @override
  String get servicesAlsoPhoneFailed =>
      'تعذّر التحقق مما يعمل أيضًا في لينكس الآن. يُعاد التحقق خلال ثوانٍ.';

  @override
  String linuxMgrProcGroupCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عملية',
      many: '$count عملية',
      few: '$count عمليات',
      two: 'عمليتان',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairFailureWords => 'تعذّرت قراءة سجل Git.';

  @override
  String get gitRepairAction => 'إصلاح';

  @override
  String get gitRepairTitle => 'إصلاح سجل Git';

  @override
  String gitRepairWorking(String project) {
    return 'جارٍ إصلاح $project…';
  }

  @override
  String get gitRepairLooking => 'جارٍ البحث عن ملفات Git المفقودة…';

  @override
  String gitRepairRestoring(int done, int total) {
    return 'جارٍ إرجاع $done من $total من ملفات Git…';
  }

  @override
  String gitRepairAskingTermux(int done, int total) {
    return 'جارٍ نسخ $done من $total من ملفات Git من Termux…';
  }

  @override
  String get gitRepairChecking => 'جارٍ فحص المشروع باستخدام Git…';

  @override
  String gitRepairDoneTitle(String project) {
    return 'تم إصلاح $project';
  }

  @override
  String gitRepairDoneBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عادت $count من ملفات Git، ويمكن لـ Git قراءة المشروع من جديد.',
      one: 'عاد ملف Git واحد، ويمكن لـ Git قراءة المشروع من جديد.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairHealthyTitle => 'لم يكن هناك شيء مفقود';

  @override
  String gitRepairHealthyBody(String project) {
    return 'يمكن لـ Git قراءة $project الآن.';
  }

  @override
  String get gitRepairStart => 'ابدأ المحادثة';

  @override
  String get gitRepairTermuxNotAllowedTitle => 'الملفات في Termux';

  @override
  String get gitRepairTermuxNotAllowedBody =>
      'اسمح للتطبيق بتشغيل الأوامر في Termux، وسيعيد نسخها.';

  @override
  String get gitRepairAllow => 'اسمح وأصلح';

  @override
  String get gitRepairTermuxNoAnswerTitle => 'لم يستجب Termux';

  @override
  String get gitRepairTermuxNoAnswerBody =>
      'افتح Termux مرة ليبدأ، ثم عد إلى هنا وأصلح من جديد.';

  @override
  String get gitRepairAgain => 'أصلح من جديد';

  @override
  String get gitRepairOpenTermux => 'افتح Termux';

  @override
  String gitRepairCannotTitle(String project) {
    return 'تعذّر إصلاح $project';
  }

  @override
  String gitRepairMissingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'لم تعد $count من ملفات Git موجودة على هذا الهاتف. انسخ المشروع من جديد من مكان استضافته، أو استعد نسخة منه.',
      one:
          'لم يعد ملف Git واحد موجودًا على هذا الهاتف. انسخ المشروع من جديد من مكان استضافته، أو استعد نسخة منه.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairStillBrokenBody =>
      'ما زال Git لا يستطيع قراءته. انسخ المشروع من جديد من مكان استضافته، أو استعد نسخة منه.';

  @override
  String get gitRepairNotRepositoryTitle => 'تعذّر إصلاح هذا المجلد';

  @override
  String get gitRepairNotRepositoryBody =>
      'يمكن هنا إصلاح مشاريع Git الموجودة في مجلد المشاريع في «على هذا الهاتف» فقط.';

  @override
  String get gitRepairClose => 'إغلاق';

  @override
  String get gitProblemNotARepository => 'هذا المجلد ليس مستودع Git.';

  @override
  String get gitProblemGitMissing =>
      'Git غير مثبّت حيث يوجد هذا المشروع. ثبّته هناك ثم حاول مجددًا.';

  @override
  String get gitProblemFolderMissing =>
      'لم يُعثر على مجلد المشروع. ربما نُقل أو حُذف.';

  @override
  String get gitProblemEnvironmentUnavailable =>
      'تعذّر الوصول إلى المكان الذي يوجد فيه هذا المشروع. تحقّق من أنه يعمل ثم حاول مجددًا.';

  @override
  String get gitProblemServerCannotRunCommands =>
      'لا يستطيع هذا الخادم تشغيل أوامر Git للتطبيق.';

  @override
  String get gitProblemOutputTooLarge =>
      'التغييرات أكثر من أن تُقرأ هنا. أودِع بعض الملفات أو نظّفها ثم حاول مجددًا.';

  @override
  String get gitProblemTimedOut => 'استغرق Git وقتًا طويلًا للرد. حاول مجددًا.';

  @override
  String get gitProblemUnsafeRepository =>
      'يرفض Git العمل في هذا المجلد لأن مستخدمًا آخر يملكه. أصلح مالك المجلد على ذلك الجهاز.';

  @override
  String get gitProblemRepositoryLocked =>
      'أمر Git آخر يستخدم هذا المستودع. انتظر حتى ينتهي ثم حاول مجددًا.';

  @override
  String get gitProblemNoUpstream =>
      'هذا الفرع غير مرتبط بفرع على المستودع البعيد بعد. ادفعه أولًا.';

  @override
  String get gitProblemNoRemote =>
      'لا يوجد لهذا المستودع مستودع بعيد للدفع إليه. أضف واحدًا على ذلك الجهاز أولًا.';

  @override
  String get gitProblemDetachedHead =>
      'أنت لست على أي فرع. انتقل إلى فرع أولًا.';

  @override
  String get gitProblemCannotFastForward =>
      'في المستودع البعيد وفي هذا الفرع إيداعات جديدة، فلا يمكن جمعهما تلقائيًا. نفّذ merge أو rebase على ذلك الجهاز.';

  @override
  String get gitProblemPushRejected =>
      'في المستودع البعيد إيداعات ليست في هذا الفرع. اسحب أولًا ثم ادفع مجددًا.';

  @override
  String get gitProblemLocalChangesBlock =>
      'ستُستبدل تغييراتك غير المودَعة. أودِعها أو تجاهلها أولًا.';

  @override
  String get gitProblemAuthRequired =>
      'يطلب المستودع البعيد تسجيل الدخول. اضبط تسجيل الدخول إلى Git حيث يوجد هذا المشروع ثم حاول مجددًا.';

  @override
  String get gitProblemAuthRequiredSignIn =>
      'يطلب المستودع البعيد تسجيل الدخول. سجّل الدخول إلى GitHub ثم حاول مجددًا.';

  @override
  String get gitProblemGitHubTokenRejected =>
      'لم يقبل GitHub تسجيل دخولك. سجّل الدخول إلى GitHub مجددًا.';

  @override
  String get gitProblemNoPermission =>
      'لا يستطيع حسابك الدفع إلى هذا المستودع. اطلب الوصول من مالكه.';

  @override
  String get gitProblemNetwork =>
      'تعذّر الوصول إلى المستودع البعيد. تحقّق من اتصال الإنترنت ثم حاول مجددًا.';

  @override
  String get gitProblemRemoteNotFound =>
      'لم يُعثر على المستودع البعيد، أو أن حسابك لا يستطيع رؤيته.';

  @override
  String get gitProblemNothingSelected => 'اختر ملفًا واحدًا على الأقل.';

  @override
  String get gitProblemEmptyMessage => 'صف ما الذي تغيّر أولًا.';

  @override
  String get gitProblemNothingToCommit =>
      'لا يوجد ما يُودَع في الملفات التي اخترتها.';

  @override
  String get gitProblemIdentityMissing =>
      'يحتاج Git إلى اسمك وبريدك الإلكتروني لإنشاء إيداع. أضفهما ثم حاول مجددًا.';

  @override
  String get gitProblemSigningFailed =>
      'تعذّر على Git توقيع الإيداع. تحقّق من إعداد توقيع الإيداعات على ذلك الجهاز.';

  @override
  String get gitProblemHookRejected =>
      'أوقف فحص في هذا المستودع الإجراء. تعرض التفاصيل ما قاله.';

  @override
  String get gitProblemInvalidBranchName =>
      'هذا ليس اسم فرع صالحًا. تجنّب المسافات والرموز مثل ~ ^ : ? * [';

  @override
  String get gitProblemBranchExists => 'يوجد فرع بهذا الاسم بالفعل.';

  @override
  String get gitProblemBranchNotFound => 'لم يُعثر على هذا الفرع.';

  @override
  String get gitProblemConflicts =>
      'في بعض الملفات تعارضات. احلّها على ذلك الجهاز أولًا.';

  @override
  String get gitProblemOperationInProgress =>
      'عملية merge أو rebase لم تكتمل. أكملها أو ألغها على ذلك الجهاز أولًا.';

  @override
  String get gitProblemCannotDiscardConflict =>
      'لا يمكن تجاهل الملفات التي فيها تعارضات من هنا.';

  @override
  String get gitProblemUnknown =>
      'تعذّر على Git الإكمال. تعرض التفاصيل ما قاله.';

  @override
  String get gitUnavailableNotConnected => 'اتصل بخادم لرؤية تغييرات Git فيه.';

  @override
  String get gitUnavailableNoProject => 'افتح مشروعًا لرؤية تغييرات Git فيه.';

  @override
  String get gitUnavailableServerCannotRun =>
      'لا يستطيع هذا الخادم تشغيل Git للتطبيق. استخدم Git على ذلك الحاسوب بدلًا من ذلك.';

  @override
  String get gitNotRepositoryTitle => 'ليس مستودع Git';

  @override
  String get gitNotRepositoryBody => 'مجلد المشروع هذا لا يستخدم Git بعد.';

  @override
  String get gitCleanBody => 'كل شيء مودَع.';

  @override
  String get gitDetachedHead => 'ليس على أي فرع';

  @override
  String get gitNoCommitsYet => 'لا توجد إيداعات بعد';

  @override
  String gitAhead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count إيداع للدفع',
      many: '$count إيداعًا للدفع',
      few: '$count إيداعات للدفع',
      two: 'إيداعان للدفع',
      one: 'إيداع واحد للدفع',
      zero: 'لا توجد إيداعات للدفع',
    );
    return '$_temp0';
  }

  @override
  String get gitOperationMerge => 'عملية merge لم تكتمل';

  @override
  String get gitOperationRebase => 'عملية rebase لم تكتمل';

  @override
  String get gitOperationCherryPick => 'عملية cherry-pick لم تكتمل';

  @override
  String get gitOperationRevert => 'عملية revert لم تكتمل';

  @override
  String get gitUsesOwnSignIn =>
      'يستخدم الإرسال وجلب الأحدث تسجيل الدخول إلى Git المضبوط مسبقًا حيث يوجد هذا المشروع.';

  @override
  String get gitChangeAdded => 'مُضاف';

  @override
  String get gitChangeModified => 'معدَّل';

  @override
  String get gitChangeDeleted => 'محذوف';

  @override
  String get gitChangeRenamed => 'معاد تسميته';

  @override
  String get gitChangeCopied => 'منسوخ';

  @override
  String get gitChangeTypeChanged => 'تغيّر نوعه';

  @override
  String get gitChangeUntracked => 'جديد';

  @override
  String get gitChangeConflicted => 'تعارض';

  @override
  String get gitActionPull => 'سحب';

  @override
  String get gitActionPush => 'دفع';

  @override
  String get gitActionCommit => 'إيداع';

  @override
  String get gitActionSwitchBranch => 'تبديل الفرع';

  @override
  String get gitActionCreateBranch => 'فرع جديد';

  @override
  String get gitActionDiscard => 'تجاهل التغييرات';

  @override
  String get gitActionRefresh => 'تحديث';

  @override
  String get gitActionSaveIdentity => 'حفظ الاسم والبريد';

  @override
  String get gitBusyPull => 'جارٍ جلب الأحدث…';

  @override
  String get gitBusyPush => 'جارٍ الإرسال…';

  @override
  String get gitBusyCommit => 'جارٍ الحفظ…';

  @override
  String get gitBusySwitchBranch => 'جارٍ تبديل الفرع…';

  @override
  String get gitBusyCreateBranch => 'جارٍ إنشاء الفرع…';

  @override
  String get gitBusyDiscard => 'جارٍ تجاهل التغييرات…';

  @override
  String get gitBusyRefresh => 'جارٍ التحقق من التغييرات…';

  @override
  String get gitBusySaveIdentity => 'جارٍ الحفظ…';

  @override
  String get gitDoneUpToDate => 'محدَّث بالفعل.';

  @override
  String get gitDonePulled => 'تم جلب آخر التغييرات.';

  @override
  String get gitDonePushed => 'تم الإرسال.';

  @override
  String get gitDoneSwitched => 'تم تبديل الفرع.';

  @override
  String get gitDoneCreated => 'تم إنشاء الفرع والانتقال إليه.';

  @override
  String get gitDoneDiscarded => 'تم تجاهل التغييرات.';

  @override
  String get gitDoneIdentitySaved =>
      'تم حفظ اسمك وبريدك الإلكتروني لهذا المستودع.';

  @override
  String get gitCommitMessageHint => 'صف ما الذي تغيّر';

  @override
  String get gitWriteItForMe => 'اكتبها لي';

  @override
  String get gitAskAgentFixConflicts => 'اطلب من الوكيل حل التعارضات';

  @override
  String gitConflictCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملفات فيها تعارض',
      one: 'ملف واحد فيه تعارض',
    );
    return '$_temp0';
  }

  @override
  String gitAskAgentFixConflictsPrompt(String files) {
    return 'من فضلك حلّ تعارضات الدمج في $files. حافظ على ما قصده الطرفان، ثم أخبرني بما اخترت.';
  }

  @override
  String get gitBranchNameHint => 'اسم الفرع';

  @override
  String get gitIdentityName => 'الاسم';

  @override
  String get gitIdentityEmail => 'البريد الإلكتروني';

  @override
  String get gitHubSignIn => 'تسجيل الدخول إلى GitHub';

  @override
  String get gitHubSignOut => 'تسجيل الخروج من GitHub';

  @override
  String get gitHubSignOutTitle => 'تسجيل الخروج من GitHub؟';

  @override
  String gitHubSignedInAs(String login) {
    return 'تم تسجيل الدخول إلى GitHub باسم $login';
  }

  @override
  String get gitHubRequestingCode => 'جارٍ الحصول على رمز تسجيل الدخول…';

  @override
  String get gitHubEnterCode =>
      'أدخل هذا الرمز على GitHub لإكمال تسجيل الدخول.';

  @override
  String get gitHubOpenGitHub => 'فتح GitHub';

  @override
  String get gitHubCopyCode => 'نسخ الرمز';

  @override
  String get gitHubWaiting => 'في انتظار إكمالك على GitHub…';

  @override
  String get gitHubFinishing => 'جارٍ إكمال تسجيل الدخول…';

  @override
  String get gitHubCancel => 'إلغاء';

  @override
  String get gitHubSignOutBody =>
      'سينسى التطبيق تسجيل دخولك إلى GitHub على هذا الجهاز. لإزالة وصوله بالكامل، ألغِه على GitHub أيضًا.';

  @override
  String get gitHubManageAccess => 'إدارة الوصول على GitHub';

  @override
  String get gitHubPushOverHttps =>
      'يستخدم الدفع إلى GitHub تسجيل الدخول هذا عبر HTTPS. لا تتغيّر إعدادات SSH لديك.';

  @override
  String get gitHubProblemExpired =>
      'انتهت صلاحية الرمز قبل إدخاله. سجّل الدخول مجددًا للحصول على رمز جديد.';

  @override
  String get gitHubProblemDenied => 'أُلغي تسجيل الدخول على GitHub.';

  @override
  String get gitHubProblemDisabled =>
      'تسجيل الدخول إلى GitHub غير مفعّل لهذا التطبيق بعد.';

  @override
  String get gitHubProblemNetwork =>
      'تعذّر الوصول إلى GitHub. تحقّق من اتصال الإنترنت ثم حاول مجددًا.';

  @override
  String get gitHubProblemTokenRejected =>
      'انتهى تسجيل دخولك إلى GitHub. سجّل الدخول مجددًا.';

  @override
  String get gitHubProblemUnexpected =>
      'أرسل GitHub ردًا لم يفهمه التطبيق. حاول مجددًا.';

  @override
  String get gitProblemInvalidRemoteName =>
      'هذا ليس اسمًا صالحًا للمستودع البعيد. استخدم أحرفًا أو أرقامًا أو نقاطًا أو شرطات.';

  @override
  String get gitProblemInvalidRemoteUrl =>
      'هذا العنوان لا يبدو صحيحًا. استخدم عنوانًا مثل https://github.com/owner/name.';

  @override
  String get gitProblemRemoteAddressHasPassword =>
      'أزل كلمة المرور أو الرمز من العنوان. سجّل الدخول إلى GitHub بدلًا من ذلك حتى لا يُحفظ أي سرّ في المستودع.';

  @override
  String get gitProblemRemoteExists =>
      'يوجد مستودع بعيد بهذا الاسم بالفعل. اختر اسمًا آخر.';

  @override
  String get gitProblemInvalidFolderName =>
      'هذا ليس اسم مجلد صالحًا. استخدم أحرفًا أو أرقامًا أو نقاطًا أو شرطات.';

  @override
  String get gitProblemFolderExists =>
      'يوجد مجلد بهذا الاسم بالفعل. اختر اسمًا آخر.';

  @override
  String get gitActionAddRemote => 'إضافة مستودع بعيد';

  @override
  String get gitActionClone => 'استنساخ';

  @override
  String get gitBusyAddRemote => 'جارٍ إضافة المستودع البعيد…';

  @override
  String get gitBusyClone => 'جارٍ الاستنساخ…';

  @override
  String get gitDoneRemoteAdded => 'تمت إضافة المستودع البعيد.';

  @override
  String gitDoneCloned(String path) {
    return 'تم الاستنساخ في $path. افتحه من مشاريعك.';
  }

  @override
  String get gitSheetTitle => 'Git';

  @override
  String gitSheetChipSemantics(String branch) {
    return 'Git، الفرع $branch. يفتح إجراءات Git.';
  }

  @override
  String get gitSheetChipSemanticsPlain => 'Git. يفتح إجراءات Git.';

  @override
  String get gitGetLatest => 'جلب الأحدث';

  @override
  String get gitSend => 'إرسال';

  @override
  String get gitSaveChanges => 'حفظ التغييرات';

  @override
  String gitSaveChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف متغيّر',
      many: '$count ملفًا متغيّرًا',
      few: '$count ملفات متغيّرة',
      two: 'ملفان متغيّران',
      one: 'ملف متغيّر واحد',
      zero: 'لا ملفات متغيّرة',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveFilesLabel => 'الملفات المضمّنة';

  @override
  String gitSaveButton(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حفظ $count ملف',
      many: 'حفظ $count ملفًا',
      few: 'حفظ $count ملفات',
      two: 'حفظ ملفين',
      one: 'حفظ ملف واحد',
    );
    return '$_temp0';
  }

  @override
  String gitSaveOnBranch(String branch) {
    return 'على $branch';
  }

  @override
  String get gitSwitchRow => 'تبديل الفرع';

  @override
  String gitSwitchRowOn(String branch) {
    return 'على $branch';
  }

  @override
  String gitBranchRemoteOnly(String remote) {
    return 'على $remote، وليس على هذا الجهاز بعد';
  }

  @override
  String get gitBranchCurrent => 'الفرع الحالي';

  @override
  String gitNewBranchFrom(String branch) {
    return 'فرع جديد من $branch';
  }

  @override
  String get gitCloneRow => 'استنساخ مستودع';

  @override
  String get gitCloneRowHint => 'انسخ مستودعًا بجوار هذا المشروع';

  @override
  String get gitCloneAddressLabel => 'عنوان المستودع';

  @override
  String get gitCloneFolderLabel => 'اسم المجلد';

  @override
  String gitCloneInto(String path) {
    return 'يُستنسخ في $path';
  }

  @override
  String get gitCloneButton => 'استنساخ';

  @override
  String get gitRemotesRow => 'المستودعات البعيدة';

  @override
  String get gitRemotesRowNone => 'لا شيء بعد';

  @override
  String get gitRemotesTitle => 'المستودعات البعيدة';

  @override
  String get gitRemotesEmpty =>
      'لا يوجد مستودع بعيد لهذا المشروع بعد. أضف واحدًا لإرسال عملك إلى مكان ما.';

  @override
  String get gitRemoteAddTitle => 'إضافة مستودع بعيد';

  @override
  String get gitRemoteNameLabel => 'الاسم';

  @override
  String get gitRemoteAddressLabel => 'العنوان';

  @override
  String get gitIdentityTitle => 'اسمك على الحفظ';

  @override
  String get gitHubSignedOutHint =>
      'سجّل الدخول مرة واحدة بالرمز. لا حاجة للصق رمز وصول.';

  @override
  String get gitHubSignInTitle => 'تسجيل الدخول إلى GitHub';

  @override
  String get gitHubSignInWhy =>
      'يتطلب الإرسال إلى GitHub تسجيل دخولك. احصل على رمز هنا وأدخله في GitHub، ويحفظ التطبيق تسجيل الدخول على هذا الجهاز فقط.';

  @override
  String get gitHubSignOutConfirm => 'تسجيل الخروج';

  @override
  String gitUsesOwnSignInOn(String host) {
    return 'يستخدم الإرسال وجلب الأحدث تسجيل دخول Git المُعدّ على $host.';
  }

  @override
  String gitDoneSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف',
      many: '$count ملفًا',
      few: '$count ملفات',
      two: 'ملفين',
      one: 'ملف واحد',
    );
    return 'تم حفظ $_temp0.';
  }

  @override
  String get gitDoneSavedPlain => 'تم الحفظ.';

  @override
  String get gitChecking => 'جارٍ الفحص…';

  @override
  String get gitCheckFailed => 'تعذّر الفحص، اضغط للمحاولة';

  @override
  String gitNewCount(int count) {
    return '$count جديد';
  }

  @override
  String get gitIdentityWhy =>
      'تحمل عمليات الحفظ في هذا المشروع هذا الاسم والبريد، فيكون عملك باسمك عند إرساله.';

  @override
  String get kitStepSliderLess => 'أ';

  @override
  String get kitStepSliderMore => 'أ';

  @override
  String get appearanceIntensityHelp =>
      'تبقى المحادثة فوق الخلفية وسهلة القراءة.';

  @override
  String get appearanceTextHelp => 'يسري على التطبيق كله فوق حجم نص هاتفك.';

  @override
  String kitCarouselPlace(int page, int count) {
    return '$page من $count';
  }

  @override
  String get gitPullNotOnRemote => 'ليس على المستودع البعيد بعد. أرسله أولًا.';

  @override
  String get gitPullNoRemote => 'أضف مستودعًا بعيدًا أدناه أولًا.';

  @override
  String get gitSendNoRemote => 'لا يوجد مستودع بعيد بعد. أضف واحدًا أدناه.';

  @override
  String get chatSteerHint => 'وجّهه أثناء عمله…';

  @override
  String chatSteerReadsAfterCommand(String agent) {
    return 'يقرأ $agent هذا عند انتهاء الأمر';
  }

  @override
  String chatSteerReadsAfterStep(String agent) {
    return 'يقرأ $agent هذا عند انتهاء هذه الخطوة';
  }

  @override
  String get chatSteerAgentFallback => 'الوكيل';

  @override
  String get chatSteerStopCommand => 'أوقف الأمر';

  @override
  String get chatSteerStopStep => 'أوقف هذه الخطوة';

  @override
  String get chatSendLaterOrRepeat => 'أرسل لاحقًا أو كرّر';

  @override
  String get chatSendLaterSaved => 'تمت الجدولة. تجدها في الرسائل المجدولة.';

  @override
  String get composerSettingModel => 'النموذج';

  @override
  String get composerSettingPlan => 'الخطة';

  @override
  String get composerSettingApprovalDetail => 'يوافق على ما يطلبه دون أن يسألك';

  @override
  String get chatAutoApproveOn => 'الموافقة التلقائية مفعّلة';

  @override
  String get chatAutoApproveTurnOff => 'إيقاف';

  @override
  String get chatNoModelSignedIn => 'لا يوجد نموذج مسجَّل الدخول';

  @override
  String get chatNoModelSignIn => 'تسجيل الدخول';

  @override
  String gitSendTo(String place, String branch) {
    return 'إرسال إلى $place · $branch';
  }

  @override
  String get gitPlaceGitHub => 'GitHub';

  @override
  String get gitStateUpToDate => 'محدَّث';

  @override
  String get gitStateNothingToSend => 'لا شيء للإرسال';

  @override
  String get gitStateFirstSend => 'أول مرة، يربط هذا الفرع';

  @override
  String get gitDetailsBranch => 'الفرع';

  @override
  String get gitDetailsGetsFrom => 'جلب الأحدث من';

  @override
  String get gitDetailsSendsTo => 'الإرسال إلى';

  @override
  String get gitDetailsAddress => 'عنوان المستودع البعيد';

  @override
  String gitDoneGotN(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تغيير جديد',
      many: '$count تغييرًا جديدًا',
      few: '$count تغييرات جديدة',
      two: 'تغييرين جديدين',
      one: 'تغيير جديد واحد',
    );
    return 'تم جلب $_temp0.';
  }

  @override
  String reviewSummary(int files, int added, int removed) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: 'تغيّر $files ملف',
      many: 'تغيّر $files ملفًا',
      few: 'تغيّرت $files ملفات',
      two: 'تغيّر ملفان',
      one: 'تغيّر ملف واحد',
    );
    return '$_temp0 · +$added −$removed';
  }

  @override
  String get reviewAlreadyApplied =>
      'أجرى الوكيل هذه التغييرات في مشروعك بالفعل.';

  @override
  String get reviewLooksGood => 'يبدو جيدًا';

  @override
  String get reviewUndoAll => 'تراجع عن الكل';

  @override
  String get reviewSendBackWithNote => 'أعِدها مع ملاحظة';

  @override
  String reviewUndoTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'التراجع عن $count ملف تغيّر؟',
      many: 'التراجع عن $count ملفًا تغيّر؟',
      few: 'التراجع عن $count ملفات تغيّرت؟',
      two: 'التراجع عن ملفين تغيّرا؟',
      one: 'التراجع عن ملف واحد تغيّر؟',
    );
    return '$_temp0';
  }

  @override
  String get reviewUndoBody =>
      'يعود مشروعك كما كان قبل أن يبدأ الوكيل. وتبقى المحادثة.';

  @override
  String get chatsNewBuildPrompt => 'ماذا تريد أن تبني؟';

  @override
  String get chatsNewProjectRow => 'المشروع';

  @override
  String get chatsNewModelRow => 'النموذج';

  @override
  String get chatsNewSeparateCopyDetail =>
      'يعمل على نسخة خاصة به، فتبقى ملفاتك كما هي.';

  @override
  String get modelPickerSheetTitle => 'النموذج';

  @override
  String modelPickerAllModels(int count) {
    return 'كل النماذج ($count)';
  }

  @override
  String get modelPickerReasonDefault => 'الافتراضي';

  @override
  String get modelPickerReasonRecent => 'استُخدم مؤخرًا';

  @override
  String get modelPickerReasonCheapest => 'الأرخص';

  @override
  String get modelPickerFree => 'مجاني';

  @override
  String get modelPickerScopeNewSessions => 'للمحادثات الجديدة';

  @override
  String get modelPickerScopeSession => 'لهذه المحادثة';

  @override
  String get searchRecentTitle => 'الأخيرة';

  @override
  String chatsHomeUnreachableShort(String servers) {
    return '$servers لا يستجيب';
  }

  @override
  String chatProjectChipFilesSemantics(String name) {
    return 'المشروع $name. يفتح ملفاته وأدواته.';
  }

  @override
  String chatProjectServerChipFilesSemantics(String name, String server) {
    return 'المشروع $name على $server. يفتح ملفاته وأدواته.';
  }

  @override
  String get chatTurnAllFiles => 'كل الملفات';

  @override
  String chatTurnAllFilesMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف آخر تغيّر',
      many: '$count ملفًا آخر تغيّر',
      few: '$count ملفات أخرى تغيّرت',
      two: 'ملفان آخران تغيّرا',
      one: 'ملف آخر تغيّر',
    );
    return 'كل الملفات · $_temp0';
  }

  @override
  String chatTurnFileLinesAdded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أُضيف $count سطر',
      many: 'أُضيف $count سطرًا',
      few: 'أُضيفت $count أسطر',
      two: 'أُضيف سطران',
      one: 'أُضيف سطر واحد',
    );
    return '$_temp0';
  }

  @override
  String chatTurnFileLinesRemoved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حُذف $count سطر',
      many: 'حُذف $count سطرًا',
      few: 'حُذفت $count أسطر',
      two: 'حُذف سطران',
      one: 'حُذف سطر واحد',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubSearch => 'ابحث في الإعدادات';

  @override
  String get settingsHubServer => 'الخادم';

  @override
  String get settingsHubHelp => 'المساعدة';

  @override
  String get settingsHubServerOnPhone => 'على هذا الهاتف';

  @override
  String settingsHubServerLine(String where, String state) {
    return '$where · $state';
  }

  @override
  String get settingsHubServerNone => 'لم يُعدّ بعد';

  @override
  String gitSaveAndSendTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حفظ $count ملف وإرسالها إلى $place · $branch',
      many: 'حفظ $count ملفًا وإرسالها إلى $place · $branch',
      few: 'حفظ $count ملفات وإرسالها إلى $place · $branch',
      two: 'حفظ ملفين وإرسالهما إلى $place · $branch',
      one: 'حفظ ملف واحد وإرساله إلى $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSendSavesTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إرسال $count حفظ إلى $place · $branch',
      many: 'إرسال $count حفظًا إلى $place · $branch',
      few: 'إرسال $count حفظات إلى $place · $branch',
      two: 'إرسال حفظين إلى $place · $branch',
      one: 'إرسال حفظ واحد إلى $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveAndSendTitle => 'حفظ وإرسال';

  @override
  String get gitSaveOnly => 'حفظ دون إرسال';

  @override
  String gitMessageAddOne(String file) {
    return 'إضافة $file';
  }

  @override
  String gitMessageUpdateOne(String file) {
    return 'تحديث $file';
  }

  @override
  String gitMessageUpdateTwo(String first, String second) {
    return 'تحديث $first و$second';
  }

  @override
  String gitMessageUpdateMany(String first, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تحديث $first و$count ملف آخر',
      many: 'تحديث $first و$count ملفًا آخر',
      few: 'تحديث $first و$count ملفات أخرى',
      two: 'تحديث $first وملفين آخرين',
      one: 'تحديث $first وملف آخر',
    );
    return '$_temp0';
  }

  @override
  String gitDoneSavedAndSent(String place, String branch) {
    return 'تم الحفظ والإرسال إلى $place · $branch.';
  }

  @override
  String gitSavedNotSent(String reason) {
    return 'تم الحفظ هنا، ولم يُرسل بعد. $reason';
  }

  @override
  String get gitMoreLabel => 'المستودعات البعيدة والاستنساخ والتفاصيل';

  @override
  String gitBusySendingTo(String place, String branch) {
    return 'جارٍ الإرسال إلى $place · $branch…';
  }

  @override
  String gitGetNewFrom(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'جلب $count تغيير جديد من $place · $branch',
      many: 'جلب $count تغييرًا جديدًا من $place · $branch',
      few: 'جلب $count تغييرات جديدة من $place · $branch',
      two: 'جلب تغييرين جديدين من $place · $branch',
      one: 'جلب تغيير جديد واحد من $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSavedSignInFirst(String place) {
    return 'تم الحفظ هنا، ولم يُرسل بعد. يطلب $place تسجيل الدخول أولًا.';
  }

  @override
  String get thisPhoneAnswerRunning => 'كل شيء يعمل';

  @override
  String thisPhoneAnswerMemory(String size) {
    return 'يستخدم نحو $size من الذاكرة';
  }

  @override
  String thisPhoneAnswerStorage(String size) {
    return 'يشغل $size من مساحة التخزين';
  }

  @override
  String get thisPhoneAnswerConnect => 'استخدم خادم هذا الهاتف';

  @override
  String get thisPhoneAnswerHeatPaused => 'متوقف مؤقتًا، هاتفك دافئ';

  @override
  String get thisPhoneAnswerHeatStopped => 'متوقف، هاتفك ساخن';

  @override
  String thisPhoneAnswerHeatResumeIn(String time) {
    return 'يستأنف فريق الذكاء الاصطناعي عمله تلقائيًا خلال $time';
  }

  @override
  String get thisPhoneAnswerHeatResumeCool =>
      'يستأنف فريق الذكاء الاصطناعي عمله تلقائيًا حين يبرد هاتفك';

  @override
  String get thisPhoneAnswerHeatStays =>
      'يبقى فريق الذكاء الاصطناعي متوقفًا حتى تستأنفه';

  @override
  String get thisPhoneAnswerOff => 'الخادم متوقف';

  @override
  String get thisPhoneAnswerOffLine => 'يبقى متوقفًا حتى تشغّله.';

  @override
  String get thisPhoneAnswerAndroidClosed => 'أغلق Android الخادم';

  @override
  String get thisPhoneAnswerStoppedItself => 'توقف الخادم من تلقاء نفسه';

  @override
  String thisPhoneAnswerRestartsIn(String time) {
    return 'يُعاد تشغيله تلقائيًا خلال $time';
  }

  @override
  String get thisPhoneAnswerRestartsSoon => 'يُعاد تشغيله تلقائيًا بعد لحظات';

  @override
  String get thisPhoneAnswerRestarting => 'يُعاد التشغيل تلقائيًا';

  @override
  String get thisPhoneAnswerGaveUp => 'ظل يتوقف، لذا لن يُعاد تشغيله تلقائيًا.';

  @override
  String get thisPhoneAnswerRestartNow => 'أعد التشغيل الآن';

  @override
  String get thisPhoneAnswerStartFailed => 'لم يبدأ الخادم';

  @override
  String get thisPhoneAnswerInstallFailed => 'لم يكتمل الإعداد';

  @override
  String get thisPhoneAnswerCheckFailed => 'تعذّر فحص الخادم';

  @override
  String get thisPhoneAnswerStopFailed => 'لم يتوقف الخادم';

  @override
  String get thisPhoneAnswerSwitchStopped => 'لم يكتمل التبديل';

  @override
  String get thisPhoneAnswerTermux => 'تعذّر الوصول إلى Termux';

  @override
  String get thisPhoneAnswerRecovery => 'لا يمكن إعادة تشغيل الخادم بأمان';

  @override
  String get thisPhoneAnswerNotSetUp => 'OpenCode ليس على هذا الهاتف بعد';

  @override
  String get thisPhoneAnswerSettingUp => 'جارٍ إعداد الخادم';

  @override
  String get thisPhoneAnswerStarting => 'جارٍ تشغيل الخادم';

  @override
  String get thisPhoneAnswerStopping => 'جارٍ إيقاف الخادم';

  @override
  String get thisPhoneAnswerChecking => 'جارٍ فحص الخادم';

  @override
  String get thisPhoneAnswerRemoving => 'جارٍ إزالة الخادم';

  @override
  String get thisPhoneAnswerConnecting => 'جارٍ الاتصال بالخادم';

  @override
  String get thisPhoneRestartRow => 'إعادة تشغيل OpenCode على هذا الهاتف';

  @override
  String get thisPhoneRestartTitle => 'إعادة تشغيل الخادم؟';

  @override
  String get thisPhoneRestartBusy =>
      'يُكتب ردٌّ الآن. إعادة التشغيل توقفه؛ وتبقى محادثاتك.';

  @override
  String thisPhoneAboutSeconds(int seconds) {
    return 'نحو $seconds ث';
  }

  @override
  String thisPhoneAboutMinutes(int minutes) {
    return 'نحو $minutes د';
  }

  @override
  String get thisPhoneAnswerStartFailedLine =>
      'شغّله مجددًا؛ يشرح قسم التفاصيل أدناه ما حدث.';

  @override
  String get serverSwitcherAddServer => 'إضافة خادم';

  @override
  String get serverSwitcherNotAnswering => 'لا يستجيب';

  @override
  String serverSwitcherChecksAgain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يتحقق مجددًا بعد $count دقيقة',
      few: 'يتحقق مجددًا بعد $count دقائق',
      two: 'يتحقق مجددًا بعد دقيقتين',
      one: 'يتحقق مجددًا بعد دقيقة',
    );
    return '$_temp0';
  }

  @override
  String serverSwitcherTryAgainLabel(String server) {
    return 'إعادة المحاولة مع $server';
  }

  @override
  String get serverSwitcherOpenRequest => 'فتح';

  @override
  String serverSwitcherOpenRequestLabel(String target) {
    return 'فتح $target';
  }

  @override
  String serverSwitcherAllowLabel(String command, String server) {
    return 'السماح بـ $command مرة واحدة على $server';
  }

  @override
  String serverSwitcherUndoLabel(String server) {
    return 'التراجع عن الإجابة على $server';
  }

  @override
  String kitRequestMoreTargets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '+$count أخرى',
      one: '+1 آخر',
    );
    return '$_temp0';
  }

  @override
  String kitRequestChangeSize(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String chatRequestAskRunIn(String command, String project) {
    return 'تشغيل $command في $project؟';
  }

  @override
  String chatRequestAskRunNamed(String command) {
    return 'تشغيل $command؟';
  }

  @override
  String chatRequestAskRunAnyIn(String project) {
    return 'تشغيل أمر في $project؟';
  }

  @override
  String chatRequestAskEditNamed(String file) {
    return 'تعديل $file؟';
  }

  @override
  String chatRequestAskEditFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تعديل $count ملف؟',
      many: 'تعديل $count ملفًا؟',
      few: 'تعديل $count ملفات؟',
      two: 'تعديل ملفين؟',
      one: 'تعديل ملف واحد؟',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskCreateNamed(String file) {
    return 'إنشاء $file؟';
  }

  @override
  String chatRequestAskCreateFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'إنشاء $count ملف؟',
      many: 'إنشاء $count ملفًا؟',
      few: 'إنشاء $count ملفات؟',
      two: 'إنشاء ملفين؟',
      one: 'إنشاء ملف واحد؟',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskDeleteNamed(String file) {
    return 'حذف $file؟';
  }

  @override
  String chatRequestAskDeleteFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حذف $count ملف؟',
      many: 'حذف $count ملفًا؟',
      few: 'حذف $count ملفات؟',
      two: 'حذف ملفين؟',
      one: 'حذف ملف واحد؟',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskReadNamed(String file) {
    return 'قراءة $file؟';
  }

  @override
  String chatRequestAlwaysAllowNamed(String what) {
    return 'السماح دائمًا بـ $what';
  }

  @override
  String get chatRequestAlwaysAllowEdits => 'السماح دائمًا بالتعديلات';

  @override
  String get chatRequestAlwaysAllowAnyCommand => 'السماح دائمًا بكل الأوامر';

  @override
  String get chatRequestAlwaysEditsWhat => 'التعديلات';

  @override
  String get chatRequestAlwaysAnyCommandWhat => 'كل الأوامر';

  @override
  String get filesAskAboutFile => 'اسأل عن هذا الملف';

  @override
  String filesMenuChanges(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'التغييرات · $count ملف',
      many: 'التغييرات · $count ملفًا',
      few: 'التغييرات · $count ملفات',
      two: 'التغييرات · ملفان',
      one: 'التغييرات · ملف واحد',
      zero: 'التغييرات',
    );
    return '$_temp0';
  }

  @override
  String filesProjectSwitchLabel(String name) {
    return 'المشروع $name. تبديل المشروع';
  }

  @override
  String get scheduleNew => 'مطالبة مجدولة جديدة';

  @override
  String get scheduleAsk => 'ماذا يفعل؟';

  @override
  String get scheduleAskHint => 'شغّل الاختبارات ولخّص ما فشل';

  @override
  String get scheduleEveryDay => 'كل يوم';

  @override
  String get scheduleWeekdaysShort => 'أيام العمل';

  @override
  String get scheduleCustom => 'مخصص';

  @override
  String scheduleRuleDaily(String time) {
    return 'كل يوم الساعة $time';
  }

  @override
  String scheduleRuleWeekdays(String time) {
    return 'كل يوم عمل الساعة $time';
  }

  @override
  String scheduleRuleHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'كل $hoursString ساعة',
      few: 'كل $hoursString ساعات',
      two: 'كل ساعتين',
      one: 'كل ساعة',
    );
    return '$_temp0';
  }

  @override
  String scheduleForTime(String time) {
    return 'جدولة الساعة $time';
  }

  @override
  String scheduleForWhen(String when) {
    return 'جدولة $when';
  }

  @override
  String scheduleForHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'جدولة كل $hoursString ساعة',
      few: 'جدولة كل $hoursString ساعات',
      two: 'جدولة كل ساعتين',
      one: 'جدولة كل ساعة',
    );
    return '$_temp0';
  }

  @override
  String scheduleForToday(String time) {
    return 'جدولة اليوم الساعة $time';
  }

  @override
  String scheduleForTomorrow(String time) {
    return 'جدولة غدًا الساعة $time';
  }

  @override
  String scheduleDayAt(String day, String time) {
    return '$day، $time';
  }

  @override
  String scheduleSent(String when) {
    return 'أُرسل $when';
  }

  @override
  String scheduleNotSent(String when) {
    return 'لم يُرسل $when';
  }

  @override
  String scheduleSkippedWhen(String when) {
    return 'تخطّى $when';
  }

  @override
  String get scheduleSendingNow => 'يُرسل الآن';

  @override
  String get scheduleDayToday => 'اليوم';

  @override
  String get scheduleDayYesterday => 'أمس';

  @override
  String get scheduleWheelToday => 'اليوم';

  @override
  String get scheduleWheelTomorrow => 'غدًا';

  @override
  String get scheduleWheelDay => 'اليوم';

  @override
  String get scheduleWheelHowOften => 'كم مرة';

  @override
  String get scheduleClosedNote => 'يعمل حتى والتطبيق مغلق، وقد يتأخر قليلًا.';

  @override
  String get scheduleNeedPrompt => 'اكتب أولًا ما يجب أن يفعله.';

  @override
  String get scheduleNeedFuture => 'اختر وقتًا بعد الآن.';

  @override
  String get scheduleInThisConversation => 'في هذه المحادثة';

  @override
  String get scheduleOpenRun => 'افتح المحادثة';

  @override
  String get scheduleDigestShort => 'ملاحظة كل صباح بما جرى تشغيله';

  @override
  String get scheduleExactNote => 'قد تبدأ التشغيلات متأخرة قليلًا.';

  @override
  String get scheduleExactAllow => 'السماح بالتوقيت الدقيق';

  @override
  String get scheduleNotifyOff =>
      'الإشعارات متوقفة، فلن يُعلمك التشغيل بأنه أُرسل.';

  @override
  String get inboxTitle => 'الوارد';

  @override
  String get inboxEmptyTitle => 'لا شيء ينتظرك';

  @override
  String get inboxFinishedLabel => 'انتهت ولم تُفتح بعد';

  @override
  String inboxOpenLabel(String conversation) {
    return 'فتح $conversation';
  }

  @override
  String inboxOpenOnServer(String server) {
    return 'فتح على $server';
  }

  @override
  String get inboxWaitingForAnswer => 'ينتظر إجابتك';

  @override
  String get inboxAsksPermission => 'يطلب إذنك';

  @override
  String get inboxAsksQuestion => 'يسألك سؤالًا';

  @override
  String inboxUnreachable(String servers) {
    return '$servers لا يستجيب. ما ينتظرك هناك غير معروض.';
  }

  @override
  String get kitRequestOpen => 'فتح';

  @override
  String goToNeedsYouHint(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'ينتظر $count',
      many: 'ينتظر $count',
      few: 'تنتظر $count',
      two: 'ينتظر اثنان',
      one: 'ينتظر واحد',
      zero: 'لا شيء ينتظر',
    );
    return '$_temp0';
  }

  @override
  String inboxEmptyPartialLine(String servers) {
    return '$servers لا يستجيب، فقد يكون لديه المزيد';
  }

  @override
  String get kitFieldOptional => 'اختياري';

  @override
  String get reportProblemAttachedLead =>
      'تفاصيل الخطأ ومعلومات التطبيق مرفقة.';

  @override
  String get reportProblemJobLead =>
      'سجل المهمة وتفاصيل الخطأ ومعلومات التطبيق مرفقة.';

  @override
  String get reportProblemJobNoLogLead =>
      'لم يُحفظ سجل لهذه المهمة. تفاصيل الخطأ ومعلومات التطبيق مرفقة.';

  @override
  String reportProblemKeptLead(String when) {
    return 'لوحظ $when. تفاصيله ومعلومات التطبيق مرفقة.';
  }

  @override
  String get reportProblemNothingTitle => 'لم يُلاحظ أي خطأ';

  @override
  String get reportProblemNothingLead =>
      'صف ما حدث. كلماتك ومعلومات التطبيق تُرسل مع التقرير.';

  @override
  String get reportProblemPrivacy =>
      'تُحذف كلمات المرور والمفاتيح وعناوين الخوادم.';

  @override
  String get reportProblemAddLabel => 'هل من شيء تضيفه؟';

  @override
  String get reportProblemAddHint => 'ماذا كنت تفعل عندما حدث ذلك؟';

  @override
  String get reportProblemSendGitHub => 'إرسال كمشكلة على GitHub';

  @override
  String get reportProblemSendHint =>
      'راجعه على GitHub قبل نشره. سيكون علنيًا.';

  @override
  String get reportProblemCopyShort => 'نسخ';

  @override
  String get reportProblemKeptLink => 'كل ما احتفظ به هذا الهاتف';

  @override
  String welcomePhoneCost(String time, String size) {
    return '$time · تنزيل $size';
  }

  @override
  String get welcomePhoneSetUp => 'الإعداد على هذا الهاتف';

  @override
  String welcomePhoneSettingUp(int percent) {
    return 'جارٍ الإعداد · $percent٪';
  }

  @override
  String welcomePhoneStopped(int percent) {
    return 'اكتمل الإعداد بنسبة $percent٪';
  }

  @override
  String get welcomePhoneContinue => 'متابعة الإعداد';

  @override
  String get welcomePhoneReady => 'مثبّت وجاهز';

  @override
  String get welcomePhoneOpen => 'البدء على هذا الهاتف';

  @override
  String get welcomeComputerLine => 'اقرن برمز، أو اكتب عنوانه';

  @override
  String get welcomeComputerConnect => 'الاتصال بحاسوبي';

  @override
  String get welcomeMoreWays => 'طرق أخرى للإعداد';

  @override
  String get welcomeDemoLine => 'محادثة تجريبية';

  @override
  String kitProgressRingPercent(String percent) {
    return '$percent٪';
  }

  @override
  String get kitProgressRingPercentSign => '٪';

  @override
  String kitProgressRingSteps(String done, String total) {
    return '$done من $total';
  }

  @override
  String get kitProgressRingFinished => 'اكتمل';

  @override
  String phoneSetupRingDownloading(String name) {
    return 'جارٍ تنزيل $name';
  }

  @override
  String phoneSetupRingUnpacking(String name) {
    return 'جارٍ فك ضغط $name';
  }

  @override
  String phoneSetupRingInstalling(String name) {
    return 'جارٍ تثبيت $name';
  }

  @override
  String get phoneSetupRingChecking => 'جارٍ فحص هذا الهاتف';

  @override
  String get phoneSetupRingPausedTitle => 'الإعداد متوقف مؤقتًا';

  @override
  String get phoneSetupRingInterruptedTitle => 'توقف الإعداد قبل اكتماله';

  @override
  String get phoneSetupRingStoppedNote =>
      'تبقى الخطوات المكتملة، ويُستأنف التنزيل من حيث توقف.';

  @override
  String phoneSetupRingMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes دقيقة تقريبًا متبقية',
      many: '$minutes دقيقة تقريبًا متبقية',
      few: '$minutes دقائق تقريبًا متبقية',
      two: 'دقيقتان تقريبًا متبقيتان',
      one: 'دقيقة واحدة تقريبًا متبقية',
      zero: 'أقل من دقيقة متبقية',
    );
    return '$_temp0';
  }

  @override
  String get phoneSetupRingUnderMinute => 'أقل من دقيقة متبقية';

  @override
  String get phoneSetupRingWifi => 'Wi-Fi';

  @override
  String get phoneSetupRingMobileData => 'بيانات الجوال';

  @override
  String get phoneSetupRingSafeToLeave =>
      'يمكنك المغادرة. سنخبرك عندما يصبح جاهزًا.';

  @override
  String get phoneSetupRingSafeToLeaveQuiet =>
      'يمكنك المغادرة. يستمر الإعداد في الخلفية.';

  @override
  String get phoneSetupRingPause => 'إيقاف الإعداد مؤقتًا';

  @override
  String get phoneSetupRingDetailsTitle => 'تفاصيل الإعداد';

  @override
  String kitProgressRingOfTotal(String total) {
    return 'من $total';
  }

  @override
  String get kitProgressRingStepsDone => 'خطوات مكتملة';

  @override
  String get devPreviewDetails => 'تفاصيل المعاينة';

  @override
  String get devPreviewShowDetails => 'عرض التفاصيل';

  @override
  String get devPreviewAddress => 'العنوان';

  @override
  String get devPreviewAskHint => 'اطلب تغييرًا…';

  @override
  String get devPreviewAskSend => 'أرسل إلى المحادثة';

  @override
  String get devPreviewAskStopAndSend => 'أوقف الوكيل وأرسل';

  @override
  String get devPreviewAskVoice => 'قل التغيير بصوتك';

  @override
  String get devPreviewAskListening => 'يستمع…';

  @override
  String get devPreviewAskDoneSpeaking => 'انتهيت من الكلام';

  @override
  String get devPreviewAskSent => 'أُرسل إلى المحادثة';

  @override
  String get devPreviewAskSentJoins => 'أُضيف إلى ما يعمل عليه الوكيل';

  @override
  String get devPreviewAskSentAfter => 'أُرسل. سيُنفَّذ بعد الرد الحالي';

  @override
  String get devPreviewAskSentRestart => 'أُرسل. بدأ الوكيل من جديد به';

  @override
  String get devPreviewAskNotSent => 'لم يُرسل. كلماتك ما زالت هنا';

  @override
  String get devPreviewAskNotHeard => 'لم أفهم ذلك. حاول مرة أخرى أو اكتبه';

  @override
  String teamMergeGateQuestion(int count, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'دمج $count تغيير في $branch؟',
      many: 'دمج $count تغييرًا في $branch؟',
      few: 'دمج $count تغييرات في $branch؟',
      two: 'دمج تغييرين في $branch؟',
      one: 'دمج تغيير واحد في $branch؟',
      zero: 'دمج التغييرات في $branch؟',
    );
    return '$_temp0';
  }

  @override
  String teamMergeGateChecksPassed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'نجحت الفحوص الـ$count كلها',
      many: 'نجحت الفحوص الـ$count كلها',
      few: 'نجحت الفحوص الـ$count كلها',
      two: 'نجح الفحصان',
      one: 'نجح الفحص',
    );
    return '$_temp0';
  }

  @override
  String get teamMergeGateApprove => 'اعتماد الدمج';

  @override
  String get teamMergeGateIfIgnored =>
      'تبقى التغييرات في فرعها. لا يُدمج شيء حتى تقول ذلك.';

  @override
  String teamMergeGateAnnouncement(String question) {
    return 'جاهز للدمج. $question';
  }

  @override
  String get teamMergeGateStale => 'الدمج متوقف حتى يستجيب الفريق مجددًا.';

  @override
  String teamTaskNowOn(String who, String step) {
    return 'الآن: $who على $step';
  }

  @override
  String teamTaskNowWorker(String who) {
    return 'الآن: $who يعمل';
  }

  @override
  String get teamTaskNowWorking => 'الآن: العمل جارٍ على مهمتك';

  @override
  String teamTaskNowStarting(String who) {
    return 'الآن: $who يبدأ';
  }

  @override
  String get teamTaskNowStartingWorker => 'الآن: تشغيل عامل';

  @override
  String get teamTaskNowWaitingWorker => 'الآن: بانتظار عامل';

  @override
  String get teamTaskNowPlanning => 'الآن: تخطيط الخطوات';

  @override
  String get teamTaskNowReviewing => 'الآن: مراجعة التغييرات';

  @override
  String get teamTaskNextReview => 'التالي: المراجعة';

  @override
  String get teamTaskNextMerge => 'التالي: الدمج';

  @override
  String get teamTaskNextWork => 'التالي: يبدأ العامل';

  @override
  String get teamTaskStepsDone => 'اكتملت كل الخطوات';

  @override
  String teamTaskFinishedAgo(String age) {
    return 'انتهت قبل $age';
  }

  @override
  String get kitComposerTrayClose => 'إغلاق';

  @override
  String get composerTrayPhotos => 'الصور';

  @override
  String get composerTrayCamera => 'الكاميرا';

  @override
  String get composerTrayFile => 'ملف';

  @override
  String get composerTrayMore => 'المزيد';

  @override
  String chatLiveReading(String name) {
    return 'يقرأ $name';
  }

  @override
  String get chatLiveReadingAny => 'يقرأ ملفًا';

  @override
  String chatLiveEditing(String name) {
    return 'يعدّل $name';
  }

  @override
  String get chatLiveEditingAny => 'يعدّل الملفات';

  @override
  String chatLiveWriting(String name) {
    return 'يكتب $name';
  }

  @override
  String get chatLiveWritingAny => 'يكتب ملفًا';

  @override
  String chatLiveRunning(String name) {
    return 'يشغّل $name';
  }

  @override
  String get chatLiveRunningAny => 'يشغّل أمرًا';

  @override
  String chatLiveSearching(String name) {
    return 'يبحث عن $name';
  }

  @override
  String get chatLiveSearchingAny => 'يبحث في المشروع';

  @override
  String get chatLiveSearchingWeb => 'يبحث في الويب';

  @override
  String get chatLivePlanning => 'يخطّط للخطوات';

  @override
  String get terminalSessionsLabel => 'الطرفيات المفتوحة';

  @override
  String get terminalSessionEnded => 'انتهت';

  @override
  String localTerminalStopNamed(String name) {
    return 'إيقاف $name';
  }

  @override
  String localTerminalCloseNamed(String name) {
    return 'إغلاق $name';
  }

  @override
  String get terminalDevServerRunning => 'خادم التطوير يعمل';

  @override
  String get terminalOpenPreview => 'فتح المعاينة';

  @override
  String terminalPlaceLabel(String place) {
    return 'تعمل على $place';
  }

  @override
  String terminalPlaceSwitchLabel(String place) {
    return 'تعمل على $place، تغيير مكان تشغيلها';
  }

  @override
  String get terminalScreenDetailState => 'الحالة';

  @override
  String get terminalStateRunning => 'تعمل';

  @override
  String get terminalStateEnded => 'انتهت';

  @override
  String connectionTryingAgainIn(int seconds) {
    return 'المحاولة مجددًا خلال $seconds ث';
  }

  @override
  String get connectionTryingAgainNow => 'جارٍ المحاولة الآن…';

  @override
  String get chatsHomeNothingRunning => 'لا شيء يعمل الآن';

  @override
  String get chatsHomeNothingRunningBody =>
      'ابدأ محادثة، أو افتح واحدة لتكمل من حيث توقفت.';

  @override
  String get chatsHomeShowAll => 'إظهار كل المحادثات';

  @override
  String get chatsHomeEmptyCalmBody =>
      'قل ما تريد بناءه، ويبدأ وكيل العمل عليه.';

  @override
  String get chatsHomeConnectComputer => 'ربط حاسوب';

  @override
  String get chatsHomeProjectEmptyBody =>
      'اطلب شيئًا في هذا المشروع، وسيظهر هنا.';

  @override
  String get scheduleEmptyBody =>
      'اختر وقتًا، وسيرسل هذا الهاتف الطلب نيابةً عنك.';

  @override
  String get projectsLoadFailedTitle => 'لم تُحمَّل المشاريع';

  @override
  String get chatStopAfterStep => 'توقف بعد هذه الخطوة';

  @override
  String get chatStopAfterStepPrompt =>
      'من فضلك توقف بعد الخطوة التي تعمل عليها الآن وانتظرني قبل أن تفعل أي شيء آخر.';

  @override
  String get appearanceModeAuto => 'تلقائي';

  @override
  String get appearanceModeSaver => 'توفير';

  @override
  String get appearanceSaverNote =>
      'أسود خالص بلا حركة. يوفّر البطارية في شاشات OLED.';

  @override
  String get appearanceAccentTitle => 'لون التمييز';

  @override
  String get appearanceAccentMint => 'نعناعي';

  @override
  String get appearanceAccentTeal => 'فيروزي';

  @override
  String get appearanceAccentBlue => 'أزرق';

  @override
  String get appearanceAccentViolet => 'بنفسجي';

  @override
  String get appearanceAccentPink => 'وردي';

  @override
  String get appearanceAccentMono => 'أحادي';

  @override
  String get appearanceAccentSilver => 'فضي';

  @override
  String get appearanceSurfaceSolid => 'صلب';

  @override
  String get appearanceSurfaceSoft => 'ناعم';

  @override
  String get appearanceSurfaceBrutal => 'جريء';

  @override
  String get appearanceSurfacePaper => 'ورقي';

  @override
  String get signalStyleName => 'إشارة';

  @override
  String get signalShow => 'إظهار';

  @override
  String get signalHide => 'إخفاء';

  @override
  String signalRowRunningCommand(String command) {
    return 'يشغّل $command';
  }

  @override
  String get appearanceFineTuneCorners => 'الزوايا';

  @override
  String get appearanceFineTuneCornersRound => 'مستديرة';

  @override
  String get appearanceFineTuneCornersSoft => 'ناعمة';

  @override
  String get appearanceFineTuneCornersSharp => 'حادة';

  @override
  String get appearanceFineTuneDepth => 'العمق';

  @override
  String get appearanceFineTuneEdges => 'الحواف';

  @override
  String appearanceFineTuneReset(String style) {
    return 'إعادة إعدادات $style الافتراضية';
  }

  @override
  String get kitTourNext => 'التالي';

  @override
  String get kitTourSkip => 'تخطٍّ';

  @override
  String get kitTourDone => 'تم';

  @override
  String kitTourStep(int current, int total) {
    return 'الخطوة $current من $total';
  }

  @override
  String get tipStopAfterStep => 'اضغط مطولًا للتوقف بعد هذه الخطوة';

  @override
  String get tipStopAfterStepTitle => 'التوقف بعد هذه الخطوة';

  @override
  String get tipSendLater => 'اضغط مطولًا على إرسال لترسل لاحقًا أو تكرّر';

  @override
  String get tipSendLaterTitle => 'الإرسال لاحقًا أو التكرار';

  @override
  String get tourAppearanceStart => 'أرني كيف · 30 ث';

  @override
  String get tourNotNow => 'ليس الآن';

  @override
  String get tipsTitle => 'نصائح';

  @override
  String get tipsHubSubtitle => 'إيماءات مخفية وجولات قصيرة';

  @override
  String get tipsSearchAliases =>
      'نصائح جولة شرح تلميحات إيماءات ضغط مطول كيف أتعلم tips tutorial';

  @override
  String get tipsIntro =>
      'تظهر كل نصيحة في وقتها، مرتين على الأكثر. أعد أيًّا منها من هنا.';

  @override
  String get tipsShowMe => 'أرني';

  @override
  String get tipsTipsLabel => 'نصائح';

  @override
  String get tipsToursLabel => 'شروح قصيرة';

  @override
  String get chatStopAfterStepPending => 'سيتوقف بعد هذه الخطوة';

  @override
  String get chatStopAfterStepUndo => 'تراجع';

  @override
  String get chatStopAfterStepUndone => 'سيواصل العمل.';

  @override
  String get chatStopAfterStepTooLate =>
      'فات الأوان: وصلت الملاحظة إلى الوكيل.';

  @override
  String get chatLiveShowSteps => 'عرض الخطوات';

  @override
  String get chatLiveHideSteps => 'إخفاء الخطوات';

  @override
  String get kitComposerStopPending => 'سيتوقف بعد هذه الخطوة';

  @override
  String get phraseChipsRetryStarter => 'حاول مرة أخرى';

  @override
  String get tourMakeItYoursTitle => 'اجعله على ذوقك';

  @override
  String get tourMakeItYoursOffer =>
      'اجعله على ذوقك: فاتح أو داكن، والألوان، والنمط، في أربع خطوات';

  @override
  String get tourMakeItYoursColours =>
      'ابدأ هنا: فاتح أو داكن، وسمة، ولون تمييز. تعرض المعاينة في الأعلى كل تغيير فورًا.';

  @override
  String get tourMakeItYoursStyle =>
      'اختر كيف تبدو البطاقات والأشرطة: صلب أو ناعم أو جريء أو ورقي.';

  @override
  String get kitGuideChanges => 'يغيّر';

  @override
  String get kitGuideAll => 'الكل';

  @override
  String get kitGuideBack => 'العودة إلى كل الأزرار';

  @override
  String get appearanceGroupColours => 'الألوان';

  @override
  String get appearanceGroupStyle => 'النمط';

  @override
  String get appearanceGroupBackground => 'الخلفية';

  @override
  String get appearanceGroupComfort => 'النص والحركة';

  @override
  String appearanceGroupPair(String first, String second) {
    return '$first، $second';
  }

  @override
  String get appearanceChipMode => 'الوضع';

  @override
  String get appearanceChipTheme => 'السمة';

  @override
  String get appearanceChipAccent => 'لون التمييز';

  @override
  String get appearanceChipStyle => 'النمط';

  @override
  String get appearanceChipShape => 'الشكل';

  @override
  String get appearanceChipLook => 'المظهر';

  @override
  String get appearanceChipStrength => 'القوة';

  @override
  String get appearanceChipText => 'حجم النص';

  @override
  String get appearanceChipMotion => 'الحركة';

  @override
  String get appearanceChipGlow => 'توهج الرد';

  @override
  String get appearanceAskMode => 'فاتح أم داكن؟';

  @override
  String get appearanceAskTheme => 'أي ألوان؟';

  @override
  String get appearanceAskAccent => 'أي لون تمييز؟';

  @override
  String get appearanceAskStyle => 'كيف تبدو الأسطح؟';

  @override
  String get appearanceAskShape => 'الزوايا والمسافات';

  @override
  String get appearanceAskLook => 'ماذا يتحرك خلف صفحاتك؟';

  @override
  String get appearanceAskStrength => 'ما مدى قوتها؟';

  @override
  String get appearanceAskText => 'ما حجم النص الذي تريده؟';

  @override
  String get appearanceAskMotion => 'كم من الحركة تريد؟';

  @override
  String get appearanceAskGlow => 'توهج أثناء كتابة الرد؟';

  @override
  String get appearanceChangesMode => 'كل صفحة، فاتحة أو داكنة';

  @override
  String get appearanceChangesTheme => 'ألوان كل صفحة';

  @override
  String get appearanceChangesAccent => 'الأزرار والروابط وزر الإرسال والتوهج';

  @override
  String get appearanceChangesStyle => 'كل بطاقة وشريط وفقاعة رسالة';

  @override
  String get appearanceChangesShape => 'زوايا هذا النمط ومسافاته';

  @override
  String get appearanceChangesLook => 'خلفية كل صفحة';

  @override
  String get appearanceChangesStrength => 'كم يظهر من الخلفية';

  @override
  String get appearanceChangesText => 'النص في كل مكان، فوق حجم نص هاتفك';

  @override
  String get appearanceChangesMotion => 'الحركات في التطبيق كله';

  @override
  String get appearanceChangesGlow => 'حدود مربع الرسالة';

  @override
  String get appearanceOffInSaver => 'متوقف في التوفير';

  @override
  String get appearanceLookOff => 'متوقفة';

  @override
  String get appearanceStrengthNoLook => 'بلا مظهر';

  @override
  String appearanceShapeValue(String corners, String spacing) {
    return '$corners، $spacing';
  }

  @override
  String get appearanceShapeSpacing => 'المسافات';

  @override
  String get appearanceSpacingRoomy => 'واسعة';

  @override
  String get appearanceSpacingCompact => 'مضغوطة';

  @override
  String appearanceShapeNote(String style, String corners, String spacing) {
    return 'يبدأ $style بزوايا $corners ومسافات $spacing. اختيار نمط آخر يعيدها من البداية.';
  }

  @override
  String get appearanceMotionPhraseFull => 'حركة كاملة';

  @override
  String get appearanceMotionPhraseCalm => 'حركة هادئة';

  @override
  String get appearanceMotionPhraseOff => 'بلا حركة';

  @override
  String get appearanceGlowOff => 'متوقف';

  @override
  String get appearanceGlowOffHint => 'حدود عادية';

  @override
  String get appearanceGlowClassicHint => 'ضوء يدور حول المربع';

  @override
  String get appearanceGlowSoftHint => 'حلقة تتنفس ببطء';

  @override
  String get appearanceModeAutoNote => 'يتبع هاتفك: فاتح نهارًا وداكن ليلًا.';

  @override
  String appearanceSeeAllThemes(int count) {
    return 'عرض كل السمات ($count)';
  }

  @override
  String appearanceSeeAllLooks(int count) {
    return 'عرض كل المظاهر ($count)';
  }

  @override
  String appearanceAccentMatchNote(String theme) {
    return '«مطابقة» يستخدم لون تمييز $theme نفسه.';
  }

  @override
  String get appearanceStrengthNeedsLook => 'الخلفية متوقفة، فلا شيء لتقويته.';

  @override
  String get appearanceChooseLookFirst => 'اختر مظهرًا أولًا';

  @override
  String get appearanceSaverHoldsStyle =>
      'التوفير يُبقي البطاقات صلبة والخلفية ساكنة لتوفير بطارية هذه الشاشة. يعود نمطك عند إيقاف التوفير.';

  @override
  String get appearanceSaverHoldsBackground =>
      'التوفير يُبقي البطاقات صلبة والخلفية ساكنة لتوفير بطارية هذه الشاشة. تعود خلفيتك عند إيقاف التوفير.';

  @override
  String get appearanceTurnOffSaver => 'إيقاف التوفير';

  @override
  String get appearanceSaverMotion => 'التوفير يُبقي الحركة متوقفة.';

  @override
  String get appearanceGuideStart =>
      'ابدأ بالألوان، فهي التغيير الأكبر. يُحفظ كل اختيار فورًا، ويمكنك لمس أي صف في أي وقت.';

  @override
  String get appearanceGuideSkip => 'تخطي الدليل';

  @override
  String get appearanceGuideResume => 'تابع من حيث توقفت، أو المس أي زر.';

  @override
  String get appearanceGuideAllSetTitle => 'تم كل شيء.';

  @override
  String get appearanceGuideAllSet =>
      'اختياراتك محفوظة. المس أي زر لتغيير أحدها.';

  @override
  String get appearanceGuideAgain => 'أرشدني مرة أخرى';

  @override
  String get appearanceGuideReset => 'إعادة الإعدادات الافتراضية';

  @override
  String get appearanceGuideResetConfirm =>
      'إعادة كل اختيارات المظهر إلى الافتراضي؟ يُعاد الوضع الفاتح أو الداكن والسمة ولون التمييز والنمط والخلفية وحجم النص والحركة وتوهج الرد.';

  @override
  String get appearanceGuideResetDo => 'إعادة';

  @override
  String get appearanceGuideResetKeep => 'الاحتفاظ باختياراتي';

  @override
  String appearanceGuideCountIntro(int count) {
    return '$count أسئلة سريعة، كلها اختيارية';
  }

  @override
  String appearanceGuideCountSeen(int seen, int total) {
    return 'شوهد $seen من $total';
  }

  @override
  String appearanceGuideCountAt(int at, int total) {
    return '$at من $total';
  }

  @override
  String get appearanceGuideSavesAsYouGo => 'يُحفظ كل شيء فورًا';

  @override
  String get appearanceGuideStartButton => 'ابدأ بالألوان';

  @override
  String appearanceGuideContinue(String group) {
    return 'متابعة: $group';
  }

  @override
  String appearanceGuideNext(String name) {
    return 'التالي: $name';
  }

  @override
  String get appearanceGuideFinish => 'إنهاء';

  @override
  String appearanceGuidePlace(
    String label,
    String value,
    int at,
    int count,
    String group,
  ) {
    return '$label، $value، السؤال $at من $count في $group';
  }

  @override
  String get tourMakeItYoursBackground => 'اختر ما يتحرك خلف صفحاتك، أو أوقفه.';

  @override
  String get tourMakeItYoursComfort =>
      'كبّر النص، وهدّئ الحركة، واختر التوهج أثناء كتابة الرد.';

  @override
  String get appearanceAccentMatchShort => 'مطابقة';

  @override
  String get chatTurnStoppedElsewhere => 'أُوقف هذا الرد من تطبيق أو جهاز آخر.';

  @override
  String get chatTurnPausedInactive =>
      'أوقف الخادم هذا الرد مؤقتًا بعد ساعة دون عمل جديد.';

  @override
  String get chatTurnStoppedServerRestart =>
      'أُعيد تشغيل الخادم، فتوقف هذا الرد.';

  @override
  String get chatTurnSuperseded => 'حلّ تشغيل أحدث محل هذا الرد.';

  @override
  String get chatTurnStoppedEarly => 'أُوقف هذا الرد قبل أن يكتمل.';

  @override
  String get settingsV3ModelNewOnPhone => 'المحادثات الجديدة على هذا الهاتف';

  @override
  String chatLoadAgentMissing(String agent) {
    return 'لا توجد هذه المحادثة لدى $agent على هذا الهاتف. ارجع وافتحها من محادثاتك.';
  }

  @override
  String chatLoadAgentUnreadable(String agent) {
    return 'استجاب $agent على هذا الهاتف، لكن التطبيق لم يستطع قراءة الرد. حاول مرة أخرى، أو أبلغ عن مشكلة.';
  }

  @override
  String get productErrorUnreadableReply =>
      'جاء رد، لكن التطبيق لم يستطع قراءته. حاول مرة أخرى، أو أبلغ عن مشكلة.';

  @override
  String get settingsV3SavedOnPhone =>
      'يُحفظ على هذا الهاتف. التطبيقات الأخرى التي تستخدم خادم هذا الهاتف تحصل على هذا أيضًا.';

  @override
  String settingsV3UseMyDefaultDonePhone(String what) {
    return 'أُعيد على هذا الهاتف إلى اختياراتك: $what.';
  }

  @override
  String get chatStallSubagent =>
      'هذا الوكيل الفرعي هادئ منذ مدة. يتوقف مع محادثته الرئيسية: أوقف الرد هناك.';

  @override
  String get serverPageThisPhonesServer => 'خادم هذا الهاتف';

  @override
  String kitBackgroundWorkOne(String task, String time) {
    return 'ما زال يعمل في الخلفية · $task · $time';
  }

  @override
  String kitBackgroundWorkMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مهمة ما زالت تعمل في الخلفية',
      few: '$count مهام ما زالت تعمل في الخلفية',
      two: 'مهمتان ما زالتا تعملان في الخلفية',
      one: 'مهمة واحدة ما زالت تعمل في الخلفية',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkTask(String task, String time) {
    return '$task · $time';
  }

  @override
  String kitBackgroundWorkMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes د',
      zero: 'أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String get kitBackgroundWorkGoToStep => 'يعرض الخطوة التي بدأته';

  @override
  String kitBackgroundWorkOneAfterStop(String task, String time) {
    return 'ما زال يعمل بعد توقّف التبادل · $task · $time';
  }

  @override
  String kitBackgroundWorkManyAfterStop(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مهمة ما زالت تعمل بعد توقّف التبادل',
      few: '$count مهام ما زالت تعمل بعد توقّف التبادل',
      two: 'مهمتان ما زالتا تعملان بعد توقّف التبادل',
      one: 'مهمة واحدة ما زالت تعمل بعد توقّف التبادل',
    );
    return '$_temp0';
  }

  @override
  String chatAgentTurnLostConnectionTo(String agent, String company) {
    return 'فقد $agent اتصاله بـ$company، فتوقّف التبادل.';
  }

  @override
  String chatAgentTurnLostConnection(String agent) {
    return 'فقد $agent اتصاله بمزوّد النموذج، فتوقّف التبادل.';
  }

  @override
  String chatSendFailureLostConnectionTo(String agent, String company) {
    return 'فقد $agent اتصاله بـ$company';
  }

  @override
  String chatSendFailureLostConnection(String agent) {
    return 'فقد $agent اتصاله بمزوّد النموذج';
  }

  @override
  String chatBackgroundWorkWaitingFor(String program) {
    return 'بانتظار $program';
  }

  @override
  String chatSendFailureSignIn(String agent) {
    return 'يحتاج $agent إلى تسجيل دخولك';
  }

  @override
  String chatSendFailureNoCredit(String agent) {
    return 'لا يملك $agent رصيدًا لهذا النموذج';
  }

  @override
  String get chatSendFailureModelUnavailable => 'هذا النموذج غير متاح';

  @override
  String chatSendFailureBusy(String agent) {
    return '$agent مشغول الآن';
  }

  @override
  String get chatSendFailureAgentFallback => 'الوكيل';

  @override
  String get chatSendFailureSignInAction => 'تسجيل الدخول';

  @override
  String get chatSendFailureAccountAction => 'فتح الحساب';

  @override
  String get chatSendFailureDetailsAgent => 'الوكيل';

  @override
  String get chatSendFailureDetailsModel => 'النموذج';

  @override
  String get chatSendFailureDetailsCode => 'رمز الخطأ';

  @override
  String get chatSendFailureNewConversation =>
      'لا يستطيع هذا الوكيل متابعة هذه المحادثة';

  @override
  String chatSendFailureNeedsAccount(String agent) {
    return 'يحتاج هذا النموذج إلى حساب $agent';
  }

  @override
  String chatSendFailureSignInToAgent(String agent) {
    return 'تسجيل الدخول إلى $agent';
  }

  @override
  String get chatComposerNoteDismiss => 'إخفاء الملاحظة';

  @override
  String get chatAnnounceReplyFinished => 'اكتمل الرد';

  @override
  String get chatAnnounceNeedsYou => 'الوكيل يحتاج إليك';

  @override
  String get runningPhoneTitle => 'يعمل على هذا الهاتف';

  @override
  String runningPhoneSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عمليات تعمل · $memory',
      one: 'عملية واحدة تعمل · $memory',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneAutoRefresh =>
      'يتحقق مجددًا كل 5 ثوانٍ ما دامت الصفحة مفتوحة';

  @override
  String get runningPhoneLimitTitle => 'قد تتوقف الخوادم';

  @override
  String runningPhoneLimitBody(int count, int limit) {
    return '$count من $limit عملية في الخلفية: فوق $limit يوقف أندرويد أقدم عمليات هذا التطبيق أولًا، وهي الخوادم. أوقف ما بقي يعمل أو ما لا تحتاجه، أو ارفع الحد من خيارات المطوّر › تعطيل قيود العمليات الفرعية.';
  }

  @override
  String runningPhoneGroup(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind': 'بقيت تعمل',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'مساعد الوكلاء',
      'aiTeam': 'فريق الذكاء الاصطناعي',
      'terminal': 'الطرفية',
      'setup': 'الإعداد والفحوص',
      'helpers': 'خدمات مساعدة',
      'app': 'هذا التطبيق',
      'other': 'أخرى',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneGroupChat(String title) {
    return 'Claude Code · $title';
  }

  @override
  String runningPhoneName(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'flutterTest': 'اختبار Flutter',
      'command': 'أمر',
      'linux': 'Linux',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'مساعد الوكلاء',
      'signIn': 'تسجيل دخول الوكيل',
      'team': 'محرك فريق الذكاء الاصطناعي',
      'node': 'Node.js',
      'python': 'Python',
      'git': 'Git',
      'npm': 'npm',
      'dart': 'Dart',
      'flutter': 'Flutter',
      'gradle': 'Gradle',
      'java': 'Java',
      'search': 'بحث',
      'app': 'هذا التطبيق',
      'other': 'برنامج',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneCpu(String level, String percent) {
    String _temp0 = intl.Intl.selectLogic(level, {
      'idle': 'خامل',
      'light': 'خفيف',
      'busy': 'مشغول',
      'other': 'مشغول جدًا',
    });
    return '$_temp0 · $percent%';
  }

  @override
  String get runningPhoneMarkHeavy => 'ثقيل';

  @override
  String get runningPhoneMarkStuck => 'قد يكون عالقًا';

  @override
  String get runningPhoneMarkLeftBehind => 'بقي يعمل';

  @override
  String runningPhoneFromChat(String title) {
    return 'بدأه Claude Code · $title';
  }

  @override
  String get runningPhoneFromClaude => 'بدأه Claude Code';

  @override
  String get runningPhoneStop => 'أوقف';

  @override
  String runningPhoneStopNamed(String name) {
    return 'أوقف $name';
  }

  @override
  String runningPhoneStopTitle(String name) {
    return 'إيقاف $name؟';
  }

  @override
  String get runningPhoneStopBody =>
      'يتلقى طلب إيقاف لطيفًا أولًا، ثم إيقافًا قسريًا إن استمر.';

  @override
  String runningPhoneStopAllLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أوقف كل العمليات التي بقيت تعمل ($count)',
      one: 'أوقف العملية التي بقيت تعمل',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneStopAllDetail => 'لا شيء آخر يوقفها، وإيقافها آمن.';

  @override
  String get runningPhoneStopAllTitle => 'إيقاف كل ما بقي يعمل؟';

  @override
  String runningPhoneStopAllBody(String names) {
    return '$names. تتلقى كل منها طلب إيقاف لطيفًا أولًا، ثم إيقافًا قسريًا إن استمرت.';
  }

  @override
  String runningPhoneStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أُوقفت $count عمليات',
      one: 'أُوقفت عملية واحدة',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneAlreadyEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count كانت قد انتهت',
      one: 'واحدة كانت قد انتهت',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneNotStoppedTitle => 'لم يتوقف كل شيء';

  @override
  String runningPhoneRefusedApp(String name) {
    return '$name: لا يمكن إيقاف هذا التطبيق نفسه من هنا.';
  }

  @override
  String runningPhoneRefusedServer(String name) {
    return '$name: يشغّل خادمًا. أوقف الخادم من صفحة هذا الهاتف.';
  }

  @override
  String runningPhoneRefusedStillRunning(String name) {
    return '$name: استمر بعد الإيقاف القسري. حاول مرة أخرى، أو أعد تشغيل الهاتف.';
  }

  @override
  String get runningPhoneStopFailed => 'تعذّر الإيقاف. حاول مرة أخرى.';

  @override
  String get runningPhoneLoadFailedTitle => 'تعذّرت قراءة ما يعمل';

  @override
  String get runningPhoneLoadFailedBody => 'لم يستجب الهاتف. حاول مرة أخرى.';

  @override
  String get runningPhoneRefreshFailed =>
      'تعذّر التحقق مجددًا، لذا تُعرض آخر قراءة.';

  @override
  String get runningPhoneEmpty => 'لا شيء آخر يعمل';

  @override
  String get runningPhoneEmptyBody =>
      'عندما يعمل OpenCode أو Claude Code أو طرفية هنا، يظهر في هذه القائمة.';

  @override
  String runningPhoneAbout(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind':
          'بقي يعمل بعد انتهاء Linux الخاص به. لا شيء ينتظره، لذا إيقافه آمن.',
      'openCode': 'جزء من خادم OpenCode على هذا الهاتف.',
      'claudeCode': 'جزء من محادثة Claude Code.',
      'agentHelper': 'يشغّل Claude Code والوكلاء الآخرين على هذا الهاتف.',
      'aiTeam': 'جزء من فريق الذكاء الاصطناعي.',
      'terminal': 'يعمل في طرفية على هذا الهاتف.',
      'setup': 'خطوة إعداد أو فحص.',
      'helpers': 'خدمة مساعدة لهذا التطبيق.',
      'app': 'هذا التطبيق نفسه.',
      'other': 'بدأه هذا التطبيق.',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneHeavyFor(String elapsed) {
    return 'يستخدم أكثر من 80% من نواة المعالج منذ $elapsed.';
  }

  @override
  String runningPhoneStuckWhy(String elapsed) {
    return 'يعمل قرب 100% منذ $elapsed دون محادثة تنتظره.';
  }

  @override
  String get runningPhoneServerRoot =>
      'يشغّل خادمًا. أوقف الخادم من صفحة هذا الهاتف.';

  @override
  String get runningPhoneConversationId => 'معرّف محادثة Claude Code';

  @override
  String get runningPhoneOwner => 'يتبع';

  @override
  String runningPhoneRowNormal(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عمليات تعمل · $memory',
      one: 'عملية واحدة تعمل · $memory',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عمليات بقيت تعمل وما زالت تستخدم الهاتف',
      one: 'عملية بقيت تعمل وما زالت تستخدم الهاتف',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowHeavy(int count, String name, String percent) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ثقيلة، أكثرها $name · $percent%',
      one: '$name ثقيل · $percent%',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowNearLimit(int count, int limit) {
    return '$count من $limit: قد يوقف أندرويد الخوادم';
  }

  @override
  String get runningPhoneRowIdle => 'اعرف ما يعمل وما يسخّن الهاتف';

  @override
  String chatStuckCommand(String time) {
    return '$time · قد يكون عالقًا';
  }

  @override
  String chatStuckMinutes(int minutes) {
    return '$minutes د';
  }

  @override
  String chatStuckHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String chatStuckUnnamed(String time) {
    return 'ما زال يعمل · $time · قد يكون عالقًا';
  }

  @override
  String chatStuckWaitsNote(String command) {
    return 'ينتظر انتهاء $command. أوقفه لتصل رسالتك الآن.';
  }

  @override
  String get chatStuckNeverEnds =>
      'قد لا ينتهي هذا الرد أبدًا. «إيقاف وإرسال» يوصل رسالتك الآن.';

  @override
  String get chatStuckThisStep => 'هذه الخطوة';

  @override
  String get kitComposerAddsToReply => 'تُضاف إلى هذا الرد';

  @override
  String kitComposerCantTakeMidReply(String agent) {
    return 'لا يستطيع $agent استقبال رسالة أثناء الرد.';
  }

  @override
  String get kitTurnLiveStopShort => 'إيقاف';

  @override
  String get frostGlassQuestion => 'الزجاج';

  @override
  String get frostGlassOff => 'إيقاف';

  @override
  String get frostGlassWeak => 'خفيف';

  @override
  String get frostGlassBalanced => 'متوازن';

  @override
  String get frostGlassStrong => 'قوي';

  @override
  String get frostGlassHint =>
      'عندما تمرّ المحادثة تحت مربع الرسالة أو الرأس، يظهر ما تحتهما مموّهًا بلطف. «إيقاف» يُبقيهما مصمتين.';

  @override
  String get frostGlassHeldSaver =>
      'وضع التوفير مفعّل، لذا يبقى مربع الرسالة والرأس مصمتين.';

  @override
  String get frostGlassHeldMotion =>
      'تقليل الحركة مفعّل، لذا يبقى مربع الرسالة والرأس مصمتين.';

  @override
  String get frostGlassHeldContrast =>
      'التباين العالي مفعّل، لذا يبقى مربع الرسالة والرأس مصمتين.';

  @override
  String get frostGlassHeldStyle =>
      'لهذا النمط مظهره الخاص، لذا يبقى مربع الرسالة والرأس مصمتين.';

  @override
  String stopRowSemantics(String agent) {
    return 'إيقاف $agent';
  }
}
