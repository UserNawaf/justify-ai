export type Lang = "ar" | "en";

const strings = {
  ar: {
    title: "مبرر واضح من أول مرة",
    subtitle: "اكتب مبرر طلبك بطريقتك، ونرتبه للمهندس بصيغة ثابتة. إذا نقص شي بنسألك بحد أقصى 3 أسئلة.",
    requestType: "نوع الطلب",
    yourText: "المبرر",
    placeholder: "مثال: نحتاج نفتح اتصال من سيرفر الرواتب للبنك على المنفذ 443 عشان صرف الرواتب الشهرية…",
    submit: "تحقق من المبرر",
    working: "جاري القراءة…",
    questionsTitle: "باقي كم معلومة",
    round: (r: number, m: number) => `الجولة ${r} من ${m}`,
    send: "إرسال الإجابات",
    complete: "المبرر جاهز",
    incomplete: "أُرسل المبرر مع نواقص",
    incompleteHint: "انتهت الجولات، والخانات الناقصة مكتوب فيها Not provided.",
    score: "درجة الجودة",
    copy: "نسخ",
    copied: "تم النسخ",
    again: "طلب جديد",
    errorModel: "الموديل غير متاح الحين، جرب بعد دقيقة.",
    error: "صار خطأ: ",
    switchLang: "English",
  },
  en: {
    title: "A clear justification, first time",
    subtitle: "Write your justification your own way; we turn it into a fixed format for the engineer. If something is missing we ask at most 3 questions.",
    requestType: "Request type",
    yourText: "Justification",
    placeholder: "e.g. We need the payroll server to reach the bank on port 443 to pay monthly salaries…",
    submit: "Check justification",
    working: "Reading…",
    questionsTitle: "A few details are missing",
    round: (r: number, m: number) => `Round ${r} of ${m}`,
    send: "Send answers",
    complete: "Justification ready",
    incomplete: "Sent with gaps",
    incompleteHint: "Rounds ran out; missing fields show “Not provided”.",
    score: "Quality score",
    copy: "Copy",
    copied: "Copied",
    again: "New request",
    errorModel: "The model is not available right now, try again in a minute.",
    error: "Error: ",
    switchLang: "العربية",
  },
};

export function t(lang: Lang) {
  return strings[lang];
}
