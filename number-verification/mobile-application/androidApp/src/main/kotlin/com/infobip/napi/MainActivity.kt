package com.infobip.napi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.infobip.napi.di.AndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidContext.attachActivity(this)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidContext.detachActivity(this)
        super.onDestroy()
    }
}
