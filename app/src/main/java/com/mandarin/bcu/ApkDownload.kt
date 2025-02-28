package com.mandarin.bcu

import android.Manifest
import android.content.Context
import android.content.SharedPreferences.Editor
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mandarin.bcu.androidutil.LocaleManager
import com.mandarin.bcu.androidutil.StaticStore
import com.mandarin.bcu.androidutil.supports.SingleClick
import com.mandarin.bcu.androidutil.io.AContext
import com.mandarin.bcu.androidutil.io.DefineItf
import com.mandarin.bcu.androidutil.io.coroutine.DownloadApk
import com.mandarin.bcu.androidutil.supports.LeakCanaryManager
import common.CommonStatic
import java.util.*

class ApkDownload : AppCompatActivity() {
    private var path = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = getSharedPreferences(StaticStore.CONFIG, Context.MODE_PRIVATE)
        val ed: Editor
        if (!shared.contains("initial")) {
            ed = shared.edit()
            ed.putBoolean("initial", true)
            ed.putBoolean("theme", true)
            ed.apply()
        } else {
            if (!shared.getBoolean("theme", false)) {
                setTheme(R.style.AppTheme_night)
            } else {
                setTheme(R.style.AppTheme_day)
            }
        }


        LeakCanaryManager.initCanary(shared)

        DefineItf.check(this)

        AContext.check()

        (CommonStatic.ctx as AContext).updateActivity(this)

        setContentView(R.layout.activity_apk_download)

        path = StaticStore.getExternalPath(this)+"apk/"

        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 786)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this@ApkDownload, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            val result = intent

            if (result.getStringExtra("ver") != null) {
                val ver = (result.getStringExtra("ver") ?: StaticStore.VER).replace(Regex("b_.+?\$"), "")
                val filestart = "BCU_Android_"
                val apk = ".apk"
                val realpath = path + filestart + ver + apk
                val url = "https://github.com/battlecatsultimate/bcu-assets/blob/master/apk/BCU_Android_"
                val end = "?raw=true"
                val realurl = url + ver + apk + end
                val retry = findViewById<Button>(R.id.apkretry)
                retry.visibility = View.GONE
                val prog = findViewById<ProgressBar>(R.id.apkprog)
                prog.isIndeterminate = true
                prog.max = 100
                val state = findViewById<TextView>(R.id.apkstate)
                state.setText(R.string.down_state_rea)
                DownloadApk(this@ApkDownload, ver, realurl, path, realpath).execute()
                retry.setOnClickListener(object : SingleClick() {
                    override fun onSingleClick(v: View?) {
                        DownloadApk(this@ApkDownload, ver, realurl, path, realpath).execute()
                        retry.visibility = View.GONE
                    }
                })
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val shared = newBase.getSharedPreferences(StaticStore.CONFIG, Context.MODE_PRIVATE)
        val lang = shared?.getInt("Language",0) ?: 0

        val config = Configuration()
        var language = StaticStore.lang[lang]
        var country = ""

        if(language == "") {
            language = Resources.getSystem().configuration.locales.get(0).language
            country = Resources.getSystem().configuration.locales.get(0).country
        }

        val loc = if(country.isNotEmpty()) {
            Locale(language, country)
        } else {
            Locale(language)
        }

        config.setLocale(loc)
        applyOverrideConfiguration(config)
        super.attachBaseContext(LocaleManager.langChange(newBase,shared?.getInt("Language",0) ?: 0))
    }

    public override fun onDestroy() {
        super.onDestroy()
        StaticStore.toast = null
    }

    override fun onResume() {
        AContext.check()

        if(CommonStatic.ctx is AContext)
            (CommonStatic.ctx as AContext).updateActivity(this)

        super.onResume()
    }
}