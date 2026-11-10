# Mood Music Sorter V2 — کیت پیاده‌سازی (نسخه Markdown)

> این فایل تبدیل‌شدهٔ `MoodMusicSorter_V2_Implementation_Kit.zip` است و محتوای همهٔ فایل‌های متنی و کد کیت را در یک سند جمع می‌کند. آیکون `assets/moodmusic-icon-1024.png` چون خودش فرمت پشتیبانی‌شده (PNG) است، جداگانه ضمیمه شده.

## فهرست

1. README
2. شروع از اینجا (`START-HERE-FA.md`)
3. محدوده نسخه ۲ و معیار تحویل (`docs/V2-SCOPE-FA.md`)
4. دستور Codex (انگلیسی) (`docs/CODEX-PROMPT-EN.md`)
5. آموزش اعمال بسته و ساخت APK (`docs/APPLY-AND-BUILD-FA.md`)
6. امنیت مخزن و امضای APK (`docs/SECURITY-FA.md`)
7. کپی دسته به پوشه/فلش OTG و اشتراک‌گذاری (`android/CategoryExportHelper.kt`)
8. ساخت ZIP از فایل‌های آهنگ (`android/CategoryZipExporter.kt`)
9. مدل اصلاح دستی حال‌وهوا (`android/MoodCorrectionRecord.kt`)
10. آیکون برنامه (`assets/moodmusic-icon-1024.png`) — فایل جداگانه

---

## README (`README.txt`)

```text
Mood Music Sorter V2 Implementation Kit

Persian start guide: START-HERE-FA.md
Full V2 scope: docs/V2-SCOPE-FA.md
Codex task prompt: docs/CODEX-PROMPT-EN.md
Apply/build guide: docs/APPLY-AND-BUILD-FA.md
Security notes: docs/SECURITY-FA.md
Starter Kotlin helpers: android/
Launcher artwork: assets/

IMPORTANT: This is an implementation kit, not a compiled or integrated APK.
The helpers must be adapted to the actual Android project and tested.
```

---

## شروع از اینجا (`START-HERE-FA.md`)

## بسته پیاده‌سازی Mood Music Sorter V2

این بسته تمام درخواست‌های نسخه ۲ را در یک جا جمع کرده است: نقشه راه هفت‌بخشی، معیار تحویل هر بخش، فایل‌های کمکی Kotlin، آیکون برنامه، دستور کامل برای Codex و آموزش ساخت و آزمایش.

### نکته مهم درباره وضعیت این فایل

این بسته **نسخه نهایی و ادغام‌شده APK نیست**. سورس پروژه داخل مخزن GitHub در فایل `mood-source (1).zip` قرار دارد. در این محیط امکان باز کردن ZIP باینری از اتصال GitHub فراهم نشد؛ بنابراین کدهای داخل `android/` هنوز با کلاس‌ها، دیتابیس و رابط فعلی برنامه ادغام و کامپایل نشده‌اند. به همین دلیل این بسته را به‌عنوان «کیت پیاده‌سازی V2» تحویل می‌دهم، نه APK آماده.

### محتویات

- `docs/V2-SCOPE-FA.md`: تمام ۷ قابلیت و معیارهای پذیرش.
- `docs/CODEX-PROMPT-EN.md`: دستور آماده‌ای که باید به Codex بدهی تا پروژه اصلی را بررسی و نسخه ۲ را روی یک شاخه جدا پیاده کند.
- `docs/APPLY-AND-BUILD-FA.md`: آموزش مرحله‌به‌مرحله استفاده از Codex، گرفتن تغییرات، ساخت APK و نصب روی گوشی.
- `docs/SECURITY-FA.md`: نکات مهم امنیت کلید امضای APK و مخزن عمومی.
- `android/CategoryExportHelper.kt`: کپی واقعی آهنگ‌های یک دسته به پوشه انتخاب‌شده یا فلش OTG و اشتراک‌گذاری دسته.
- `android/CategoryZipExporter.kt`: ساخت ZIP از فایل‌های واقعی آهنگ.
- `android/MoodCorrectionRecord.kt`: مدل پایه برای ذخیره اصلاح دستی تشخیص حال‌وهوا.
- `assets/moodmusic-icon-1024.png`: تصویر آیکون اختصاصی.

