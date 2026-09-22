package com.example.budge.data.repository

import java.util.Locale

/**
 * The categories the app seeds on first launch, named in each language it speaks.
 *
 * [SLOTS] defines the set once: icon, colour, type and sort order. [NAMES] holds only
 * the display names, in the same order, so there is one place where the two can fall
 * out of step instead of nine. A name list shorter than [SLOTS] would fail loudly on
 * the indexed lookup below, which is the failure mode we want.
 */
internal data class DefaultCategory(
    val name: String,
    val icon: String,
    val color: Long,
    val type: Int,
    val sortOrder: Int,
)

/** Language-independent definition of one built-in category. */
private data class Slot(
    val icon: String,
    val color: Long,
    val type: Int,
    val sortOrder: Int,
)

// Eight expense slots, then four income slots. Every list in [NAMES] is in this order.
private val SLOTS =
    listOf(
        Slot("restaurant", 0xFFD32F2F, 0, 1),
        Slot("directions_car", 0xFFC2185B, 0, 2),
        Slot("shopping_bag", 0xFF7B1FA2, 0, 3),
        Slot("movie", 0xFF303F9F, 0, 4),
        Slot("favorite", 0xFF1976D2, 0, 5),
        Slot("school", 0xFF0097A7, 0, 6),
        Slot("home", 0xFF00796B, 0, 7),
        Slot("bolt", 0xFF388E3C, 0, 8),
        Slot("payments", 0xFFAFB42B, 1, 1),
        Slot("savings", 0xFFFBC02D, 1, 2),
        Slot("savings", 0xFFF57C00, 1, 3),
        Slot("attach_money", 0xFF5D4037, 1, 4),
    )

// Display names, keyed by ISO language code, in SLOTS order.
private val NAMES: Map<String, List<String>> =
    mapOf(
        "en" to
            listOf(
                "Dining", "Transport", "Shopping", "Entertainment",
                "Health", "Education", "Housing", "Utilities",
                "Salary", "Bonus", "Income", "Deposit",
            ),
        "zh" to
            listOf(
                "餐饮", "交通", "购物", "娱乐",
                "医疗", "教育", "住房", "日用",
                "工资", "奖金", "收入", "存款",
            ),
        "fr" to
            listOf(
                "Restauration", "Transport", "Achats", "Loisirs",
                "Santé", "Éducation", "Logement", "Charges",
                "Salaire", "Prime", "Revenus", "Dépôt",
            ),
        "de" to
            listOf(
                "Essen", "Transport", "Einkäufe", "Unterhaltung",
                "Gesundheit", "Bildung", "Wohnen", "Nebenkosten",
                "Gehalt", "Bonus", "Einkommen", "Einzahlung",
            ),
        "es" to
            listOf(
                "Comida", "Transporte", "Compras", "Ocio",
                "Salud", "Educación", "Vivienda", "Servicios",
                "Salario", "Bonificación", "Ingresos", "Depósito",
            ),
        "ru" to
            listOf(
                "Питание", "Транспорт", "Покупки", "Развлечения",
                "Здоровье", "Образование", "Жильё", "Коммунальные",
                "Зарплата", "Премия", "Доход", "Депозит",
            ),
        "ja" to
            listOf(
                "食費", "交通費", "買い物", "娯楽",
                "医療", "教育", "住居", "日用品",
                "給料", "ボーナス", "収入", "貯金",
            ),
        "it" to
            listOf(
                "Ristorazione", "Trasporti", "Acquisti", "Svago",
                "Salute", "Istruzione", "Casa", "Utenze",
                "Stipendio", "Bonus", "Entrate", "Deposito",
            ),
        "pt" to
            listOf(
                "Alimentação", "Transporte", "Compras", "Lazer",
                "Saúde", "Educação", "Moradia", "Contas",
                "Salário", "Bônus", "Renda", "Depósito",
            ),
    )

/** Language used when there is no table for the requested one. */
private const val FALLBACK_LANGUAGE = "en"

/**
 * The seeded categories for [locale].
 *
 * An unknown language gets the English names. Falling back beats returning an empty
 * list, because this runs on first launch and the user needs something to pick from.
 */
internal fun defaultCategoriesFor(locale: Locale): List<DefaultCategory> {
    val names = NAMES[locale.language.lowercase(Locale.ROOT)] ?: NAMES.getValue(FALLBACK_LANGUAGE)
    return SLOTS.mapIndexed { index, slot ->
        DefaultCategory(
            name = names[index],
            icon = slot.icon,
            color = slot.color,
            type = slot.type,
            sortOrder = slot.sortOrder,
        )
    }
}
