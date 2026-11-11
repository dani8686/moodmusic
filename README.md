# Mood Music Sorter 3.0 (V3 development)

اپلیکیشن اندروید `com.daniel.mood` با دسته‌بندی آفلاین صوت و قابلیت‌های V3: Music DNA، Radar، Mood Journey، Smart DJ، Time Machine، Music Twin فارسی/انگلیسی و Providerهای اختیاری آنلاین.

- Android 8.0 (API 26)، target/compile 34، Kotlin/Compose/Media3/Room
- حالت‌های Offline، Hybrid (پیشنهادی) و Online AI
- حفظ کتابخانه، اصلاحات دستی، پلی‌لیست‌ها و تنظیمات V2 با Migration افزایشی ۲→۳
- ارسال صدا فقط با رضایت صریح، سیاست Wi-Fi/حجم/سهمیه، کش و Fallback محلی
- هیچ کلید مخفی یا هزینه سرویس میزبانی‌شده در APK وجود ندارد

**وضعیت کلی: PARTIAL** — Build و تست خودکار COMPLETE است؛ تست دستگاه فیزیکی Redmi/MIUI، Crossfade شنیداری، OTG/Bluetooth و Providerهای واقعی هنوز PARTIAL و امضای Production/Backend میزبان BLOCKED است.

## APK آزمایشی تأییدشده V3

- [دانلود MoodMusicSorter-3.0-test.apk](https://github.com/dani8686/moodmusic/releases/download/v3.0.0-test/MoodMusicSorter-3.0-test.apk)
- CI موفق: https://github.com/dani8686/moodmusic/actions/runs/43035039717
- ۲۰ تست PASS، Lint/Debug/Release و بررسی Package/امضا/مجوزها PASS
- حجم: `3,290,249` بایت
- SHA-256: `31623f7897322cbffb5014f9ed0c87e0de8bf4c7c19d00a73925c0672c771f8f`
- این APK با کلید Debug امضا شده و فقط TEST است؛ نصب روی دستگاه فیزیکی و Migration واقعی V2 کاربر هنوز باید آزمایش شود.

## ساخت و تست

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

بدون Secrets امضا، Release با کلید Debug ساخته می‌شود و فقط TEST است. امضای Production نیازمند `MOOD_KEYSTORE_BASE64`، `MOOD_KEYSTORE_PASSWORD`، `MOOD_KEY_ALIAS` و `MOOD_KEY_PASSWORD` در Actions Secrets است. کلید قدیمی عمومی افشاشده است و استفاده مجدد نمی‌شود.

## قابلیت‌های اصلی

1. **تحلیل محلی:** MediaCodec/FFT و ویژگی‌های RMS، طیف، Chroma، Onset و BPM؛ عنوان/نام فایل وارد دسته‌بند نمی‌شود. ده دسته و یادگیری centroid اصلاحات V2 حفظ شده است.
2. **Music DNA:** زمان واقعی شنیدن، تکمیل، Skip، Like/Dislike و ویژگی‌های صوتی؛ قابل بازنشانی مستقل.
3. **Radar:** پیشنهاد محلی/کاتالوگ آنلاین اختیاری با دلیل، Wishlist، کش و حذف آهنگ‌های اخیر/Dislike.
4. **Journey و Smart DJ:** سفر ذخیره‌شدنی بین Moodها و Crossfade واقعی دو Player با تنظیم BPM/انرژی/Familiarity.
5. **Time Machine:** رویدادهای V3، تقویم و ماه‌ها، آمار، On This Day، CSV و پلی‌لیست تاریخی.
6. **Music Twin:** Actionهای کنترل‌شده فارسی/انگلیسی، تفسیر آنلاین اختیاری و تأیید عمل حذفی؛ اجرای دلخواه کد/Action خارجی مجاز نیست.
7. **کتابخانه V2:** جستجو/مرتب‌سازی، اسکن افزایشی، پلی‌لیست/صف، MediaSession، کپی SAF و ZIP/Share واقعی.
8. **Online اختیاری:** Gemini برای صدا/گفتگو، iTunes برای کاتالوگ و LRCLIB برای Lyrics. Provider قابل تعویض است و در نبود اتصال تحلیل محلی باقی می‌ماند.

## حریم خصوصی و هزینه

Offline پیش‌فرض است. آپلود صوتی بدون رضایت انجام نمی‌شود. کلید شخصی پیشرفته با Android Keystore رمز می‌شود، اما برای انتشار عمومی Backend امن HTTPS لازم است و در این مخزن میزبانی نشده است. سهمیه/قیمت/منطقه Provider ممکن است تغییر کند و Free Tier دائمی تضمین نمی‌شود. جزئیات رسمی Privacy/Retention و محدودیت‌ها در [docs/V3.md](docs/V3.md) ثبت شده است.

## معماری

- `AudioAnalysis.kt` / `MoodEngine.kt`: تحلیل محلی و یادگیری اصلاحات
- `Database.kt` / `V3Database.kt`: Room 3، Migration و Intelligence DAO
- `MusicIntelligence.kt`: DNA، Radar، Journey و انتخاب DJ
- `OnlineProviders.kt`: Providerها، CredentialStore، Consent/Network policy و cache
- `PlaybackService.kt`: Media3، telemetry و dual-player crossfade
- `MusicTwin.kt`: Grammar و Actionهای کنترل‌شده
- `V3*Screens.kt`: رابط V3 فارسی RTL/انگلیسی
- `CategoryExport.kt` / `ExportService.kt`: خروجی واقعی V2

## محدودیت‌های صادقانه

Mood تشخیصی احتمالی است و اصلاح کاربر اولویت دارد. Beat quantization به معنی Beat-matching کامل DJ نیست. Build موفق جای تست Redmi، میزان باتری، ۱۲۰۰+ آهنگ یا صحت Provider را نمی‌گیرد. Apple quota/terms و عملیات Backend امن هنوز PARTIAL/BLOCKED هستند؛ جزئیات در سند V3.
