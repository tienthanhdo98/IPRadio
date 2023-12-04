package app.tek4tv.ipradio.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val bundle = intent.extras
            if (bundle != null) {
                Log.d("SmsReceiver", "New SMS received from: Message: $bundle")
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                    val messageBody = smsMessage.messageBody
                    val sender = smsMessage.originatingAddress

                    Log.d("SmsReceiver", "New SMS received from: $sender, Message: $messageBody")

                    // Do something with the received SMS
                }
            }
        }
    }
}