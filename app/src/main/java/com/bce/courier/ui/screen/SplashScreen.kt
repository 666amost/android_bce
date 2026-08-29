package com.bce.courier.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bce.courier.data.model.AppConfigResponse

@Composable
fun SplashScreen(
    config: AppConfigResponse?,
    onConfigLoaded: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = com.bce.courier.R.drawable.splash_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "BCE EXPRESS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Courier Native",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (config == null) {
                CircularProgressIndicator()
            } else if (!config.enabled) {
                Text(
                    text = "System under maintenance",
                    color = Color.Red,
                    fontWeight = FontWeight.Medium
                )
            } else {
                LaunchedEffect(Unit) {
                    onConfigLoaded()
                }
            }
        }
        
        if (config?.banner?.enabled == true) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(config.banner.title) },
                text = { Text(config.banner.message) },
                confirmButton = {
                    Button(onClick = { onConfigLoaded() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
