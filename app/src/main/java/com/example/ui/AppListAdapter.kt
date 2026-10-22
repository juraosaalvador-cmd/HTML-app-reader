package com.example.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.HtmlApp
import com.example.databinding.ItemAppBinding
import com.example.util.FileUtils

/**
 * Adapter para a lista vertical de aplicações instaladas na barra lateral.
 */
class AppListAdapter(
    private val onAppClick: (HtmlApp) -> Unit,
    private val onAppRename: (HtmlApp) -> Unit,
    private val onAppDetails: (HtmlApp) -> Unit,
    private val onAppDelete: (HtmlApp) -> Unit
) : ListAdapter<HtmlApp, AppListAdapter.AppViewHolder>(DiffCallback) {

    var selectedAppId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app, app.id == selectedAppId)
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: HtmlApp, isSelected: Boolean) {
            val context = binding.root.context

            binding.tvAppName.text = app.name

            // Letra inicial como ícone estilizado
            val initial = app.name.trim().take(1).uppercase()
            binding.tvIconLetter.text = if (initial.isNotEmpty()) initial else "H"

            // Subtítulo de última execução
            val relativeTime = FileUtils.formatRelativeTime(app.lastOpenedAt)
            binding.tvAppSubtitle.text = context.getString(R.string.desc_last_opened, relativeTime)

            // Contagem de aberturas
            binding.tvOpenCount.text = context.getString(R.string.desc_open_count, app.openCount)

            // Tipo de fonte
            binding.tvSourceType.text = app.sourceType

            // Indicador de app aberto atualmente
            if (isSelected) {
                binding.activeIndicator.visibility = View.VISIBLE
                binding.cardContainer.setBackgroundResource(R.drawable.bg_terminal_card_active)
            } else {
                binding.activeIndicator.visibility = View.INVISIBLE
                binding.cardContainer.setBackgroundResource(R.drawable.bg_terminal_card)
            }

            // Clique no item abre o app
            binding.root.setOnClickListener {
                onAppClick(app)
            }

            // Menu de contexto / overflow (···)
            binding.btnOptions.setOnClickListener { view ->
                showPopupMenu(view, app)
            }
        }

        private fun showPopupMenu(anchor: View, app: HtmlApp) {
            val context = anchor.context
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, 1, 0, context.getString(R.string.action_open))
            popup.menu.add(0, 2, 1, context.getString(R.string.action_rename))
            popup.menu.add(0, 3, 2, context.getString(R.string.action_folder))
            popup.menu.add(0, 4, 3, context.getString(R.string.action_delete))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onAppClick(app)
                        true
                    }
                    2 -> {
                        onAppRename(app)
                        true
                    }
                    3 -> {
                        onAppDetails(app)
                        true
                    }
                    4 -> {
                        onAppDelete(app)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<HtmlApp>() {
        override fun areItemsTheSame(oldItem: HtmlApp, newItem: HtmlApp): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HtmlApp, newItem: HtmlApp): Boolean {
            return oldItem == newItem
        }
    }
}
