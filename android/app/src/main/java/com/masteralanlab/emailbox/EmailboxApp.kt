package com.masteralanlab.emailbox

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.masteralanlab.emailbox.data.NotesLocalStore
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.NavigationSettings
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.data.LedgerLocalStore
import com.masteralanlab.emailbox.notify.MailNotifyService
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import com.masteralanlab.emailbox.update.UpdateNotifications

class EmailboxApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NavigationSettings.load()
        SecureMailCache.init(this)
        LedgerLocalStore.init(this)
        NotesLocalStore.init(this)
        ThemeSettings.load()
        UpdateNotifications.createChannel(this)
        MailNotifyService.createChannels(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .build()
}
