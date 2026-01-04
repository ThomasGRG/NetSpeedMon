package jp.ikigai.netspeedmon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.ikigai.netspeedmon.ui.screens.MainScreen
import jp.ikigai.netspeedmon.ui.theme.NetSpeedMonTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetSpeedMonTheme {
                MainScreen()
            }
        }
    }
}