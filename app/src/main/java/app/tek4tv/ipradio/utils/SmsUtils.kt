package app.tek4tv.ipradio.utils

import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

object SmsUtils {
     fun sendSms()
     {
         val smsManager = SmsManager.getDefault()
         smsManager.sendTextMessage("191",null,"KTTK",null,null)
     }

    fun querySms(context: Context)
    {
        val SMS_URI_INBOX = "content://sms/inbox"
        val uri: Uri = Uri.parse(SMS_URI_INBOX)
        val projection = arrayOf("_id", "address", "person", "body", "date", "type")
        val cur =
            context.contentResolver.query(uri, projection, null, null, "date asc")
                ?: return

        val c = cur.count

        while (cur.moveToNext())
        {
            val index_Address = cur.getColumnIndex("address")
            val index_Person = cur.getColumnIndex("person")
            val index_Body = cur.getColumnIndex("body")
            val index_Date = cur.getColumnIndex("date")
            val index_Type = cur.getColumnIndex("type")

            val strAddress = cur.getString(index_Address)
            val intPerson = cur.getInt(index_Person)
            val strbody = cur.getString(index_Body)
            val longDate = cur.getLong(index_Date)
            val int_Type = cur.getInt(index_Type)

            val str = "$strAddress: $strbody"

            Log.d("Mess",str)
        }
    }
}