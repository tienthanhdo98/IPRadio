package app.tek4tv.ipradio.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MoshiInst {
    val moshi =  Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()
}