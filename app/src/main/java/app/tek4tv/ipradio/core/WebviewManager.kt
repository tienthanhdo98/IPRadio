package app.tek4tv.ipradio.core

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import app.tek4tv.ipradio.MyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class WebViewJsInterface(private val webView : WebView)
{
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val appContext = MyApp.instance!!.applicationContext


    @JavascriptInterface
    fun sendCommand( command : String ,message: String )
    {

        //handleFromWebview(command,message)
        coroutineScope.launch {
            if(command == "STATUS_TIME_DATA")
            {
//                print("Time: $message");
                Log.d("WebviewHandle", "sendCommand: Time $message ")

            }
            }
        }
    }
