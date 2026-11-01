package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import io.github.eslamasabry.oc.android.surfaces.R

/** Frozen FQCNs of the Flutter 2.1.0+57 widgets and tiles (ADR §9.9), declared from a library module. P5 stubs. */
public abstract class P5StubWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, RemoteViews(context.packageName, R.layout.p5_widget_stub))
    }
}

public abstract class P5StubTile : TileService()

public class SessionsWidgetProvider : P5StubWidget()
public class SessionsWideWidgetProvider : P5StubWidget()
public class SessionsListWidgetProvider : P5StubWidget()
public class SessionsStripWidgetProvider : P5StubWidget()
public class UsageWidgetProvider : P5StubWidget()
public class UsageWideWidgetProvider : P5StubWidget()
public class StatusWidgetProvider : P5StubWidget()
public class AttentionTileService : P5StubTile()
public class PauseTileService : P5StubTile()
