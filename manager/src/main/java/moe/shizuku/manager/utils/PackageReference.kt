package moe.shizuku.manager.utils

/**
 * [value] with a leading [oldPackage] replaced by [newPackage], or null when this value has nothing
 * to do with the old package name.
 *
 * The name an app is called by is written into its manifest in more places than its `package`
 * attribute: intent filter actions, permission names and provider authorities all carry it, and
 * renaming only the package leaves a copy that advertises one name and answers to another. That is
 * issue #86: a hidden install whose `.START` action matched no filter at all, while the action that
 * did match was then rejected by a receiver comparing against the runtime package name.
 *
 * Only a value that *is* the package, or begins with it followed by a dot, is rewritten - so a URL
 * or a path that happens to contain those characters somewhere inside it is left alone.
 */
internal fun rewrittenPackageReference(
    value: String,
    oldPackage: String,
    newPackage: String
): String? = when {
    value == oldPackage -> newPackage
    !value.startsWith("$oldPackage.") -> null
    else -> newPackage + value.removePrefix(oldPackage)
}
