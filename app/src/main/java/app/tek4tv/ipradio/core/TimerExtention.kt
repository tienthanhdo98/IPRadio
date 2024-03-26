package app.tek4tv.ipradio.core


import java.util.*

class TimerTaskExtention (var listMediaRepo: MutableList<app.tek4tv.ipradio.model.MediaItem>) : TimerTask() {

//     TimerTaskExtention(){}
//    TimerTaskExtention(string ){}

    var busing : Boolean = false
    override fun run() {

        if(busing){
            return;
        }

        busing = true;

        //lay du lieu local db





        busing = false;





    }



}