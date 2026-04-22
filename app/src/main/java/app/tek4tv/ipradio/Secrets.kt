package app.tek4tv.ipradio

class Secrets {

    //Method calls will be added by gradle task hideSecret
    //Example : external fun getWellHiddenSecret(packageName: String): String

    companion object {
        init {
            System.loadLibrary("secrets")
        }
    }

    // token device
    external fun getAQNIjugs(packageName: String): String


    external fun getHubPrivateKey(packageName: String): String


    external fun gethubUrlBinhDinhAl(packageName: String): String

    external fun getgetTokenApi(packageName: String): String

    external fun gethubbn(packageName: String): String

    external fun gethubhue(packageName: String): String
}