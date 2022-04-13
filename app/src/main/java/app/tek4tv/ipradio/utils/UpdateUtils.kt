package app.tek4tv.ipradio.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.tek4tv.ipradio.core.SerialPortController
import java.io.File


/*fun downloadUpdateApk(url: String, context: Context) {
    var destination: String = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).toString() + "/"
    val fileName = "ggg.apk"
    destination += fileName
    val fileUri = Uri.parse("file://$destination")

    val file = File(destination)
    if (file.exists()) file.delete()

    val appname = context.getString(R.string.app_name)

    val request = DownloadManager.Request(Uri.parse(url))
    request.setDescription("Updating $appname")
    request.setTitle("Updating $appname...")

    request.setDestinationUri(fileUri)

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = downloadManager.enqueue(request)

    val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent?) {
            Log.d("installx", "begin install")
            if (!rootInstall(destination)) normalInstall(
                context, fileUri, downloadManager,
                downloadId
            )
        }
    }
    context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
}*/

fun downloadUpdateApk(
    url: String,
    context: Context,
    controller: SerialPortController,
) {
    var destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .toString() + "/"
    val fileName = "ipradio.apk"
    destination += fileName
    val uri = Uri.parse("file://$destination")

    //Delete update file if exists
    val file = File(destination)
    if (file.exists()) //file.delete() - test this, I think sometimes it doesnt work
        file.delete()

    val successMsg = StringBuilder();

    val errorMsg = StringBuilder();
    //set downloadmanager
    val request = DownloadManager.Request(Uri.parse(url))
    request.setDescription("update version")
    request.setTitle("ipradio.apk")
    request.setMimeType("application/vnd.android.package-archive")

    //set destination
    request.setDestinationUri(uri)

    // get download service and enqueue file
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
    manager!!.enqueue(request)

    //set BroadcastReceiver to install app when .apk is downloaded
    val finalDestination = destination
    val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            // Install Updated APK
            try {
                //pm install -r /storage/emulated/0/Download/ipradio.apk
                val command = "pm install -r $finalDestination"
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val test = proc.waitFor() // Error is here.
                Log.d("install", test.toString() + "")
                Thread.sleep(30000)
                doRestart(controller)

            } catch (e: Exception) {
                Log.d("install", "failed root")
                e.printStackTrace()
            }




        }


    }

    //register receiver for when .apk download is compete
    context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
}

private fun doRestart(controller: SerialPortController) {
    controller.invokeDeviceFunc(Define.FUNC_WRITE_FORCE_RESTART_DEVICE, "", true)
}