### ترتیب اجرای نسخه ۲

۱. موتور تحلیل صوت و تشخیص حال‌وهوا
۲. پخش‌کننده کامل و پخش پس‌زمینه
۳. رابط کاربری مدرن و تم‌های احساسی
۴. سرعت، مصرف رم و باتری
۵. مدیریت کتابخانه، جست‌وجو و پلی‌لیست‌ها
۶. خروجی هر دسته، اشتراک‌گذاری، ZIP و فلش OTG
۷. آیکون نهایی، تست کامل و انتشار APK

### قبل از شروع

- فایل ZIP را از حالت فشرده خارج کن.
- فایل `docs/CODEX-PROMPT-EN.md` را کامل به Codex بده.
- اجازه نده Codex تغییرات را مستقیم روی `main` اعمال کند. باید شاخه جدا و Pull Request بسازد.
- تا وقتی ساخت Debug و تست‌های اصلی موفق نشده‌اند، نسخه فعلی برنامه را جایگزین نکن.

---

## محدوده نسخه ۲ و معیار تحویل (`docs/V2-SCOPE-FA.md`)

## محدوده نسخه ۲ و معیار تحویل

این سند مشخص می‌کند نسخه ۲ دقیقاً چه خروجی‌ای باید داشته باشد. موارد پایین باید روی پروژه واقعی ادغام شوند، نه اینکه فقط در یک سند یا فایل جدا باقی بمانند.

### ۱) تشخیص هوشمندتر حال‌وهوای آهنگ‌ها

- تحلیل محتوای صوتی فایل؛ نام فایل، پوشه و تگ ID3 نباید مبنای تشخیص باشند.
- پردازش محلی و رایگان در حالت پیش‌فرض؛ برنامه نباید برای کار اصلی به API پولی یا اشتراک وابسته شود.
- استخراج ویژگی‌های صوتی مانند شدت و پویایی، انرژی، طیف فرکانسی، تغییرات طیفی و تخمین ضرب‌آهنگ.
- دسته‌های پایه: شاد، غمگین، آرام، بارانی، نوستالژیک، ورزشی/انرژی‌زا و عاشقانه.
- برای دسته‌های ذهنی مثل نوستالژیک و بارانی، سیستم نباید با قطعیت ساختگی تصمیم بگیرد. امتیاز اطمینان پایین باید به «نیازمند بررسی» منجر شود.
- کاربر باید بتواند دسته هر آهنگ را اصلاح کند. اصلاح‌ها با URI یا شناسه پایدار آهنگ در دیتابیس ذخیره شوند.
- اصلاحات کاربر در نسخه‌های بعدی برای ارزیابی و بهبود مدل استفاده شوند. صرف ذخیره اصلاح‌ها به معنی آموزش خودکار مدل نیست.
- نتیجه تحلیل برای هر فایل کش شود تا هر بار اسکن کامل و پردازش سنگین تکرار نشود.

**معیار پذیرش:** قطع اینترنت نباید مانع اسکن و تشخیص محلی شود؛ تغییر نام فایل نباید نتیجه را عوض کند؛ اصلاح دستی باید بعد از بستن و بازکردن برنامه باقی بماند؛ آهنگ با اطمینان پایین نباید با برچسب قطعی نمایش داده شود.

### ۲) پخش‌کننده کامل

- صف پخش و افزودن/حذف/جابجایی آهنگ‌ها.
- پخش تصادفی، تکرار یک آهنگ و تکرار صف.
- کنترل پخش، توقف، آهنگ بعدی و قبلی از نوار اعلان و صفحه قفل.
- پخش پس‌زمینه با سرویس رسانه‌ای استاندارد اندروید و اعلان معتبر.
- حفظ موقعیت پخش و صف در چرخش صفحه یا بازگشت به برنامه.
- مدیریت Audio Focus و قطع/ادامه مناسب هنگام تماس یا تغییر خروجی صدا.

