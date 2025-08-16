package com.example.aobot

import android.app.Application
import org.tensorflow.lite.Interpreter

class GameBotApp : Application() {

    private var detectionInterpreter: Interpreter? = null
    private var decisionInterpreter: Interpreter? = null

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Set the TFLite Interpreter for object detection.
     */
    fun setDetectionInterpreter(interpreter: Interpreter) {
        this.detectionInterpreter = interpreter
    }

    /**
     * Get the TFLite Interpreter for object detection.
     */
    fun getDetectionInterpreter(): Interpreter? = detectionInterpreter

    /**
     * Set the TFLite Interpreter for decision making.
     */
    fun setDecisionInterpreter(interpreter: Interpreter) {
        this.decisionInterpreter = interpreter
    }

    /**
     * Get the TFLite Interpreter for decision making.
     */
    fun getDecisionInterpreter(): Interpreter? = decisionInterpreter
}
