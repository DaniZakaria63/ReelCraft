package id.my.daniza.reelcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.my.daniza.reelcraft.navigation.ReelCraftNavGraph
import id.my.daniza.reelcraft.theme.ReelCraftTheme

@AndroidEntryPoint
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