**معیار پذیرش:** با رفتن به Home موسیقی قطع نشود؛ با بستن صفحه کنترل اعلان کار کند؛ جابه‌جایی بین آهنگ‌ها و حالت Shuffle/Repeat بدون توقف ناخواسته انجام شود.

### ۳) رابط کاربری مدرن

- رابط فارسی راست‌چین و امکان انتخاب زبان انگلیسی در تنظیمات.
- صفحه خانه با دسته‌ها و تعداد آهنگ‌های هر دسته.
- صفحه آهنگ با کاور، عنوان، هنرمند در صورت وجود و کنترل پخش.
- انیمیشن‌های سبک و روان، نه انیمیشن‌های سنگین و مصرف‌کننده باتری.
- تم‌های رنگی متناسب با دسته‌ها و حالت تاریک خوانا.
- حفظ معماری فعلی تا حد امکان؛ بازنویسی کامل برنامه فقط در صورت ضرورت فنی.

**معیار پذیرش:** متن فارسی به‌هم‌ریخته یا چپ‌چین نباشد؛ رابط روی نمایشگر Redmi Note 10S بدون بریدگی و لگ قابل استفاده باشد.

### ۴) عملکرد و مصرف منابع

- اسکن و تحلیل فقط روی فایل‌های جدید یا تغییرکرده.
- اجرای خواندن فایل، تحلیل صوت، کپی و ZIP در پس‌زمینه، نه روی UI Thread.
- نمایش پیشرفت اسکن و امکان لغو امن.
- محدودکردن هم‌زمانی پردازش‌ها متناسب با رم دستگاه.
- استفاده از کش و دیتابیس برای جلوگیری از پردازش تکراری.
- بازیابی امن عملیات بعد از بسته‌شدن فرایند، جایی که لازم است.

**معیار پذیرش:** اسکن کتابخانه باعث قفل‌شدن رابط نشود؛ اسکن دومِ کتابخانه بدون تغییر، تحلیل کامل را از ابتدا تکرار نکند.

### ۵) مدیریت کتابخانه

- جست‌وجوی سریع بر اساس نام آهنگ و هنرمند، در صورت موجود بودن اطلاعات.
- ساخت، تغییر نام و حذف پلی‌لیست.
- افزودن/حذف آهنگ از پلی‌لیست.
- مدیریت دسته‌ها و امکان اصلاح دسته آهنگ.
- جلوگیری از رکورد تکراری برای یک فایل.
- مهاجرت امن دیتابیس؛ داده‌های قبلی کاربر نباید پاک شوند.

**معیار پذیرش:** پس از به‌روزرسانی برنامه، کتابخانه قبلی حفظ شود؛ جست‌وجو و تغییر پلی‌لیست نتیجه درست بدهند.

### ۶) خروجی و اشتراک‌گذاری مستقل هر دسته

برای هر دسته به‌صورت جداگانه:
- انتخاب پوشه مقصد با Android Storage Access Framework.
- کپی بایت‌های واقعی آهنگ‌ها در پوشه‌ای به نام همان دسته روی USB OTG یا حافظه انتخاب‌شده.
- اشتراک‌گذاری چند فایل از طریق Android Sharesheet.
- خروجی ZIP شامل فایل‌های واقعی همان دسته.
- مدیریت نام‌های تکراری، فایل حذف‌شده، خطای دسترسی، جداشدن فلش و کمبود فضا.
- نمایش تعداد موفق و ناموفق و امکان مشاهده خطاها.

**معیار پذیرش:** فایل‌های کپی‌شده روی فلش در یک پخش‌کننده مستقل باز شوند؛ ZIP قابل بازشدن باشد؛ لغو یا خطا باعث کرش برنامه نشود.

### ۷) هویت بصری و آیکون

