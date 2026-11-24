package com.finora.app.finance

/** The web app's CATEGORY_KEYWORDS (note text -> suggested category key). */
object CategoryKeywords {
    val map: LinkedHashMap<String, List<String>> = linkedMapOf(
        "food" to listOf("غذا", "رستوران", "کافه", "فست فود", "پيتزا", "پیتزا", "ساندویچ", "بستنی", "آب میوه", "نوشیدنی", "کافی شاپ", "سوشی", "کباب", "دیجیکالا food", "اسنپ فود", "snapp food", "snappfood", "zoodfoood", "zood food"),
        "transport" to listOf("اسنپ", "snapp", "تاکسی", "مترو", "اتوبوس", "بلیط", "پارکینگ", "بنزین", "سوخت", "جریمه", "uber", "lyft", "کرایه", "ایکس"),
        "health" to listOf("دارو", "داروخانه", "دکتر", "پزشک", "بیمارستان", "کلینیک", "دندانپزشک", "آزمایش", "ویزیت", "pharmacy", "hospital"),
        "entertainment" to listOf("سینما", "کنسرت", "تئاتر", "بازی", "گیم", "steam", "playstation", "netflix", "spotify", "یوتیوب", "اپل موزیک", "دیجی‌فای", "filimo", "فیلیمو", "aparat"),
        "education" to listOf("کتاب", "کتابخانه", "آموزش", "دوره", "مدرسه", "دانشگاه", "زبان", "مکالمه", "تدریس", "ielts", "toefl", "coursera", "udemy"),
        "shopping" to listOf("دیجیکالا", "دیجی کالا", "digikala", "ترب", "divar", "بازار", "مال", "خرید", "پوشاک", "لباس", "کفش", "موبایل", "لپ‌تاپ", "amazon", "zara", "شاپ", "shop", "store"),
        "home" to listOf("خانه", "اجاره", "قبض", "برق", "گاز", "آب", "تلفن", "اینترنت", "مبل", "لوازم", "تعمیر", "نظافت", "سرایدار"),
        "personal" to listOf("آرایشگاه", "سلمانی", "آرایشگر", "باشگاه", "ورزش", "gym", "ناخن", "میکاپ", "عطر", "لوازم آرایشی"),
        "income" to listOf("حقوق", "پورسانت", "فریلنس", "پروژه", "سود", "اجاره دریافتی", "کارمزد"),
    )
}
