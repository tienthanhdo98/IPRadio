package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

object FtpUtils {
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun connectToFtpServer() : FTPClient
    {
        val ftpClient = FTPClient()
        ftpClient.connect("14.225.16.145")
        ftpClient.login("sondev", "123456")

        return ftpClient
    }

    private fun uploadFile(appContext: Context, filePath: String, fileType : Int) {
        scope.launch(Dispatchers.IO) {
            if (!File(filePath).exists()) return@launch

            try {
                val ftpClient = connectToFtpServer()
                ftpClient.setFileType(fileType)

                val remoteFolder = Utils.getDeviceId(appContext).replace(":", "-")
                val isRemoteFolderExisted = ftpClient.changeWorkingDirectory(remoteFolder)

                if (!isRemoteFolderExisted) {
                    ftpClient.makeDirectory("/$remoteFolder")
                    ftpClient.changeWorkingDirectory(remoteFolder)
                }

                val buffIn = BufferedInputStream(FileInputStream(filePath))
                ftpClient.enterLocalPassiveMode()
                val res = ftpClient.storeFile(File(filePath).name, buffIn)
                Log.d("upfile", res.toString())

                buffIn.close()
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadLogFile(appContext: Context, filePath: String)
    {
        uploadFile(appContext, filePath, FTP.ASCII_FILE_TYPE)
    }
}