- استفاده از `assets/moodmusic-icon-1024.png` به‌عنوان طرح پایه.
- تولید Adaptive Icon استاندارد با foreground/background مجزا و نسخه گرد.
- تنظیم `android:icon` و `android:roundIcon` در Manifest.
- بررسی ظاهر آیکون در لانچرهای مختلف.

**معیار پذیرش:** آیکون در لانچر، صفحه اطلاعات برنامه و فهرست برنامه‌های اخیر درست دیده شود.

### معیارهای نهایی انتشار

- ساخت موفق Debug APK.
- اجرای تست‌های واحد و بررسی‌های lint موجود پروژه.
- نصب و اجرای آزمایشی روی Android 13 و دستگاهی هم‌رده Redmi Note 10S.
- تست مجوزهای فایل، پخش پس‌زمینه، اعلان، OTG، ZIP و مهاجرت دیتابیس.
- عدم وجود کلید امضا، رمز یا توکن داخل مخزن عمومی.
- تولید APK نهایی از یک workflow قابل تکرار و ارائه Artifact قابل دانلود.

---

## دستور Codex (انگلیسی) (`docs/CODEX-PROMPT-EN.md`)

## Codex task: implement Mood Music Sorter V2

You are working in the public repository `dani8686/moodmusic`. Implement the complete V2 described below. Do not merely write a plan or create disconnected sample files. First inspect the repository and the existing Android project. The Android source may be stored inside `mood-source (1).zip`; inspect the archive, extract the real project into a clean working directory, and determine the actual package names, architecture, database, player, UI toolkit, Gradle/Android versions, and current CI workflow before editing.

### Workflow and safety

1. Create a new branch named `feature/moodmusic-v2` from `main`. Do not commit directly to `main`.
2. Inspect all existing source and project history relevant to the Android app. Preserve current user data and compatible behavior.
3. Do not invent existing classes, schemas, package names, dependency versions, or API availability. Confirm each from source before coding.
4. Prefer small, integrated changes over a wholesale rewrite.
5. Keep the repository public. Never add API keys, signing passwords, private keystores, personal tokens, or credentials.
6. Audit the existing `signing.p12` and any hardcoded signing passwords. Remove signing material from tracked source and stop embedding passwords in Gradle. Recommend key rotation if a real signing key has been exposed. Do not delete user data or overwrite a production signing key without documenting the consequences.
7. Keep the core music scan and mood analysis usable without network access. Do not introduce paid APIs, subscription dependencies, or an unbounded remote inference service.
8. Do not claim the task is complete until the app builds and the relevant tests pass. Report blockers honestly.

### Implementation order

#### 1. Audio-based mood analysis
- Build on the current library/database architecture.
- Analyze decoded audio content, not file names, folder names, or ID3 mood tags.
- Use on-device PCM/audio signal analysis and/or a genuinely free, redistributable on-device model. Inspect licenses before adding a model or dependency.
- Use useful acoustic features such as RMS/loudness and dynamics, spectral centroid, spectral flux, zero-crossing rate, harmonic/percussive balance, and tempo/rhythm estimates where technically reliable.
- Support the app's Persian music use case. Do not assume western genre conventions are universally valid.
- Support the target mood families: happy, sad, calm, rainy, nostalgic, energetic/sports, romantic. Avoid false precision: some labels (especially rainy and nostalgic) are subjective and may need user confirmation or low-confidence review.
- Return a confidence score or uncertainty state. Do not silently force a low-confidence track into a definitive category.
- Persist user corrections using a stable track identifier/URI and timestamp. Make manual correction available from the track and category UI.
- Cache analysis results and re-analyze only new or changed files.
- Do not claim that correction records automatically retrain a model unless a real training/update mechanism is implemented and tested.

#### 2. Full player and background playback
- Inspect the existing playback implementation first.
- Complete queue management, reorder/remove/add, shuffle, repeat-one/repeat-all, previous/next, and restore state.
- Use the Android Media3/MediaSession approach compatible with this project's Gradle and Android versions, if suitable; do not blindly add latest dependencies.
- Implement a correctly declared media playback foreground service and notification/media controls, including lock-screen controls where supported.
- Handle audio focus, noisy-output events (e.g. unplugged headphones), process recreation, and notification permission/runtime differences.
- Keep playback alive when the Activity is backgrounded. Do not keep an Activity alive merely to keep music playing.

