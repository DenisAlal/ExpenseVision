package com.denalal.expensevision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.denalal.expensevision.core.designsystem.theme.ExpenseVisionTheme
import com.denalal.expensevision.feature.scan.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseVisionTheme {
                ScanScreen()
            }
        }
    }
}
