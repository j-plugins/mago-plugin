package com.github.xepozz.mago.intentions.apply

import com.github.xepozz.mago.MagoBundle

enum class ApplyAllScope(val maxSafetyLevel: Int, private val labelKey: String) {
    SAFE_ONLY(maxSafetyLevel = 0, labelKey = "apply.scope.safeOnly"),
    POTENTIALLY_UNSAFE(maxSafetyLevel = 1, labelKey = "apply.scope.potentiallyUnsafe"),
    UNSAFE(maxSafetyLevel = 2, labelKey = "apply.scope.unsafe");

    val label: String get() = MagoBundle.message(labelKey)
}
