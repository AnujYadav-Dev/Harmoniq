package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun ErrorComp(message: String, stackTrace: String) {
    Column(
        modifier = Modifier
            .background(Color.Red)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val normalTextStyle = TextStyle(color = Color.White)
        val boldTextStyle = normalTextStyle.copy(fontWeight = FontWeight.Bold)
        ProvideTextStyle(value = normalTextStyle) {
            Text(
                ":(",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = boldTextStyle.copy(fontSize = 40.sp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                AppStrings.SomethingWentHorriblyWrong,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = boldTextStyle,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                AppStrings.ErrorX(message),
                style = boldTextStyle,
            )
            Text(stackTrace)
        }
    }
}
