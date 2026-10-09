package com.assem.mechanicus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// Quick contact actions for a customer's phone number: a direct WhatsApp chat
// and the phone dialer. Both only make sense for a real number, so callers gate
// on `valid(...)` first.
object Contact {
    fun normalize(phone: String): String = phone.filter { it.isDigit() }

    fun valid(phone: String): Boolean = normalize(phone).length >= 7

    fun whatsapp(ctx: Context, phone: String, failed: String) {
        val digits = waNumber(phone)
        if (digits.length < 7) return
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")), failed)
    }

    // WhatsApp needs an international number. Local Egyptian numbers start with
    // 0 (e.g. 01012345678); convert to +20 for a working wa.me link.
    private fun waNumber(phone: String): String {
        var d = normalize(phone)
        if (d.startsWith("00")) d = d.substring(2)
        if (d.startsWith("0")) d = "20" + d.substring(1)
        return d
    }

    fun dial(ctx: Context, phone: String, failed: String) {
        val digits = normalize(phone)
        if (digits.length < 7) return
        open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")), failed)
    }

    private fun open(ctx: Context, intent: Intent, failed: String) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, failed, Toast.LENGTH_SHORT).show()
        }
    }
}
