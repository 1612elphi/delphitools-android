package com.delphi.delphitools.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.shell.PaneBar
import com.delphi.delphitools.shell.SearchRow
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.shell.shownCategories
import com.delphi.delphitools.ui.theme.DelphitoolsTheme
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** dt-ios SingleToolIntent: picks the tool a Quick Launch stamp opens. */
class WidgetToolPicker : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val placed = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // back keeps the default
        setResult(RESULT_OK, placed)
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                Column(Modifier.fillMaxSize().background(LocalDelphiColours.current.background).statusBarsPadding().navigationBarsPadding()) {
                    PaneBar("Quick Launch", done = ::finish, doneLabel = "Cancel")
                    LazyColumn {
                        shownCategories(ShellStores.of(this@WidgetToolPicker).categoryOrder).forEach { (category, members) ->
                            item(key = category) { CanonHeading(category.title) }
                            items(members, key = { it.id }) { tool ->
                                SearchRow(tool) {
                                    QuickLaunchWidget.set(this@WidgetToolPicker, id, tool)
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
