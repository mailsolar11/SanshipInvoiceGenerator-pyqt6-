package com.sanship.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) showDialog = true },
                enabled = enabled
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date")
            }
        },
        singleLine = true
    )
    
    if (showDialog) {
        DatePickerDialog(
            currentDate = value,
            onDateSelected = { selectedDate ->
                onValueChange(selectedDate)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

// View modes for the date picker
private enum class PickerMode {
    DAY,    // Normal calendar grid
    MONTH,  // 12-month grid for quick month selection
    YEAR    // Year grid for quick year selection
}

@Composable
fun DatePickerDialog(
    currentDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val date = try {
        if (currentDate.isNotBlank()) LocalDate.parse(currentDate, DateTimeFormatter.ISO_LOCAL_DATE) else LocalDate.now()
    } catch (e: Exception) { LocalDate.now() }
    
    var viewDate by remember { mutableStateOf(date.withDayOfMonth(1)) }
    var pickerMode by remember { mutableStateOf(PickerMode.DAY) }
    // For year picker: which "decade" page are we on
    var yearPageStart by remember { mutableStateOf((viewDate.year / 12) * 12) }
    
    val primaryColor = MaterialTheme.colors.primary
    val todayColor = Color(0xFFE3F2FD)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // Header with navigation
            when (pickerMode) {
                PickerMode.DAY -> DayModeHeader(
                    viewDate = viewDate,
                    onPrevMonth = { viewDate = viewDate.minusMonths(1) },
                    onNextMonth = { viewDate = viewDate.plusMonths(1) },
                    onMonthYearClick = { pickerMode = PickerMode.MONTH }
                )
                PickerMode.MONTH -> MonthModeHeader(
                    year = viewDate.year,
                    onPrevYear = { viewDate = viewDate.minusYears(1) },
                    onNextYear = { viewDate = viewDate.plusYears(1) },
                    onYearClick = { 
                        yearPageStart = (viewDate.year / 12) * 12
                        pickerMode = PickerMode.YEAR 
                    }
                )
                PickerMode.YEAR -> YearModeHeader(
                    yearStart = yearPageStart,
                    onPrevPage = { yearPageStart -= 12 },
                    onNextPage = { yearPageStart += 12 }
                )
            }
        },
        text = {
            when (pickerMode) {
                PickerMode.DAY -> DayGrid(
                    viewDate = viewDate,
                    selectedDate = date,
                    onDateSelected = onDateSelected
                )
                PickerMode.MONTH -> MonthGrid(
                    selectedMonth = viewDate.monthValue,
                    selectedYear = viewDate.year,
                    onMonthSelected = { month ->
                        viewDate = viewDate.withMonth(month)
                        pickerMode = PickerMode.DAY
                    }
                )
                PickerMode.YEAR -> YearGrid(
                    yearStart = yearPageStart,
                    selectedYear = viewDate.year,
                    onYearSelected = { year ->
                        viewDate = viewDate.withYear(year)
                        pickerMode = PickerMode.MONTH
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ======================================
// HEADER COMPOSABLES
// ======================================

@Composable
private fun DayModeHeader(
    viewDate: LocalDate,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthYearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(Icons.Default.KeyboardArrowLeft, "Previous Month")
        }
        
        // Clickable month+year header → jumps to Month picker
        Text(
            text = viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onMonthYearClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colors.primary
        )
        
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Default.KeyboardArrowRight, "Next Month")
        }
    }
}

@Composable
private fun MonthModeHeader(
    year: Int,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onYearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevYear) {
            Icon(Icons.Default.KeyboardArrowLeft, "Previous Year")
        }
        
        // Clickable year header → jumps to Year picker
        Text(
            text = "$year",
            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onYearClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colors.primary
        )
        
        IconButton(onClick = onNextYear) {
            Icon(Icons.Default.KeyboardArrowRight, "Next Year")
        }
    }
}

@Composable
private fun YearModeHeader(
    yearStart: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevPage) {
            Icon(Icons.Default.KeyboardArrowLeft, "Previous Years")
        }
        
        Text(
            text = "${yearStart} – ${yearStart + 11}",
            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        IconButton(onClick = onNextPage) {
            Icon(Icons.Default.KeyboardArrowRight, "Next Years")
        }
    }
}

// ======================================
// GRID COMPOSABLES
// ======================================

@Composable
private fun DayGrid(
    viewDate: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (String) -> Unit
) {
    Column {
        // Days Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { 
                Text(
                    it, 
                    modifier = Modifier.width(36.dp), 
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        
        // Calendar Grid
        val offset = if (viewDate.dayOfWeek.value == 7) 0 else viewDate.dayOfWeek.value
        val daysInMonth = viewDate.lengthOfMonth()
        
        Column {
            var dayCounter = 1
            for (row in 0..5) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (col in 0..6) {
                        if (dayCounter > daysInMonth) {
                            Spacer(Modifier.size(36.dp))
                        } else if (row == 0 && col < offset) {
                            Spacer(Modifier.size(36.dp))
                        } else {
                            val currentDay = dayCounter
                            val fullDate = viewDate.withDayOfMonth(currentDay)
                            val isSelected = fullDate == selectedDate
                            val isToday = fullDate == LocalDate.now()
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colors.primary
                                            isToday -> Color(0xFFE3F2FD)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateSelected(fullDate.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$currentDay",
                                    color = if (isSelected) Color.White else Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                            dayCounter++
                        }
                    }
                }
                if (dayCounter > daysInMonth) break
            }
        }
    }
}

@Composable
private fun MonthGrid(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Select Month",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // 4 rows × 3 cols = 12 months
        for (row in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val monthIndex = row * 3 + col + 1  // 1-based month
                    val monthName = Month.of(monthIndex).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    val isSelected = monthIndex == selectedMonth
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colors.primary 
                                else Color(0xFFF5F5F5)
                            )
                            .clickable { onMonthSelected(monthIndex) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = monthName,
                            color = if (isSelected) Color.White else Color.Black,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(
    yearStart: Int,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Select Year",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // 4 rows × 3 cols = 12 years
        for (row in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val year = yearStart + row * 3 + col
                    val isSelected = year == selectedYear
                    val isCurrentYear = year == LocalDate.now().year
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colors.primary
                                    isCurrentYear -> Color(0xFFE3F2FD)
                                    else -> Color(0xFFF5F5F5)
                                }
                            )
                            .clickable { onYearSelected(year) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$year",
                            color = if (isSelected) Color.White else Color.Black,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
