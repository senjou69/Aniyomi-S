package eu.kanade.tachiyomi.ui.browse.animeextension

import android.view.View
import androidx.core.view.isVisible
import coil.clear
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ExtensionItemBinding
import eu.kanade.tachiyomi.extension.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.LocaleHelper

class AnimeExtensionHolder(view: View, val adapter: AnimeExtensionAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = ExtensionItemBinding.bind(view)

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: AnimeExtensionItem) {
        val extension = item.extension

        binding.name.text = extension.name
        binding.version.text = extension.versionName
        binding.lang.text = LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
        binding.warning.text = when {
            extension is AnimeExtension.Untrusted -> itemView.context.getString(R.string.ext_untrusted)
            extension is AnimeExtension.Installed && extension.isUnofficial -> itemView.context.getString(R.string.ext_unofficial)
            extension is AnimeExtension.Installed && extension.isObsolete -> itemView.context.getString(R.string.ext_obsolete)
            else -> ""
        }.uppercase()

        binding.icon.clear()
        if (extension is AnimeExtension.Available) {
            binding.icon.load(extension.iconUrl)
        } else {
            extension.getApplicationIcon(itemView.context)?.let { binding.icon.setImageDrawable(it) }
        }
        bindButtons(item)
    }

    @Suppress("ResourceType")
    fun bindButtons(item: AnimeExtensionItem) = with(binding.extButton) {
        val extension = item.extension

        val installStep = item.installStep
        setText(
            when (installStep) {
                InstallStep.Pending -> R.string.ext_pending
                InstallStep.Downloading -> R.string.ext_downloading
                InstallStep.Installing -> R.string.ext_installing
                InstallStep.Installed -> R.string.ext_installed
                InstallStep.Error -> R.string.action_retry
                InstallStep.Idle -> {
                    when (extension) {
                        is AnimeExtension.Installed -> {
                            if (extension.hasUpdate) {
                                R.string.ext_update
                            } else {
                                R.string.action_settings
                            }
                        }
                        is AnimeExtension.Untrusted -> R.string.ext_trust
                        is AnimeExtension.Available -> R.string.ext_install
                    }
                }
            }
        )

        val isIdle = installStep == InstallStep.Idle || installStep == InstallStep.Error
        binding.cancelButton.isVisible = !isIdle
        isEnabled = isIdle
        isClickable = isIdle
    }
}
