package com.wa2c.android.cifsdocumentsprovider.presentation.ui.edit

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.button.MaterialButton
import com.wa2c.android.cifsdocumentsprovider.R
import com.wa2c.android.cifsdocumentsprovider.common.utils.pathFragment
import com.wa2c.android.cifsdocumentsprovider.databinding.FragmentEditBinding
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.common.navigateBack
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.common.navigateSafe
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.common.toast
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.common.viewBinding
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.dialog.MessageDialogDirections
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.dialog.setDialogResult
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.folder.FolderFragment
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.host.HostFragment
import dagger.hilt.android.AndroidEntryPoint


/**
 * Edit Screen
 */
@AndroidEntryPoint
class EditFragment : Fragment(R.layout.fragment_edit) {

    /** View Model */
    private val viewModel by viewModels<EditViewModel>()
    /** Binding */
    private val binding: FragmentEditBinding? by viewBinding()
    /** Arguments */
    private val args: EditFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)
        (activity as? AppCompatActivity)?.supportActionBar?.let {
            it.setIcon(null)
            it.setTitle(R.string.edit_title)
            it.setDisplayShowHomeEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(true)
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.onClickBack()
            }
        })

        binding.let {
            it?.viewModel = viewModel
        }

        viewModel.let {
            it.navigationEvent.observe(viewLifecycleOwner, ::onNavigate)
            it.initialize(args.cifsConnection)
        }
    }

    override fun onStop() {
        super.onStop()
        // Close keyboard
        view?.let {
            (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (!viewModel.isNew) {
            inflater.inflate(R.menu.menu_edit, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                viewModel.onClickBack()
                return true
            }
            R.id.edit_menu_delete -> {
                navigateSafe(
                    MessageDialogDirections.actionGlobalMessageDialog(
                        message = getString(R.string.edit_delete_confirmation_message),
                        positiveText = getString(android.R.string.ok),
                        neutralText = getString(android.R.string.cancel)
                    )
                )
                setDialogResult { result ->
                    if (result == DialogInterface.BUTTON_POSITIVE) {
                        viewModel.onClickDelete()
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onNavigate(event: EditNav) {
        when (event) {
            is EditNav.Back -> {
                if (event.changed) {
                    setDialogResult { result ->
                        if (result == DialogInterface.BUTTON_POSITIVE) {
                            findNavController().popBackStack(R.id.editFragment, true)
                        }
                    }
                    navigateSafe(
                        MessageDialogDirections.actionGlobalMessageDialog(
                            message = getString(R.string.edit_back_confirmation_message),
                            positiveText = getString(android.R.string.ok),
                            neutralText = getString(android.R.string.cancel)
                        )
                    )
                } else {
                    navigateBack(R.id.editFragment, true)
                }
            }
            is EditNav.SelectHost -> {
                // Select host
                setFragmentResultListener(HostFragment.REQUEST_KEY_HOST) { _, bundle ->
                    bundle.getString(HostFragment.RESULT_KEY_HOST_TEXT)?.let { hostText ->
                        viewModel.setHostResult(hostText)
                    }
                }
                navigateSafe(EditFragmentDirections.actionEditFragmentToHostFragment(event.connection))
            }
            is EditNav.SelectFolder -> {
                // Select folder
                setFragmentResultListener(FolderFragment.REQUEST_KEY_FOLDER) { _, bundle ->
                    bundle.getParcelable<Uri>(FolderFragment.RESULT_KEY_FOLDER_URI)?.let { uri ->
                        viewModel.setFolderResult(uri.pathFragment)
                    }
                }
                navigateSafe(EditFragmentDirections.actionEditFragmentToFolderFragment(event.connection))
            }
            is EditNav.CheckConnectionResult -> {
                // Connection check
                val message =
                    if (event.result) getString(R.string.edit_check_connection_ok_message)
                    else event.throwable?.message ?: getString(R.string.edit_check_connection_ng_message)
                toast(message)
            }
            is EditNav.SaveResult -> {
                if (event.messageId == null) {
                    findNavController().popBackStack(R.id.editFragment, true)
                } else {
                    toast(event.messageId)
                }
            }
        }
    }

}

/**
 * Check connection result
 */
@BindingAdapter("checkResult")
fun MaterialButton.setCheckResult(result: Boolean?) {
    if (tag == null) {
        // Backup
        tag = iconTint
    }

    when(result) {
        true -> {
            setIconResource(R.drawable.ic_check_ok)
            setIconTintResource(R.color.ic_check_ok)
        }
        false -> {
            setIconResource(R.drawable.ic_check_ng)
            setIconTintResource(R.color.ic_check_ng)
        }
        else -> {
            setIconResource(R.drawable.ic_check)
            iconTint = tag as? ColorStateList
        }
    }
}
