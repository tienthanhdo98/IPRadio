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

    external fun getWJsNVsim(packageName: String): String

    external fun getHubPrivateKey(packageName: String): String


    external fun gethubUrl(packageName: String): String

    external fun gettokenUrl(packageName: String): String

    //token binh dinh
    external fun getqmHINDtk(packageName: String): String

    external fun getgetToken(packageName: String): String
}