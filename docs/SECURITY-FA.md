# راهنمای امن امضای نسخه انتشار

## وضعیت کلید قبلی

فایل ZIP نسخه اولیه در تاریخچه عمومی مخزن شامل `signing.p12` و رمز ثابت بوده است. این کلید باید افشاشده تلقی شود و دیگر استفاده نشود. تاریخچه مخزن بدون اجازه صریح بازنویسی نشده است.

## تنظیم امضای Production در GitHub

۱. روی کامپیوتر امن، یک Keystore تازه بسازید و در دو محل خصوصی و امن پشتیبان‌گیری کنید. از کلید قبلی استفاده نکنید.

نمونه با `keytool` (رمزها را در فایل پروژه ننویسید):

```bash
keytool -genkeypair -v -keystore mood-release.p12 -storetype PKCS12 \
  -alias moodmusic-v2 -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 mood-release.p12 > mood-release.base64
```

در macOS اگر `base64 -w0` ندارد، از `base64 mood-release.p12` و حذف خطوط جدید استفاده کنید.

۲. در مخزن GitHub به مسیر **Settings → Secrets and variables → Actions → New repository secret** بروید و این چهار Secret را بسازید:

| نام | مقدار |
|---|---|
| `MOOD_KEYSTORE_BASE64` | محتوای Base64 فایل Keystore، بدون خط جدید |
| `MOOD_KEYSTORE_PASSWORD` | رمز Keystore |
| `MOOD_KEY_ALIAS` | نام Alias، مثلاً `moodmusic-v2` |
| `MOOD_KEY_PASSWORD` | رمز کلید؛ در PKCS12 معمولاً همان رمز Keystore است |

۳. Workflow را از **Actions → Test and build Android APKs → Run workflow** اجرا کنید.

۴. فایل Base64 موقت و هر رونوشت عمومی Keystore را حذف کنید. خود Keystore و رمز را امن نگه دارید؛ بدون آن‌ها به‌روزرسانی‌های بعدی با همان امضا ممکن نیست.

## اگر Secrets تنظیم نشود

Workflow عمداً یک **APK آزمایشی Release با امضای Debug** می‌سازد تا نصب و آزمون ممکن باشد. این فایل معتبر و نصب‌شدنی است، اما برای انتشار Production یا زنجیره به‌روزرسانی دائمی مناسب نیست، چون کلید Debug هر Runner می‌تواند متفاوت باشد.

## نصب روی نسخه قبلی

تعویض کلید باعث می‌شود Android نسخه جدید را Update نسخه قبلی نپذیرد. ابتدا از اطلاعات مهم پشتیبان بگیرید، نسخه قبلی را Uninstall کنید و سپس APK جدید را نصب کنید. حذف برنامه، دیتابیس داخلی آن را هم حذف می‌کند.
