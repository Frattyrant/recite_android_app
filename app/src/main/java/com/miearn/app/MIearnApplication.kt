package com.miearn.app

import android.app.Application

class MIearnApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