#### 3. Modern UI
- Preserve or carefully extend the current UI framework.
- Provide Persian RTL as the default and an English option in settings.
- Add clear home/category views, track rows, player controls, artwork/cover placeholders, tasteful animations, and a per-mood color theme.
- Keep animations light and accessible; avoid unnecessary recompositions or heavy image work.

#### 4. Performance and battery
- Move decoding, scanning, copy and ZIP work off the main thread.
- Use bounded concurrency appropriate for mid-range devices such as Redmi Note 10S (6 GB RAM / Helio G95).
- Use incremental scanning, stable IDs, cache, cancellation, and visible progress.
- Avoid repeatedly decoding unchanged tracks.
- Ensure large libraries do not load every PCM sample or artwork bitmap into memory at once.

#### 5. Library management
- Add fast search for available track/artist/title fields.
- Add playlist create/rename/delete and track add/remove.
- Add safe category management and manual category changes.
- Add database migrations that preserve existing user data. Never destructively reset the database to simplify a migration.

#### 6. Per-category export and sharing
- Integrate `android/CategoryExportHelper.kt` and `android/CategoryZipExporter.kt` from the supplied implementation kit, adapting them to actual app models and package paths.
- For each category independently, provide: copy actual audio bytes into a user-selected SAF folder (including USB OTG where supported), share selected audio URIs with Android Sharesheet, and export a ZIP using `ACTION_CREATE_DOCUMENT`.
- Keep operations on an IO dispatcher/background worker. Show progress and success/failure counts. Handle duplicate filenames, stale URIs, permission denial, target removal and low storage.
- Do not use broad legacy storage permissions when SAF can meet the requirement.

#### 7. App icon and release polish
- Use `assets/moodmusic-icon-1024.png` as the artwork source and generate proper adaptive foreground/background resources.
- Update manifest icon references based on the project's actual structure.
- Check app name, RTL, accessibility labels, and launch behavior.

### Testing and definition of done

- Add unit tests for feature scoring/uncertainty, queue behavior, filename collision handling, and ZIP entry creation where possible.
- Add tests for database migration and manual correction persistence.
- Build the debug APK using the repository's Gradle wrapper and existing Java/Android toolchain.
- Run existing tests, lint, and CI-compatible checks. Fix failures introduced by this work.
- Do not require signing secrets to build a debug APK.
- Add or repair a reproducible GitHub Actions workflow that builds the debug APK and uploads it as an artifact. Avoid breaking existing working workflows; identify and disable/remove only clearly obsolete failing workflows after confirming their role.
- Verify the manifest's permissions match actual features. Do not add INTERNET unless a clearly justified, optional feature requires it; the primary scan and playback must remain offline.
- Provide a concise summary of modified files, dependencies, database migrations, known limitations, tests run, build result, and APK artifact location.
- Open a Pull Request from `feature/moodmusic-v2` to `main`. Do not merge it automatically.

### Required supplied files

The implementation kit includes:
- `android/CategoryExportHelper.kt`
- `android/CategoryZipExporter.kt`
- `android/MoodCorrectionRecord.kt`
- `assets/moodmusic-icon-1024.png`
- `docs/V2-SCOPE-FA.md`

Treat these as starter resources, not proof of integration. Adapt them to the actual app and compile/test the result.

---

## آموزش اعمال بسته و ساخت APK (`docs/APPLY-AND-BUILD-FA.md`)

## آموزش اعمال بسته و گرفتن APK نسخه ۲

### مسیر پیشنهادی: با کامپیوتر و Codex

این روش برای اعمال تغییرات روی پروژه واقعی مناسب‌تر است.

#### مرحله ۱: آماده‌کردن مخزن

