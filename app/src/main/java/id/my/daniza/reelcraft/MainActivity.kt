package id.my.daniza.reelcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import id.my.daniza.reelcraft.ui.navigation.ReelCraftNavGraph
import id.my.daniza.reelcraft.ui.theme.ReelCraftTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReelCraftTheme {
                val navController = rememberNavController()
                ReelCraftNavGraph(navController = navController)
            }
        }
    }
}
