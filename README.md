# সুর — Android Music Player

একটি Kotlin-ভিত্তিক Android মিউজিক প্লেয়ার। গান বাছতে **গান যোগ করুন** চাপুন; Android-এর ফাইল পিকার থেকে এক বা একাধিক অডিও ফাইল নির্বাচন করা যাবে।

## ফিচার

- অডিও ফাইল নির্বাচন করে প্লেলিস্ট তৈরি
- প্লে/পজ, আগের/পরের গান এবং seek control
- প্লেলিস্ট ও নির্বাচিত গান পরেরবার খোলার জন্য সংরক্ষণ
- Media3 playback service-এর মাধ্যমে ব্যাকগ্রাউন্ড প্লেব্যাক
- বাংলা ইন্টারফেস ও সময় প্রদর্শন

## চালানো

এই ফোল্ডারটি Android Studio-তে খুলুন। Gradle JDK হিসেবে `JAVA_HOME` (JDK 17) এবং Android SDK হিসেবে API 35 কনফিগার করা আছে। প্রথমবার Gradle sync করার সময় dependency ডাউনলোড হতে পারে।

PowerShell-এ প্রোজেক্ট ফোল্ডার থেকে debug APK তৈরি করুন:

```powershell
.\gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`। এটি Android 6.0 (API 23) বা নতুন সংস্করণের ডিভাইস/এমুলেটরে ইনস্টল করা যাবে।
