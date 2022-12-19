package com.example.kmpfirstlib

import com.example.androidlib.AndroidLib
import com.example.kmpsecondlib.KmpAndroidSecondLibClass

class KmpAndroidFirstLibClass {

    fun callCommonLibClass(): String {
        return KmpCommonFirstLibClass().get()
    }

    fun callKmpSecondLibClass(): String {
        return KmpAndroidSecondLibClass().callCommonLibClass()
    }

    fun callAndroidLibClass(): String {
        return AndroidLib().get()
    }
}