۱. در مرورگر وارد `https://github.com/dani8686/moodmusic` شو.
۲. از دکمه **Code** گزینه **Download ZIP** را بزن.
۳. ZIP مخزن را روی کامپیوتر استخراج کن.
۴. داخل مخزن، فایل `mood-source (1).zip` را هم استخراج کن و محل پروژه واقعی Android را پیدا کن. اگر Codex به مخزن متصل است، می‌تواند همین کار را با ترمینال انجام دهد.

#### مرحله ۲: اجرای Codex

۱. Codex را روی کامپیوتر باز کن و مخزن `dani8686/moodmusic` را متصل کن.
۲. مطمئن شو شاخه `main` دست‌نخورده می‌ماند؛ Codex باید شاخه `feature/moodmusic-v2` بسازد.
۳. فایل `docs/CODEX-PROMPT-EN.md` را از همین بسته باز کن.
۴. تمام متن آن را کپی و در Codex ارسال کن.
۵. وقتی Codex پروژه را بررسی کرد، از او بخواه ابتدا تغییرات را به‌صورت مرحله‌ای انجام دهد، اما همه هفت قابلیت را در همان شاخه پیاده کند.
۶. قبل از پذیرش Pull Request، گزارش تست‌ها و نتیجه ساخت APK را بررسی کن.

#### مرحله ۳: فایل‌های کمکی همین بسته

اگر Codex به ZIP این بسته دسترسی دارد، مسیرهای `android/` و `assets/` را به او معرفی کن. او باید فایل‌ها را با مدل واقعی برنامه تطبیق دهد. فایل‌ها را کورکورانه روی مسیرهای پروژه کپی نکن؛ ممکن است مدل آهنگ یا ساختار دیتابیس پروژه متفاوت باشد.

#### مرحله ۴: ساخت APK در Android Studio

۱. پروژه Android استخراج‌شده را در Android Studio باز کن.
۲. اجازه بده Gradle Sync کامل شود.
۳. از منوی **Build** گزینه **Make Project** را اجرا کن.
۴. در ترمینال ریشه پروژه دستور زیر را اجرا کن:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

در Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

۵. اگر ساخت موفق شد، APK معمولاً در این مسیر ساخته می‌شود:

```text
app/build/outputs/apk/debug/app-debug.apk
```

مسیر واقعی را از خروجی Gradle بررسی کن؛ ممکن است نام ماژول متفاوت باشد.

#### مرحله ۵: نصب و تست روی Redmi Note 10S

۱. APK را به گوشی منتقل کن.
۲. اجازه نصب از منبعی که فایل را باز می‌کند فعال کن.
۳. قبل از جایگزین‌کردن نسخه قبلی، از کتابخانه و اطلاعات مهم نسخه فعلی پشتیبان بگیر.
۴. این موارد را جداگانه تست کن:
   - اسکن چند آهنگ فارسی از سبک‌های متفاوت و اصلاح دستی دسته‌ها.
   - پخش، صف، Shuffle و Repeat.
   - رفتن به صفحه اصلی و قفل‌کردن گوشی در زمان پخش.
   - کنترل پخش از اعلان و صفحه قفل.
   - جست‌وجو و ساخت پلی‌لیست.
   - خروجی یک دسته به ZIP و بازکردن ZIP در برنامه مدیریت فایل.
   - کپی یک دسته کوچک به فلش OTG و بازکردن چند فایل از فلش.
   - قطع دسترسی یا جداکردن فلش هنگام عملیات و بررسی رفتار خطا.

### اگر فقط گوشی اندرویدی داری

می‌توانی مخزن را از مرورگر باز کنی و در صورت فعال‌بودن GitHub Codespaces برای حسابت، یک محیط ابری بسازی؛ اما ویرایش و ساخت پروژه Android از مرورگر موبایل سخت‌تر و کندتر است. مسیر Codex دسکتاپ یا یک کامپیوتر برای این پروژه مطمئن‌تر است. اتصال معمولی GitHub در ChatGPT برای این کار امکان نوشتن مستقیم روی مخزن را فراهم نکرد.

### خطاهای مهم

