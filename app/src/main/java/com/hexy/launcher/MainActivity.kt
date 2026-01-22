package com.hexy.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.databinding.ActivityMainBinding
import com.hexy.launcher.ui.LauncherViewModel
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.ui.SettingsActivity
import com.hexy.launcher.util.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setActivityContext(this)
        setupGrid()
        setupFab()
        setupSearchBar()
        
        // Load apps immediately - no permission needed with click-based tracking
        viewModel.loadApps()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // If home button pressed while already at home, animate to center
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            binding.hexGrid.animateToOrigin()
        }
    }
    
    private fun setupFab() {
        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun setupSearchBar() {
        val position = SettingsManager.getSearchPosition(this)
        val withMic = SettingsManager.getSearchWithMic(this)
        
        // Always hide both first, then show the correct one
        binding.searchContainerTop.visibility = View.GONE
        binding.searchContainerBottom.visibility = View.GONE
        
        if (position == SettingsManager.SearchPosition.NONE) {
            return
        }
        
        val container = if (position == SettingsManager.SearchPosition.TOP) 
            binding.searchContainerTop else binding.searchContainerBottom
        
        val editText = if (position == SettingsManager.SearchPosition.TOP)
            binding.searchEtTop else binding.searchEtBottom
            
        val micButton = if (position == SettingsManager.SearchPosition.TOP)
            binding.searchMicTop else binding.searchMicBottom
            
        container.visibility = View.VISIBLE
        micButton.visibility = if (withMic) View.VISIBLE else View.GONE
        
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        micButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            try {
                startActivityForResult(intent, REQUEST_CODE_VOICE_SEARCH)
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            viewModel.launchApp(app)
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ ->
            showContextMenu(app)
        }

        viewModel.apps.observe(this) { apps ->
            binding.hexGrid.setApps(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
        android.app.AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Hide App", "App Info", "Uninstall")) { _, which ->
                when (which) {
                    0 -> viewModel.hideApp(app)
                    1 -> {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${app.packageName}")
                        }
                        startActivity(intent)
                    }
                    2 -> {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding.hexGrid.refreshSettings()
        // Removed automatic scrollToOrigin on resume to allow returning to previous state
        // Only home button press triggers animation via onNewIntent
        setupSearchBar() // Refresh search bar visibility settings
        viewModel.loadApps()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VOICE_SEARCH && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (spokenText != null) {
                val editText = if (binding.searchContainerTop.visibility == View.VISIBLE) 
                    binding.searchEtTop else binding.searchEtBottom
                editText.setText(spokenText)
            }
        }
    }
    
    companion object {
        private const val REQUEST_CODE_VOICE_SEARCH = 101
    }
}
