# AI Phone Assistant

أنشئ مشروعاً حقيقياً باسم AI Phone Agent، وهو مساعد ذكاء اصطناعي يعمل على Android ويستطيع فهم أوامر المستخدم باللغة الطبيعية وتنفيذها على الهاتف داخل التطبيقات التي يسمح المستخدم للتطبيق بالتحكم بها.

الفكرة الأساسية

لا أريد تطبيقاً خاصاً بـTikTok أو WhatsApp أو أي تطبيق واحد.

أريد AI Agent عاماً للهاتف يفهم ما أقوله له ويحوّل كلامي إلى خطوات عملية ثم ينفذها على الجهاز.

أمثلة:

«افتح WhatsApp وأرسل رسالة إلى أحمد تقول: سأصل بعد قليل.»

«افتح YouTube وابحث عن فيديو عن الفضاء وشغله.»

«افتح TikTok وشاهد الفيديوهات لمدة 10 دقائق وانتقل للفيديو التالي.»

«افتح لعبة معينة والعب بالطريقة التي أشرحها لك.»

«افتح الإعدادات وانتقل إلى القسم الذي أطلبه.»

«افتح تطبيقاً معيناً ونفذ الخطوات التي أحددها.»

يجب أن يكون النظام عاماً وقابلاً للتوسع وليس مجموعة أوامر ثابتة لتطبيقات محددة.

Android

استخدم:

Kotlin

Android SDK

AccessibilityService

Android Intents عندما تكون مناسبة

OCR / تحليل الشاشة عند الحاجة

Gestures مثل الضغط والسحب والتمرير

اكتشاف عناصر الواجهة Accessibility Nodes عندما تكون متاحة

يجب أن يحصل التطبيق على موافقة المستخدم قبل استخدام Accessibility.

طريقة عمل الـAI

المستخدم يقول أمراً باللغة الطبيعية.

مثلاً:

«افتح WhatsApp، ادخل إلى محادثة أحمد، اكتب سأصل بعد قليل، ثم أرسلها.»

يقوم AI أولاً بفهم الهدف وتحويله إلى خطة منظمة:

{
  "goal": "send_message",
  "app": "WhatsApp",
  "steps": [
    "open_app",
    "find_contact",
    "open_chat",
    "type_text",
    "send"
  ]
}


ثم يقوم Android Agent بتنفيذ الخطوات واحدة تلو الأخرى ومراقبة الشاشة بعد كل خطوة للتأكد من نجاحها.

إذا تغيرت واجهة التطبيق، لا يعتمد النظام فقط على إحداثيات ثابتة، بل يحاول فهم عناصر الشاشة واختيار الإجراء المناسب.

الرؤية وفهم الشاشة

أضف نظاماً يستطيع تحليل الشاشة عند الحاجة، بحيث يستطيع الـAI معرفة:

النصوص الموجودة على الشاشة

الأزرار

القوائم

الصور والعناصر المرئية

حالة التطبيق الحالية

ثم يقرر الخطوة التالية بناءً على الحالة الحالية.

الألعاب

اجعل النظام قابلاً للتعامل مع الألعاب أيضاً عندما تسمح صلاحيات Android بذلك، باستخدام اللمس والسحب والأزرار والإجراءات المتاحة.

لا تجعل النظام يعتمد على لعبة واحدة؛ يجب أن تكون بنية الـAgent عامة وقابلة للتوسع.

التحكم والأمان

أضف:

Start

Pause

Stop

Emergency Stop

يجب ألا ينفذ AI إجراءات خطرة أو حساسة بدون تأكيد المستخدم عند الحاجة.

لا تسمح للنظام بتجاوز صلاحيات Android أو تعطيل حماية الجهاز أو تنفيذ إجراءات مخفية.

واجهة التطبيق

أنشئ واجهة عربية حديثة جداً تحتوي على:

محادثة مع AI

زر ميكروفون للأوامر الصوتية

زر تنفيذ

حالة المهمة الحالية

الخطوة الحالية

سجل الإجراءات

التطبيقات المسموح بها

إعدادات Accessibility

زر إيقاف طارئ

البنية التقنية

افصل المشروع إلى:

AI Layer

فهم اللغة

التخطيط

تحويل الأمر إلى خطوات

Vision Layer

تحليل الشاشة

OCR

فهم عناصر الواجهة

Android Agent

AccessibilityService

Touch/Gesture actions

App launching

UI interaction

Task Engine

تنفيذ الخطوات

التحقق من نجاح كل خطوة

إعادة المحاولة عند الحاجة

التوقف عند حدوث خطأ

UI

واجهة المحادثة

الإعدادات

حالة التنفيذ

مهم جداً

لا تنشئ Demo وهمياً أو واجهة فقط.

أريد مشروعاً حقيقياً يمكن تحويله إلى APK Android حقيقي.

لا تجعل النظام خاصاً بـTikTok أو WhatsApp؛ يجب أن يكون AI Phone Agent عاماً يستطيع فهم أوامر مختلفة وتنفيذها في التطبيقات التي يسمح المستخدم بالوصول إليها.

لا تعتمد على إحداثيات ثابتة فقط.

إذا كان Lovable لا يستطيع إنشاء جزء Android الأصلي مباشرة، فأنشئ كل ما تستطيع داخل المشروع، واعزل الأجزاء التي تحتاج Android Studio، وأنشئ ملفات Kotlin ونقاط التكامل المطلوبة بوضوح.

حافظ على المشروع منظماً وقابلاً للتوسع ولا تقم بتخريب أو حذف أجزاء غير مرتبطة بالمطلوب.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/ca6cc44a-95fd-4f52-891b-ca8260e89daa).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