- **Gradle Sync failed:** نسخه Java، Gradle و Android Gradle Plugin را از پیام خطا بررسی کن؛ نسخه‌ها را تصادفی ارتقا نده.
- **Manifest merge failed:** سرویس پخش و مجوزهای تکراری را با Manifest اصلی تطبیق بده.
- **OTG write failed:** دسترسی SAF، پشتیبانی فایل‌سیستم فلش و فضای خالی را بررسی کن.
- **پخش بعد از قفل قطع می‌شود:** سرویس MediaSession/Foreground Service، مجوز اعلان و مدیریت باتری را بررسی کن.
- **دسته‌بندی اشتباه است:** نتیجه کم‌اطمینان باید قابل اصلاح باشد؛ با چند آهنگ نمی‌توان دقت مدل را اثبات کرد.

**نکته:** تا وقتی ساخت موفق و تست‌های بالا انجام نشده‌اند، هیچ APK را نسخه نهایی تلقی نکن.

---

## امنیت مخزن و امضای APK (`docs/SECURITY-FA.md`)

## امنیت مخزن عمومی و امضای APK

در بررسی قبلی پروژه گزارش شده بود که فایل `signing.p12` و رمز امضا داخل سورس/تنظیمات Gradle وجود داشته است. اگر این فایل واقعی است، یک کلید خصوصی محسوب می‌شود.

۱. کلید خصوصی و رمز آن را در مخزن عمومی commit نکن.
۲. فایل keystore را از نسخه فعلی سورس و مسیرهای قابل انتشار حذف کن، اما قبل از حذف مطمئن شو نسخه پشتیبان امن و خصوصی داری.
۳. رمزها را از `build.gradle.kts` خارج کن. از GitHub Actions Secrets یا متغیر محیطی امن استفاده کن.
۴. اگر کلید واقعاً عمومی شده، با درنظرگرفتن امکان به‌روزرسانی نصب‌های قبلی، برنامه چرخش/تعویض کلید امضا را بررسی کن. برای اپ منتشرشده، تعویض کلید ممکن است روی قابلیت به‌روزرسانی نسخه‌های نصب‌شده اثر بگذارد.
۵. برای تست روزمره، `assembleDebug` کافی است و به کلید امضای انتشار نیاز ندارد.
۶. فایل‌ها یا رمزهای لو رفته را فقط از آخرین commit حذف نکن؛ در صورت واقعی‌بودن افشا، سابقه Git هم باید با برنامه امن پاک‌سازی شود و اعتبارنامه‌ها چرخانده شوند.

این سند جایگزین بررسی امنیتی تاریخچه Git و وضعیت انتشار برنامه نیست.

---

## کپی دسته به پوشه/فلش OTG و اشتراک‌گذاری (`android/CategoryExportHelper.kt`)

```kotlin
package com.daniel.mood

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/** Copies the actual audio bytes into a user-selected SAF folder (including USB OTG). */
class CategoryExportHelper(private val context: Context) {

    fun copyCategoryToFolder(
        treeUri: Uri,
        categoryName: String,
        audioUris: List<Uri>
    ): ExportResult {
        val resolver = context.contentResolver
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ExportResult(0, audioUris.size, listOf("پوشه مقصد قابل دسترسی نیست"))

        val folderName = safeName(categoryName).ifBlank { "MoodMusic" }
        val categoryDir = root.findFile(folderName)
            ?: root.createDirectory(folderName)
            ?: return ExportResult(0, audioUris.size, listOf("ساخت پوشه ناموفق بود"))

        var copied = 0
        val errors = mutableListOf<String>()

        audioUris.forEachIndexed { index, source ->
            try {
                val displayName = queryDisplayName(resolver, source)
                    ?: "track_${index + 1}.audio"
                val mimeType = resolver.getType(source) ?: "application/octet-stream"
                val destination = categoryDir.createFile(mimeType, uniqueName(categoryDir, displayName))
                    ?: throw IOException("ساخت فایل مقصد ناموفق بود")

                val input = resolver.openInputStream(source)
                    ?: throw IOException("خواندن فایل مبدأ ممکن نیست")
                val output = resolver.openOutputStream(destination.uri, "w")
                    ?: throw IOException("نوشتن روی مقصد ممکن نیست")

                input.use { sourceStream ->
                    output.use { destinationStream ->
                        sourceStream.copyTo(destinationStream)
                        destinationStream.flush()
                    }
                }
                copied++
            } catch (error: Exception) {
                errors += "${source.lastPathSegment ?: "track_${index + 1}"}: ${error.message ?: "خطای ناشناخته"}"
            }
        }

        return ExportResult(copied, audioUris.size - copied, errors)
    }

    fun shareCategory(audioUris: List<Uri>): Intent {
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(audioUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueName(folder: DocumentFile, requestedName: String): String {
        val clean = safeName(requestedName).ifBlank { "audio" }
        if (folder.findFile(clean) == null) return clean
        val dot = clean.lastIndexOf('.')
        val base = if (dot > 0) clean.substring(0, dot) else clean
        val extension = if (dot > 0) clean.substring(dot) else ""
        var number = 2
        while (folder.findFile("${base}_$number$extension") != null) number++
        return "${base}_$number$extension"
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()

    data class ExportResult(val copied: Int, val failed: Int, val errors: List<String>)
}
```

