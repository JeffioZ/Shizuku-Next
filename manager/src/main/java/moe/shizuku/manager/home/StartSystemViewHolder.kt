package moe.shizuku.manager.home

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeStartSystemBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.starter.StarterActivity
import rikka.core.util.ClipboardUtils
import rikka.html.text.HtmlCompat
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class StartSystemViewHolder(private val binding: HomeStartSystemBinding, root: View) :
    BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeStartSystemBinding.inflate(inflater, outer.root, true)
            StartSystemViewHolder(inner, outer.root)
        }

        /**
         * Command that resolves and launches the Shizuku server executable. It is
         * meant to be run by the user's own privilege escalation, since Shizuku
         * itself cannot start under another UID.
         */
        val CUSTOM_COMMAND = "STARTER=\$(pm path moe.shizuku.privileged.api | sed -E 's|^package:(.*/)[^/]+\\.apk\$|\\1lib/arm64/libshizuku.so|') && \$STARTER"
    }

    private inline val start get() = binding.button1

    init {
        start.setOnClickListener { onStartClicked(it) }
        binding.text1.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun onStartClicked(v: View) {
        val context = v.context
        if (ShizukuSettings.getSystemStartMethod() == ShizukuSettings.SYSTEM_START_EXPLOIT) {
            context.startActivity(
                Intent(context, StarterActivity::class.java).apply {
                    putExtra(StarterActivity.EXTRA_IS_SYSTEM, true)
                }
            )
        } else {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_system_custom_title)
                .setMessage(context.getString(R.string.home_system_custom_message, CUSTOM_COMMAND))
                .setPositiveButton(R.string.home_system_copy_command) { _, _ ->
                    if (ClipboardUtils.put(context, CUSTOM_COMMAND)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_copied_to_clipboard),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onBind() {
        start.isEnabled = true

        val exploit = ShizukuSettings.getSystemStartMethod() == ShizukuSettings.SYSTEM_START_EXPLOIT
        binding.text1.text = context.getString(
            if (exploit) R.string.home_system_description_exploit else R.string.home_system_description_custom
        ).toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
    }
}
