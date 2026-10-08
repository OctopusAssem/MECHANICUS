package com.assem.mechanicus

data class User(
    val id: Long,
    val name: String,
    val pin: String,
    val role: String,
    val active: Boolean,
)

data class Payment(
    val id: Long,
    val carId: String,
    val amount: Double,
    val stage: String,
    val userName: String,
    val ts: Long,
)

data class Car(
    val id: String,
    val plate: String,
    val engine: String,
    val odometer: String,
    val make: String,
    val model: String,
    val deliveryDate: String,
    val customer: String,
    val phone: String,
    val worker: String,
    val intake: String,
    val status: String,
    val monthKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val photo: String? = null,
    val parts: List<String> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

data class IndexRow(
    val id: String,
    val plate: String,
    val customer: String,
    val phone: String,
    val status: String,
    val month: String,
    val updated: Long,
    val pay: Double,
    val photo: String?,
    val adate: String = "",
)

data class PayRow(
    val plate: String,
    val amount: Double,
    val stage: String,
    val userName: String,
    val ts: Long,
)

data class LogEntry(
    val id: Long,
    val ts: Long,
    val userName: String,
    val action: String,
    val plate: String,
    val detail: String,
)

data class Stats(
    val carsToday: Int,
    val inWork: Int,
    val todayIncome: Double,
    val monthIncome: Double,
    val pending: Double,
)

object Status {
    const val WORK = "work"
    const val DONE = "done"
    const val LATE = "late"
}