---

## ساخت ZIP از فایل‌های آهنگ (`android/CategoryZipExporter.kt`)

```kotlin
package com.daniel.mood

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a ZIP containing real audio-file bytes to a URI created with ACTION_CREATE_DOCUMENT. */
class CategoryZipExporter(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    data class AudioItem(val uri: Uri, val fallbackName: String)

    /**
     * Run this method on Dispatchers.IO or another background thread.
     * The target URI should come from ActivityResultContracts.CreateDocument("application/zip").
     */
    fun writeZip(targetUri: Uri, items: List<AudioItem>): ZipResult {
        var written = 0
        val errors = mutableListOf<String>()
        val output = resolver.openOutputStream(targetUri, "w")
            ?: throw IOException("ساخت فایل ZIP ممکن نشد")

        output.use { rawOutput ->
            ZipOutputStream(rawOutput).use { zip ->
                val usedNames = mutableSetOf<String>()
                items.forEachIndexed { index, item ->
                    try {
                        val requested = displayName(item.uri) ?: item.fallbackName.ifBlank { "track_${index + 1}.audio" }
                        val entryName = uniqueEntryName(safeName(requested), usedNames)
                        val input = resolver.openInputStream(item.uri)
                            ?: throw IOException("خواندن فایل مبدأ ناموفق بود")
                        input.use { stream ->
                            zip.putNextEntry(ZipEntry(entryName))
                            stream.copyTo(zip)
                            zip.closeEntry()
                        }
                        written++
                    } catch (error: Exception) {
                        errors += "${item.fallbackName}: ${error.message ?: "خطای ناشناخته"}"
                    }
                }
                zip.finish()
            }
        }
        return ZipResult(written, items.size - written, errors)
    }

    private fun displayName(uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "audio" }

    private fun uniqueEntryName(requested: String, used: MutableSet<String>): String {
        if (used.add(requested)) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var suffix = 2
        while (!used.add("${base}_$suffix$extension")) suffix++
        return "${base}_$suffix$extension"
    }

    data class ZipResult(val written: Int, val failed: Int, val errors: List<String>)
}
```

---

## مدل اصلاح دستی حال‌وهوا (`android/MoodCorrectionRecord.kt`)

```kotlin
package com.daniel.mood

/** Model for a user's correction. Persist this in the app's existing database. */
data class MoodCorrectionRecord(
    val audioUri: String,
    val predictedMoodId: String,
    val correctedMoodId: String,
    val modelConfidence: Float?,
    val correctedAtEpochMillis: Long
)
```

---

## آیکون برنامه (`assets/moodmusic-icon-1024.png`)

تصویر ۱۰۲۴×۱۰۲۴ پیکسلی PNG؛ به‌صورت فایل جدا ضمیمه شده است (`moodmusic-icon-1024.png`).
