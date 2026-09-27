package moe.shizuku.manager.management

import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.databinding.AppsActivityBinding
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku
import java.util.*

class ApplicationManagementActivity : AppBarActivity() {

    private val viewModel: AppsViewModel by viewModels()
    private val adapter = AppsAdapter()

    private var actionMode: ActionMode? = null

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isDead() && !isFinishing)
            finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShizukuStateMachine.isRunning()) {
            finish()
            return
        }

        val binding = AppsActivityBinding.inflate(layoutInflater, rootView, true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.packages.observe(this) {
            when (it.status) {
                Status.SUCCESS -> {
                    adapter.updateData(it.data)
                }
                Status.ERROR -> {
                    finish()
                    val tr = it.error
                    Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                    tr.printStackTrace()
                }
                Status.LOADING -> {

                }
            }
        }
        viewModel.load()

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(top = 8f, bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                viewModel.load(true)
            }
        })

        adapter.setSelectionListener { count -> onSelectionCountChanged(count) }

        binding.searchInput.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString() ?: "")
        }

        ShizukuStateMachine.addListener(stateListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.apps_management, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val sortOrder = when (item.itemId) {
            R.id.action_sort_last_added -> SortOrder.LAST_ADDED
            R.id.action_sort_alphabetical -> SortOrder.ALPHABETICAL
            else -> return super.onOptionsItemSelected(item)
        }
        item.isChecked = true
        viewModel.setSortOrder(sortOrder)
        return true
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    private fun onSelectionCountChanged(count: Int) {
        if (count == 0) {
            actionMode?.finish()
        } else if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
        } else {
            actionMode?.invalidate()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.apps_management_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.batch_selected_count, adapter.selectedCount)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_select_all -> {
                    adapter.selectAll()
                    mode.invalidate()
                    true
                }

                R.id.action_grant -> {
                    confirmBatch(grant = true)
                    true
                }

                R.id.action_revoke -> {
                    confirmBatch(grant = false)
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            adapter.clearSelection()
        }
    }

    private fun confirmBatch(grant: Boolean) {
        val packages = adapter.selectedPackages
        if (packages.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(
                if (grant) R.string.app_management_batch_grant_title
                else R.string.app_management_batch_revoke_title
            )
            .setMessage(getString(R.string.app_management_batch_message, packages.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                for (pi in packages) {
                    try {
                        if (grant) {
                            AuthorizationManager.grant(pi.packageName, pi.applicationInfo!!.uid)
                        } else {
                            AuthorizationManager.revoke(pi.packageName, pi.applicationInfo!!.uid)
                        }
                    } catch (_: Exception) {
                    }
                }
                actionMode?.finish()
                adapter.notifyDataSetChanged()
                viewModel.load(true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }
}
