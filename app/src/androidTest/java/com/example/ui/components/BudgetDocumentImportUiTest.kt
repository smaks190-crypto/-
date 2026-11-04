package com.example.ui.components

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BudgetDocumentImportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun documentWaitsForPinAndSurvivesRecreation() {
        var requestCode = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                requestCode = code
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val locked = mutableStateOf(false)
        val imported = mutableListOf<Uri>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val choose = rememberBudgetDocumentImport(locked.value) { imported.add(it) }
                if (!locked.value) Button(onClick = choose) { Text("Choose") }
                else Button(onClick = { locked.value = false }) { Text("Unlock") }
            }
        }
        compose.onNodeWithText("Choose").performClick()
        compose.runOnIdle { locked.value = true }
        compose.waitForIdle()
        val uri = Uri.parse("content://test/budget.json")
        compose.runOnIdle { registry.dispatchResult(requestCode, uri) }
        compose.waitForIdle()
        assertEquals(0, imported.size)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(5000) { imported.size == 1 }
        assertEquals(listOf(uri), imported)
        compose.runOnIdle { locked.value = true }
        compose.runOnIdle { locked.value = false }
        compose.waitForIdle()
        assertEquals(1, imported.size)
    }
}
