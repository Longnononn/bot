package com.example.aobot

import android.app.Application
import org.tensorflow.lite.Interpreter

class GameBotApp : Application() {
    companion object {
        private lateinit var instance: GameBotApp
        fun getInstance(): GameBotApp = instance
    }

    private var interpreter: Interpreter? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun setInterpreter(interpreter: Interpreter) {
        this.interpreter = interpreter
    }

    fun getInterpreter(): Interpreter? = interpreter
}