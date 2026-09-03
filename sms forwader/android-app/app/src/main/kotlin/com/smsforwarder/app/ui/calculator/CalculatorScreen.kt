package com.smsforwarder.app.ui.calculator

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun CalculatorScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current

    var displayValue by remember { mutableStateOf("0") }
    var previousValue by remember { mutableStateOf<Double?>(null) }
    var pendingOperation by remember { mutableStateOf<String?>(null) }
    var isNewEntry by remember { mutableStateOf(true) }

    fun checkSecretPasscode(input: String) {
        val cleanInput = input.trim()
        if (cleanInput == "767" || cleanInput == "0767") {
            Toast.makeText(context, "Calculator Disguise Unlocked!", Toast.LENGTH_SHORT).show()
            onUnlockSuccess()
        }
    }

    fun onNumberClick(numberStr: String) {
        if (isNewEntry || displayValue == "0") {
            displayValue = numberStr
            isNewEntry = false
        } else {
            if (displayValue.length < 12) {
                displayValue += numberStr
            }
        }
    }

    fun onDecimalClick() {
        if (isNewEntry) {
            displayValue = "0."
            isNewEntry = false
        } else if (!displayValue.contains(".")) {
            displayValue += "."
        }
    }

    fun onClearClick() {
        displayValue = "0"
        previousValue = null
        pendingOperation = null
        isNewEntry = true
    }

    fun onToggleSignClick() {
        val num = displayValue.toDoubleOrNull() ?: return
        displayValue = formatValue(-num)
    }

    fun onPercentClick() {
        val num = displayValue.toDoubleOrNull() ?: return
        displayValue = formatValue(num / 100.0)
    }

    fun evaluatePending(nextOp: String?) {
        val currentNum = displayValue.toDoubleOrNull() ?: return
        val prevNum = previousValue

        if (prevNum != null && pendingOperation != null) {
            val result = when (pendingOperation) {
                "+" -> prevNum + currentNum
                "-" -> prevNum - currentNum
                "×" -> prevNum * currentNum
                "÷" -> if (currentNum != 0.0) prevNum / currentNum else Double.NaN
                else -> currentNum
            }
            displayValue = formatValue(result)
            previousValue = if (nextOp != null) result else null
        } else {
            previousValue = currentNum
        }

        pendingOperation = nextOp
        isNewEntry = true
    }

    fun onEqualClick() {
        checkSecretPasscode(displayValue)
        evaluatePending(null)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF17171C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                if (pendingOperation != null && previousValue != null) {
                    Text(
                        text = "${formatValue(previousValue!!)} $pendingOperation",
                        fontSize = 22.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = displayValue,
                    fontSize = if (displayValue.length > 8) 42.sp else 60.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keypad Grid Colors
            val darkGray = Color(0xFF2E2F38)
            val lightGray = Color(0xFF4E505F)
            val orangeOp = Color(0xFF4B7BFF)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CalcBtn(text = "AC", color = lightGray, modifier = Modifier.weight(1f)) { onClearClick() }
                    CalcBtn(text = "±", color = lightGray, modifier = Modifier.weight(1f)) { onToggleSignClick() }
                    CalcBtn(text = "%", color = lightGray, modifier = Modifier.weight(1f)) { onPercentClick() }
                    CalcBtn(text = "÷", color = orangeOp, modifier = Modifier.weight(1f)) { evaluatePending("÷") }
                }

                // Row 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CalcBtn(text = "7", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("7") }
                    CalcBtn(text = "8", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("8") }
                    CalcBtn(text = "9", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("9") }
                    CalcBtn(text = "×", color = orangeOp, modifier = Modifier.weight(1f)) { evaluatePending("×") }
                }

                // Row 3
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CalcBtn(text = "4", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("4") }
                    CalcBtn(text = "5", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("5") }
                    CalcBtn(text = "6", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("6") }
                    CalcBtn(text = "-", color = orangeOp, modifier = Modifier.weight(1f)) { evaluatePending("-") }
                }

                // Row 4
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CalcBtn(text = "1", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("1") }
                    CalcBtn(text = "2", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("2") }
                    CalcBtn(text = "3", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("3") }
                    CalcBtn(text = "+", color = orangeOp, modifier = Modifier.weight(1f)) { evaluatePending("+") }
                }

                // Row 5
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CalcBtn(text = "C", color = lightGray, modifier = Modifier.weight(1f)) { onClearClick() }
                    CalcBtn(text = "0", color = darkGray, modifier = Modifier.weight(1f)) { onNumberClick("0") }
                    CalcBtn(text = ".", color = darkGray, modifier = Modifier.weight(1f)) { onDecimalClick() }

                    // Equal button with long-press support as backup secret unlock
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(orangeOp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onEqualClick() },
                                    onLongPress = {
                                        Toast.makeText(context, "Calculator Disguise Unlocked!", Toast.LENGTH_SHORT).show()
                                        onUnlockSuccess()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "=",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalcBtn(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

private fun formatValue(value: Double): String {
    if (value.isNaN()) return "Error"
    if (value.isInfinite()) return "Error"
    val df = DecimalFormat("#.########")
    return df.format(value)
}
