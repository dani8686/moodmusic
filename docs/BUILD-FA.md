# ساخت، تست و نصب (فارسی)

## ساخت خودکار (پیشنهادی)

Workflow `.github/workflows/build.yml` روی هر Push و Pull Request اجرا می\u200cشود و این مراحل را انجام می\u200cدهد:

1. JDK 17 + Android SDK 34 + build-tools 34.0.0 + Gradle 8.7
2. آماده\u200cسازی کلید امضا (Secret یا کلید تولیدشده در Actions cache)
3. `gradle --no-daemon :app:testDebugUnitTest`
4. `gradle --no-daemon :app:lintDebug`
5. `gradle --no-daemon :app:assembleDebug :app:assembleRelease`
6. بررسی APK با `aapt dump permissions` (باید `INTERNET` وجود نداشته باشد)، `aapt dump badging` و `apksigner verify`
7. آپلود artifact `MoodMusicSorter-v2-APK` شامل `MoodMusicSorter-v2.0.0-release.apk` و `app-debug.apk`

 artifact تست/lint هم با نام `test-and-lint-reports` آپلود می\u200cشود.

## ساخت دستی روی کامپیوتر

نیازها: JDK 17، Gradle 8.7، Android SDK platform 34 و build-tools 34.0.0، متغیر `ANDROID_HOME`.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

خروجی: `app/build/outputs/apk/release/app-release.apk`.
پروژه عمداً Gradle Wrapper باینری ندارد (محیط این مخزن اجازهٔ دانلود آن را نداد)؛ Android Studio هنگام باز کردن پروژه می\u200cتواند Wrapper بسازد.

## نصب روی Redmi Note 10S (Android 13)

1. فایل `MoodMusicSorter-v2.0.0-release.apk` را از artifact یا Release دانلود و به گوشی منتقل کنید.
2. از «تنظیمات → حریم خصوصی → نصب برنامه\u200cهای ناشناخته» به فایل\u200cمنیجر/مرورگر اجازهٔ نصب بدهید.
3. روی APK بزنید و نصب را تأیید کنید.
4. هنگام اجرا، مجوز «فایل\u200cهای صوتی» (`READ_MEDIA_AUDIO`) و «اعلان\u200cها» را بدهید. اسکن اولین بار به\u200cصورت خودکار شروع می\u200cشود.
5. در MIUI/HyperOS، برای تحلیل طولانی، مصرف باتری برنامه را «بدون محدودیت» کنید (دیالوگ راهنما داخل برنامه).

### آیا روی نسخهٔ قبلی نصب می\u200cشود؟

نسخهٔ ۱ هرگز APK امضاشدهٔ منتشرشده\u200cای نداشت، ولی **کلید قدیمی لو رفته بود**. نسخهٔ ۲ با کلید جدید امضا می\u200cشود؛ اگر نسخه\u200cای با کلید قدیمی نصب کرده\u200cاید، پیام «امضای متفاوت» می\u200cگیرید و باید ابتدا آن را حذف کنید. قبل از حذف، از دسته\u200cها خروجی ZIP بگیرید. وقتی Secrets امضا را تنظیم کنید، نسخه\u200cهای بعدی همان کلید را دارند و به\u200cصورت به\u200cروزرسانی نصب می\u200cشوند.

## تست\u200cهایی که واقعاً اجرا می\u200cشوند

- تست\u200cهای واحد JVM: مدل امتیازدهی، عدم وابستگی به نام فایل، آستانهٔ اطمینان، یادگیری از اصلاح\u200cها، نام\u200cگذاری امن، جلوگیری از نام تکراری در ZIP/پوشه، escape جست\u200cوجو، منطق صف و تکرار.
- ساخت Debug/Release، lint، اعتبارسنجی مانیفست/منابع/امضا.

## تست\u200cهایی که نیاز به دستگاه دارند (انجام نشده)

نصب واقعی، مجوزها، اسکن کتابخانهٔ واقعی فارسی، پخش پس\u200cزمینه و اعلان/صفحهٔ قفل، کنترل هدفون/بلوتوث، کپی به فلش OTG واقعی، ZIP و Sharesheet، تست ۱۲۰۰+ آهنگ و مصرف رم/باتری. این موارد باید روی Redmi Note 10S توسط کاربر بررسی شوند.
